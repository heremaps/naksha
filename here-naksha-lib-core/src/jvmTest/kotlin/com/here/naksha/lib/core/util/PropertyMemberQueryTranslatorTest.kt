@file:Suppress("DEPRECATION", "OPT_IN_USAGE")
package com.here.naksha.lib.core.util

import com.here.naksha.lib.core.models.naksha.CustomIndexMapping
import com.here.naksha.lib.core.models.naksha.CustomIndexMappingList
import com.here.naksha.lib.core.models.naksha.customIndexMapping
import naksha.model.objects.*
import naksha.model.request.*
import naksha.model.request.ops.*
import naksha.model.request.query.*
import kotlin.test.*

class PropertyMemberQueryTranslatorTest {
    private fun schema(type: MemberType = MemberType.INT32) = CustomIndexMappingCompiler.expand(NakshaCollection("mapped", "catalog").apply {
        customIndexMapping = CustomIndexMappingList(CustomIndexMapping(JsonPath("properties", "value"), type))
    })
    private fun query(op: AnyOp, value: Any? = null) = PQuery(Property("properties", "value"), op, value)

    @Test fun translatesBooleanOperationsWithoutQueryValues() {
        val schema = schema(MemberType.BOOLEAN)
        val isTrue = query(AnyOp.IS_TRUE)
        val isFalse = query(AnyOp.IS_FALSE)
        assertNull(isTrue.value)
        assertNull(isFalse.value)
        assertEquals("cm01", assertIs<IsTrue>(PropertyMemberQueryTranslator.translate(isTrue, schema)).at)
        assertEquals("cm01", assertIs<IsFalse>(PropertyMemberQueryTranslator.translate(isFalse, schema)).at)
        val request = ReadFeatures().withPropertyQuery(isFalse)
        val adapted = assertNotNull(PropertyMemberQueryTranslator.adapt(request, schema))
        assertIs<IsFalse>(adapted.queryMembers)
        assertIs<PropertyFilter>(adapted.resultFilters.single())
        assertNull(request.queryMembers)
    }

    @Test fun unsupportedBooleanOperationsKeepCompleteExpressionOnLegacyPath() {
        val schema = schema(MemberType.BOOLEAN)
        val contains = query(AnyOp.CONTAINS, true)
        assertNull(PropertyMemberQueryTranslator.translate(contains, schema))
        assertNull(PropertyMemberQueryTranslator.translate(query(AnyOp.IS_NULL), schema))
        assertNull(PropertyMemberQueryTranslator.translate(PAnd(query(AnyOp.IS_TRUE), contains), schema))
        assertNull(PropertyMemberQueryTranslator.translate(POr(query(AnyOp.IS_FALSE), contains), schema))
    }

    @Test fun translatesFractionalIntegerBoundsWithoutTruncatingTheirMeaning() {
        val schema = schema()
        assertEquals(50, assertIs<Gt>(PropertyMemberQueryTranslator.translate(query(DoubleOp.GT, 50.5), schema)).value)
        assertEquals(51, assertIs<Gte>(PropertyMemberQueryTranslator.translate(query(DoubleOp.GTE, 50.5), schema)).value)
        assertEquals(51, assertIs<Lt>(PropertyMemberQueryTranslator.translate(query(DoubleOp.LT, 50.5), schema)).value)
        assertEquals(50, assertIs<Lte>(PropertyMemberQueryTranslator.translate(query(DoubleOp.LTE, 50.5), schema)).value)
        assertIs<And>(PropertyMemberQueryTranslator.translate(query(DoubleOp.EQ, 50.5), schema))
        assertIs<Not>(PropertyMemberQueryTranslator.translate(query(DoubleOp.GT, -1e100), schema))
    }

    @Test fun unsupportedLeafPreventsPartialAndOrTranslation() {
        val a = query(DoubleOp.EQ, 50)
        val b = PQuery(Property("unmapped"), StringOp.EQUALS, "x")
        assertNull(PropertyMemberQueryTranslator.translate(PAnd(a, b), schema()))
        assertNull(PropertyMemberQueryTranslator.translate(POr(a, b), schema()))
        assertNull(PropertyMemberQueryTranslator.translate(PNot(a), schema()))
        assertNull(PropertyMemberQueryTranslator.translate(query(AnyOp.IS_NULL), schema()))
        assertIs<Or>(PropertyMemberQueryTranslator.translate(POr(a, query(DoubleOp.GT, 60)), schema()))
    }

