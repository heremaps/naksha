package com.here.naksha.lib.core.util;

import static com.here.naksha.lib.core.models.naksha.CustomIndexMappings.setCustomIndexMapping;
import static org.junit.jupiter.api.Assertions.*;

import com.here.naksha.lib.core.models.naksha.CustomIndexMapping;
import com.here.naksha.lib.core.models.naksha.CustomIndexMappingList;
import java.util.ArrayList;
import naksha.base.Guid;
import naksha.base.TupleNumber;
import naksha.model.objects.*;
import naksha.model.request.*;
import naksha.model.request.ops.*;
import naksha.model.request.query.*;
import org.junit.jupiter.api.Test;

class PropertyMemberQueryTranslatorTest {
  private NakshaCollection schema() {
    return schema(MemberType.INT32);
  }

  private NakshaCollection schema(MemberType type) {
    NakshaCollection schema = new NakshaCollection("mapped", "catalog");
    setCustomIndexMapping(schema, new CustomIndexMappingList(new CustomIndexMapping(new JsonPath("properties", "value"), type)));
    return CustomIndexMappingCompiler.expand(schema);
  }

  private PQuery query(AnyOp op) {
    return query(op, null);
  }

  private PQuery query(AnyOp op, Object value) {
    return new PQuery(new Property("properties", "value"), op, value);
  }

  @Test void translatesBooleanOperationsWithoutQueryValues() {
    NakshaCollection schema = schema(MemberType.BOOLEAN);
    PQuery isTrue = query(AnyOp.IS_TRUE);
    PQuery isFalse = query(AnyOp.IS_FALSE);
    assertNull(isTrue.getValue());
    assertNull(isFalse.getValue());
    assertEquals("cm01", assertInstanceOf(IsTrue.class, PropertyMemberQueryTranslator.translate(isTrue, schema)).getAt());
    assertEquals("cm01", assertInstanceOf(IsFalse.class, PropertyMemberQueryTranslator.translate(isFalse, schema)).getAt());
    ReadFeatures request = new ReadFeatures().withPropertyQuery(isFalse);
    ReadFeatures adapted = PropertyMemberQueryTranslator.adapt(request, schema);
    assertNotNull(adapted);
    assertInstanceOf(IsFalse.class, adapted.getQueryMembers());
    assertEquals(1, adapted.getResultFilters().size());
    assertInstanceOf(PropertyFilter.class, adapted.getResultFilters().get(0));
    assertNull(request.getQueryMembers());
  }

  @Test void unsupportedBooleanOperationsKeepCompleteExpressionOnLegacyPath() {
    NakshaCollection schema = schema(MemberType.BOOLEAN);
    PQuery contains = query(AnyOp.CONTAINS, true);
    assertNull(PropertyMemberQueryTranslator.translate(contains, schema));
    assertNull(PropertyMemberQueryTranslator.translate(query(AnyOp.IS_NULL), schema));
    assertNull(PropertyMemberQueryTranslator.translate(new PAnd(query(AnyOp.IS_TRUE), contains), schema));
    assertNull(PropertyMemberQueryTranslator.translate(new POr(query(AnyOp.IS_FALSE), contains), schema));
  }

  @Test void translatesFractionalIntegerBoundsWithoutTruncatingTheirMeaning() {
    NakshaCollection schema = schema();
    assertEquals(50, assertInstanceOf(Gt.class, PropertyMemberQueryTranslator.translate(query(DoubleOp.GT, 50.5), schema)).getValue());
    assertEquals(51, assertInstanceOf(Gte.class, PropertyMemberQueryTranslator.translate(query(DoubleOp.GTE, 50.5), schema)).getValue());
    assertEquals(51, assertInstanceOf(Lt.class, PropertyMemberQueryTranslator.translate(query(DoubleOp.LT, 50.5), schema)).getValue());
    assertEquals(50, assertInstanceOf(Lte.class, PropertyMemberQueryTranslator.translate(query(DoubleOp.LTE, 50.5), schema)).getValue());
    assertInstanceOf(And.class, PropertyMemberQueryTranslator.translate(query(DoubleOp.EQ, 50.5), schema));
    assertInstanceOf(Not.class, PropertyMemberQueryTranslator.translate(query(DoubleOp.GT, -1e100), schema));
  }

  @Test void unsupportedLeafPreventsPartialAndOrTranslation() {
    PQuery a = query(DoubleOp.EQ, 50);
    PQuery b = new PQuery(new Property("unmapped"), StringOp.EQUALS, "x");
    assertNull(PropertyMemberQueryTranslator.translate(new PAnd(a, b), schema()));
    assertNull(PropertyMemberQueryTranslator.translate(new POr(a, b), schema()));
    assertNull(PropertyMemberQueryTranslator.translate(new PNot(a), schema()));
    assertNull(PropertyMemberQueryTranslator.translate(query(AnyOp.IS_NULL), schema()));
    assertInstanceOf(Or.class, PropertyMemberQueryTranslator.translate(new POr(a, query(DoubleOp.GT, 60)), schema()));
  }

  @Test void preservesOriginalRequestAndCapturesFilterExpression() {
    ReadFeatures original = new ReadFeatures().withCatalogId("catalog").withCollectionId("mapped").withPropertyQuery(query(DoubleOp.EQ, 50));
    original.setLimit(1);
    original.getFeatureIds().add("one");
    ReadFeatures untouched = original.copy(true);
    ReadFeatures adapted = PropertyMemberQueryTranslator.adapt(original, schema());
    assertNotNull(adapted);
    assertNull(original.getQueryMembers());
    assertNotNull(original.getQuery().getProperties());
    assertEquals(new ArrayList<>(untouched.getFeatureIds()), new ArrayList<>(original.getFeatureIds()));
    assertEquals(1, adapted.getLimit());
    assertInstanceOf(And.class, adapted.getQueryMembers());
    assertTrue(adapted.getFeatureIds().isEmpty());
    assertNull(adapted.getQuery().getProperties());
    assertEquals(1, adapted.getResultFilters().size());
    PropertyFilter filter = assertInstanceOf(PropertyFilter.class, adapted.getResultFilters().get(0));
    original.getQuery().setProperties(query(DoubleOp.EQ, 99));
    FeatureTuple match = tuple("one", 1L, 50);
    FeatureTuple miss = tuple("two", 2L, 99);
    assertNotNull(filter.filter(match));
    assertNull(filter.filter(miss));
  }

  private FeatureTuple tuple(String id, long featureNumber, int value) {
    FeatureTuple tuple = new FeatureTuple(new TupleNumber(1L, 0, 0, featureNumber, 0L), null);
    NakshaFeature feature = new NakshaFeature(id);
    feature.getProperties().put("value", value);
    tuple.setFeature(feature);
    return tuple;
  }

  @Test void keepsHistoryGuidsAndUnsupportedPrecisionOnLegacyPath() {
    ReadFeatures request = new ReadFeatures().withPropertyQuery(query(DoubleOp.EQ, 50));
    request.setQueryHistory(true);
    assertNull(PropertyMemberQueryTranslator.adapt(request, schema()));
    request.setQueryHistory(false);
    Guid guid = new Guid("one", TupleNumber.TupleNumber_C.getHEAD());
    request.getGuids().add(guid);
    assertNull(PropertyMemberQueryTranslator.adapt(request, schema()));
    assertEquals(1, request.getGuids().size());
    assertEquals(guid, request.getGuids().get(0));
    request.getGuids().clear();
    Equals nativeQuery = new Equals(StandardMembers.Id, "one");
    request.setQueryMembers(nativeQuery);
    assertNull(PropertyMemberQueryTranslator.adapt(request, schema()));
    assertSame(nativeQuery, request.getQueryMembers());
    assertNull(PropertyMemberQueryTranslator.translate(query(DoubleOp.EQ, 9_007_199_254_740_992.0), schema(MemberType.INT64)));
    assertNull(PropertyMemberQueryTranslator.translate(query(DoubleOp.EQ, 0.1), schema(MemberType.FLOAT32)));
    assertInstanceOf(Equals.class, PropertyMemberQueryTranslator.translate(query(DoubleOp.EQ, 0.5), schema(MemberType.FLOAT32)));
    assertInstanceOf(And.class, PropertyMemberQueryTranslator.translate(query(DoubleOp.GT, 0.5), schema(MemberType.FLOAT64)));
  }

  @Test void supportsStringsAndExactTagListContainment() {
    assertInstanceOf(Equals.class, PropertyMemberQueryTranslator.translate(query(StringOp.EQUALS, "abc"), schema(MemberType.STRING)));
    assertInstanceOf(StartsWith.class, PropertyMemberQueryTranslator.translate(query(StringOp.STARTS_WITH, "a_%"), schema(MemberType.STRING)));
    assertInstanceOf(TagListContains.class, PropertyMemberQueryTranslator.translate(query(AnyOp.CONTAINS, "red"), schema(MemberType.TAG_LIST)));
    assertNull(PropertyMemberQueryTranslator.translate(query(AnyOp.CONTAINS, "[\"red\"]"), schema(MemberType.TAG_LIST)));
    assertNull(PropertyMemberQueryTranslator.translate(query(AnyOp.CONTAINS, "{\"a\":1}"), schema(MemberType.TAG_MAP)));
  }

  @Test void unknownCallbacksAndUnattachedPropertyFiltersKeepLegacyBehavior() {
    ReadFeatures request = new ReadFeatures().withPropertyQuery(query(DoubleOp.EQ, 50));
    request.getResultFilters().add(featureTuple -> featureTuple);
    assertNull(PropertyMemberQueryTranslator.adapt(request, schema()));
    request.getResultFilters().clear();
    assertNull(PropertyMemberQueryTranslator.adapt(request, schema()));
    assertNotNull(request.getQuery().getProperties());
  }
}