    @Test fun preservesOriginalRequestAndCapturesFilterExpression() {
        val original = ReadFeatures().withCatalogId("catalog").withCollectionId("mapped")
            .withPropertyQuery(query(DoubleOp.EQ, 50))
        original.limit = 1
        original.featureIds.add("one")
        val untouched = original.copy<ReadFeatures>(true)
        val adapted = assertNotNull(PropertyMemberQueryTranslator.adapt(original, schema()))
        assertNull(original.queryMembers)
        assertNotNull(original.query.properties)
        assertEquals(untouched.featureIds.toList(), original.featureIds.toList())
        assertEquals(1, adapted.limit)
        assertIs<And>(adapted.queryMembers)
        assertTrue(adapted.featureIds.isEmpty())
        assertNull(adapted.query.properties)
        val filter = assertIs<PropertyFilter>(adapted.resultFilters.single())
        original.query.properties = query(DoubleOp.EQ, 99)
        val match = FeatureTuple(naksha.base.TupleNumber(1L, 0, 0, 1L, 0L), null).apply { feature = NakshaFeature("one").apply { properties["value"] = 50 } }
        val miss = FeatureTuple(naksha.base.TupleNumber(1L, 0, 0, 2L, 0L), null).apply { feature = NakshaFeature("two").apply { properties["value"] = 99 } }
        assertNotNull(filter.filter(match))
        assertNull(filter.filter(miss))
    }

    @Test fun keepsHistoryGuidsAndUnsupportedPrecisionOnLegacyPath() {
        val request = ReadFeatures().withPropertyQuery(query(DoubleOp.EQ, 50))
        request.queryHistory = true
        assertNull(PropertyMemberQueryTranslator.adapt(request, schema()))
        request.queryHistory = false
        val guid = naksha.base.Guid("one", naksha.base.TupleNumber.HEAD)
        request.guids.add(guid)
        assertNull(PropertyMemberQueryTranslator.adapt(request, schema()))
        assertEquals(guid, request.guids.single())
        request.guids.clear()
        val native = Equals(StandardMembers.Id, "one")
        request.queryMembers = native
        assertNull(PropertyMemberQueryTranslator.adapt(request, schema()))
        assertSame(native, request.queryMembers)
        assertNull(PropertyMemberQueryTranslator.translate(query(DoubleOp.EQ, 9_007_199_254_740_992.0), schema(MemberType.INT64)))
        assertNull(PropertyMemberQueryTranslator.translate(query(DoubleOp.EQ, 0.1), schema(MemberType.FLOAT32)))
        assertIs<Equals>(PropertyMemberQueryTranslator.translate(query(DoubleOp.EQ, 0.5), schema(MemberType.FLOAT32)))
        assertIs<And>(PropertyMemberQueryTranslator.translate(query(DoubleOp.GT, 0.5), schema(MemberType.FLOAT64)))
    }

    @Test fun supportsStringsAndExactTagListContainment() {
        assertIs<Equals>(PropertyMemberQueryTranslator.translate(query(StringOp.EQUALS, "abc"), schema(MemberType.STRING)))
        assertIs<StartsWith>(PropertyMemberQueryTranslator.translate(query(StringOp.STARTS_WITH, "a_%"), schema(MemberType.STRING)))
        assertIs<TagListContains>(PropertyMemberQueryTranslator.translate(query(AnyOp.CONTAINS, "red"), schema(MemberType.TAG_LIST)))
        assertNull(PropertyMemberQueryTranslator.translate(query(AnyOp.CONTAINS, "[\"red\"]"), schema(MemberType.TAG_LIST)))
        assertNull(PropertyMemberQueryTranslator.translate(query(AnyOp.CONTAINS, "{\"a\":1}"), schema(MemberType.TAG_MAP)))
    }
    @Test fun unknownCallbacksAndUnattachedPropertyFiltersKeepLegacyBehavior() {
        val request = ReadFeatures().withPropertyQuery(query(DoubleOp.EQ, 50))
        request.resultFilters.add(object : ResultFilter {
            override fun filter(featureTuple: FeatureTuple): FeatureTuple = featureTuple
        })
        assertNull(PropertyMemberQueryTranslator.adapt(request, schema()))
        request.resultFilters.clear()
        assertNull(PropertyMemberQueryTranslator.adapt(request, schema()))
        assertNotNull(request.query.properties)
    }

}
