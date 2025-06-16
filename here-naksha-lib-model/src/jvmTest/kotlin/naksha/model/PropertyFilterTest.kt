package naksha.model

import naksha.base.AnyObject
import naksha.base.Int64
import naksha.jbon.JbEncoder
import naksha.model.Naksha.Naksha_C.featureNumber
import naksha.model.objects.NakshaFeature
import naksha.model.request.PropertyFilter
import naksha.model.request.ReadFeatures
import naksha.model.request.FeatureTuple
import naksha.model.request.query.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class PropertyFilterTest {

    companion object {
        lateinit var featureTuple : FeatureTuple
        val nestedJson = AnyObject()

        @JvmStatic
        @BeforeAll
        fun setupTuple() {
            // create the feature
            val feature = NakshaFeature()
            feature["eventHandlerIds"] = arrayOf("handler-abc", "handler-xyz")
            feature.properties["foo"] = "bar"
            feature.properties["number"] = 1.1
            nestedJson["bool"] = true
            nestedJson["nullProps"] = null
            val innerJson = AnyObject()
            innerJson["a"] = 1
            nestedJson["array"] = arrayOf("one", "two", "three", innerJson)
            feature.properties["json"] = nestedJson
            val references = arrayOf(
                AnyObject().apply {
                    put("id", "ref-1")
                    put("type", "primary")
                },
                AnyObject().apply {
                    put("id", "ref-2")
                    put("type", "secondary")
                    put("active", true)
                }
            )
            feature.properties["references"] = references
            val nestedArray = arrayOf(1, arrayOf("a", "b", arrayOf(100, 200)), "c")
            feature.properties["nestedArray"] = nestedArray
            // build tuple containing the feature
            val encoder = JbEncoder()
            val featureBytes = encoder.buildFeatureFromMap(feature)
            val storageNumber = Int64(1)
            val mapNumber = 0
            val collectionNumber = 0
            val version = Version(0)
            val flags = Flags()
            val tupleNumber = TupleNumber(storageNumber, mapNumber, collectionNumber, featureNumber(feature.id), version,0)
            val tuple = Tuple(
                meta = Metadata(
                    tupleNumber = tupleNumber,
                    updatedAt = Int64(0),
                    id = feature.id,
                    appId = "",
                    author = null,
                    flags = flags,
                ),
                feature = featureBytes
            )
            featureTuple = FeatureTuple(tupleNumber, tuple)
        }
    }

    @Test
    fun stringEqual() {
        val request = ReadFeatures()
        val filter = PropertyFilter(request)
        request.query.properties = PQuery(Property("properties","foo"),StringOp.EQUALS,"bar")
        assertEquals(featureTuple,filter.filter(featureTuple))
    }

    @Test
    fun stringNotEqual() {
        val request = ReadFeatures()
        val filter = PropertyFilter(request)
        request.query.properties = PQuery(Property("properties","foo"),StringOp.EQUALS,"foooooo")
        assertEquals(null,filter.filter(featureTuple))
    }

    @Test
    fun stringStartWith() {
        val request = ReadFeatures()
        val filter = PropertyFilter(request)
        request.query.properties = PQuery(Property("properties","foo"),StringOp.STARTS_WITH,"b")
        assertEquals(featureTuple,filter.filter(featureTuple))
    }

    @Test
    fun stringNotStartWith() {
        val request = ReadFeatures()
        val filter = PropertyFilter(request)
        request.query.properties = PQuery(Property("properties","foo"),StringOp.STARTS_WITH,"a")
        assertEquals(null,filter.filter(featureTuple))
    }

    @Test
    fun numberEqual() {
        val request = ReadFeatures()
        val filter = PropertyFilter(request)
        request.query.properties = PQuery(Property("properties","number"),DoubleOp.EQ,1.1)
        assertEquals(featureTuple,filter.filter(featureTuple))
    }

    @Test
    fun numberGreaterThan() {
        val request = ReadFeatures()
        val filter = PropertyFilter(request)
        request.query.properties = PQuery(Property("properties","number"),DoubleOp.GT,1)
        assertEquals(featureTuple,filter.filter(featureTuple))
    }

    @Test
    fun numberNotLowerThan() {
        val request = ReadFeatures()
        val filter = PropertyFilter(request)
        request.query.properties = PQuery(Property("properties","number"),DoubleOp.LT,1.1)
        assertEquals(null,filter.filter(featureTuple))
    }

    @Test
    fun numberNotGreaterThanOrEqual() {
        val request = ReadFeatures()
        val filter = PropertyFilter(request)
        request.query.properties = PQuery(Property("properties","number"),DoubleOp.GTE,2)
        assertEquals(null,filter.filter(featureTuple))
    }

    @Test
    fun numberLowerThanOrEqual() {
        val request = ReadFeatures()
        val filter = PropertyFilter(request)
        request.query.properties = PQuery(Property("properties","number"),DoubleOp.LTE,1.1)
        assertEquals(featureTuple,filter.filter(featureTuple))
    }

    @Test
    fun andQueryNumberString() {
        val request = ReadFeatures()
        val filter = PropertyFilter(request)
        request.query.properties = PAnd(
            PQuery(Property("properties","number"),DoubleOp.LTE,1.1),
            PQuery(Property("properties","foo"),StringOp.EQUALS,"bar")
        )
        assertEquals(featureTuple,filter.filter(featureTuple))
    }

    @Test
    fun andQueryNumberStringFilteredOut() {
        val request = ReadFeatures()
        val filter = PropertyFilter(request)
        request.query.properties = PAnd(
            PQuery(Property("properties","number"),DoubleOp.LTE,0),
            PQuery(Property("properties","foo"),StringOp.EQUALS,"bar")
        )
        assertEquals(null,filter.filter(featureTuple))
    }

    @Test
    fun orQueryNumberString() {
        val request = ReadFeatures()
        val filter = PropertyFilter(request)
        request.query.properties = POr(
            PQuery(Property("properties","number"),DoubleOp.EQ,1.1),
            PQuery(Property("properties","foo"),StringOp.EQUALS,"foooo")
        )
        assertEquals(featureTuple,filter.filter(featureTuple))
    }

    @Test
    fun orQueryNumberStringFilteredOut() {
        val request = ReadFeatures()
        val filter = PropertyFilter(request)
        request.query.properties = POr(
            PQuery(Property("properties","number"),DoubleOp.EQ,0),
            PQuery(Property("properties","foo"),StringOp.EQUALS,"foooo")
        )
        assertEquals(null,filter.filter(featureTuple))
    }

    @Test
    fun notQueryString() {
        val request = ReadFeatures()
        val filter = PropertyFilter(request)
        request.query.properties = PNot(PQuery(Property("properties","foo"),StringOp.STARTS_WITH,"a"))
        assertEquals(featureTuple,filter.filter(featureTuple))
    }

    @Test
    fun propExists() {
        val request = ReadFeatures()
        val filter = PropertyFilter(request)
        request.query.properties = PQuery(Property("properties","json"),AnyOp.EXISTS,null)
        assertEquals(featureTuple,filter.filter(featureTuple))
    }

    @Test
    fun propNotExists() {
        val request = ReadFeatures()
        val filter = PropertyFilter(request)
        request.query.properties = PQuery(Property("properties","json","ololo"),AnyOp.EXISTS,null)
        assertEquals(null,filter.filter(featureTuple))
    }

    @Test
    fun booleanPropTrue() {
        val request = ReadFeatures()
        val filter = PropertyFilter(request)
        request.query.properties = PQuery(Property("properties","json","bool"),AnyOp.IS_TRUE,null)
        assertEquals(featureTuple,filter.filter(featureTuple))
    }

    @Test
    fun booleanPropNotFalse() {
        val request = ReadFeatures()
        val filter = PropertyFilter(request)
        request.query.properties = PQuery(Property("properties","json","bool"),AnyOp.IS_FALSE,null)
        assertEquals(null,filter.filter(featureTuple))
    }

    @Test
    fun valueIsNull() {
        val request = ReadFeatures()
        val filter = PropertyFilter(request)
        request.query.properties = PQuery(Property("properties","json","nullProps"),AnyOp.IS_NULL,null)
        assertEquals(featureTuple,filter.filter(featureTuple))
    }

    @Test
    fun valueIsNotNull() {
        val request = ReadFeatures()
        val filter = PropertyFilter(request)
        request.query.properties = PQuery(Property("properties","json","nullProps"),AnyOp.IS_NOT_NULL,null)
        assertEquals(null,filter.filter(featureTuple))
    }

    @Test
    fun valueIsAnyOf() {
        val request = ReadFeatures()
        val filter = PropertyFilter(request)
        request.query.properties = PQuery(Property("properties","foo"),AnyOp.IS_ANY_OF, listOf("bar","barz"))
        assertEquals(featureTuple,filter.filter(featureTuple))
    }

    @Test
    fun valueIsNotAnyOf() {
        val request = ReadFeatures()
        val filter = PropertyFilter(request)
        request.query.properties = PQuery(Property("properties","foo"),AnyOp.IS_ANY_OF, arrayOf("hoho","haha"))
        assertEquals(null,filter.filter(featureTuple))
    }

    @Test
    fun valueContainsNumber() {
        val request = ReadFeatures()
        val filter = PropertyFilter(request)
        request.query.properties = PQuery(Property("properties","number"),AnyOp.CONTAINS, 1.1)
        assertEquals(featureTuple,filter.filter(featureTuple))
    }

    @Test
    fun valueContainsBoolean() {
        val request = ReadFeatures()
        val filter = PropertyFilter(request)
        request.query.properties = PQuery(Property("properties","json","bool"),AnyOp.CONTAINS, true)
        assertEquals(featureTuple,filter.filter(featureTuple))
    }

    @Test
    fun valueArrayContainsString() {
        val request = ReadFeatures()
        val filter = PropertyFilter(request)
        request.query.properties = PQuery(Property("properties","json","array"),AnyOp.CONTAINS, arrayOf("two", "three"))
        assertEquals(featureTuple,filter.filter(featureTuple))
    }

    @Test
    fun valueArrayNotContainsString() {
        val request = ReadFeatures()
        val filter = PropertyFilter(request)
        request.query.properties = PQuery(Property("properties","json","array"),AnyOp.CONTAINS, arrayOf("four", "three"))
        assertEquals(null,filter.filter(featureTuple))
    }

    @Test
    fun valueContainsJson() {
        val request = ReadFeatures()
        val filter = PropertyFilter(request)
        request.query.properties = PQuery(Property("properties","json"),AnyOp.CONTAINS, nestedJson.copy(true))
        assertEquals(featureTuple,filter.filter(featureTuple))
    }

    @Test
    fun nullQuery() {
        val request = ReadFeatures()
        val filter = PropertyFilter(request)
        request.query.properties = null
        assertEquals(featureTuple,filter.filter(featureTuple))
    }

    @Test
    fun valueContainsStringWithCustomRoot() {
        val request = ReadFeatures()
        val filter = PropertyFilter(request)
        request.query.properties = PQuery(Property("eventHandlerIds"), AnyOp.CONTAINS, "handler-abc")
        assertEquals(featureTuple, filter.filter(featureTuple))
    }

    @Test
    fun valueNotContainsStringWithCustomRoot() {
        val request = ReadFeatures()
        val filter = PropertyFilter(request)
        request.query.properties = PQuery(Property("eventHandlerIds"), AnyOp.CONTAINS, "handler-123")
        assertEquals(null, filter.filter(featureTuple))
    }

    @Test
    fun arrayContainsObjectSubset() {
        val request = ReadFeatures()
        val filter = PropertyFilter(request)
        val queryJson = """{"id":"ref-2","active":true}"""
        request.query.properties = PQuery(Property("properties","references"), AnyOp.CONTAINS, queryJson)
        assertEquals(featureTuple, filter.filter(featureTuple), "Should match when query is a subset of an object in the array")
    }

    @Test
    fun arrayContainsObjectFromJsonArrayString() {
        val request = ReadFeatures()
        val filter = PropertyFilter(request)
        val queryJson = """[{"id":"ref-1"}]"""
        request.query.properties = PQuery(Property("properties", "references"), AnyOp.CONTAINS, queryJson)
        assertEquals(featureTuple, filter.filter(featureTuple), "Should match when query is a JSON array string of objects")
    }

    @Test
    fun singleObjectContainsSubset() {
        val request = ReadFeatures()
        val filter = PropertyFilter(request)
        val queryJson = """{"bool":true}"""
        request.query.properties = PQuery(Property("properties","json"), AnyOp.CONTAINS, queryJson)
        assertEquals(featureTuple, filter.filter(featureTuple), "Should match when feature property is an object containing the query subset")
    }

    @Test
    fun scalarInArrayMatchesAtTopLevel() {
        val request = ReadFeatures()
        val filter = PropertyFilter(request)
        request.query.properties = PQuery(Property("properties", "nestedArray"), AnyOp.CONTAINS, 1)
        assertEquals(featureTuple, filter.filter(featureTuple), "Should find a scalar value at the top level of an array")
    }

    @Test
    fun scalarInArrayFailsAtDeeperLevel() {
        val request = ReadFeatures()
        val filter = PropertyFilter(request)
        request.query.properties = PQuery(Property("properties", "nestedArray"), AnyOp.CONTAINS, 200)
        assertEquals(null, filter.filter(featureTuple), "Should NOT find a scalar value deep inside a nested array")
    }

    @Test
    fun nestedArrayInArrayMatchesAtTopLevel() {
        val request = ReadFeatures()
        val filter = PropertyFilter(request)
        val queryJson = """[["a", "b", [100, 200]]]"""
        request.query.properties = PQuery(Property("properties", "nestedArray"), AnyOp.CONTAINS, queryJson)
        assertEquals(featureTuple, filter.filter(featureTuple), "Should match a nested array as a whole element")
    }

    @Test
    fun elementInNestedArrayFailsToMatchAtTopLevel() {
        val request = ReadFeatures()
        val filter = PropertyFilter(request)
        val queryJson = """["a"]"""
        request.query.properties = PQuery(Property("properties", "nestedArray"), AnyOp.CONTAINS, queryJson)
        assertEquals(null, filter.filter(featureTuple), "Should NOT match an element from a nested array at the top level")
    }

    @Test
    fun arrayContainsAllWithDuplicateQueryElements() {
        val request = ReadFeatures()
        val filter = PropertyFilter(request)
        val queryJson = """["one", "two", "two"]"""
        request.query.properties = PQuery(Property("properties", "json", "array"), AnyOp.CONTAINS, queryJson)
        assertEquals(featureTuple, filter.filter(featureTuple), "Should match even with duplicate elements in the query")
    }

    @Test
    fun objectDoesNotContainNestedPair() {
        val request = ReadFeatures()
        val filter = PropertyFilter(request)
        val queryJson = """{"a": 1}"""
        request.query.properties = PQuery(Property("properties", "json"), AnyOp.CONTAINS, queryJson)
        assertEquals(null, filter.filter(featureTuple), "Should NOT find a key-value pair from a nested object")
    }

    @Test
    fun objectContainsSubsetWithEmptyObjectValue() {
        val request = ReadFeatures()
        val filter = PropertyFilter(request)
        val queryJson = """{"json": {}}"""
        request.query.properties = PQuery(Property("properties"), AnyOp.CONTAINS, queryJson)
        assertEquals(featureTuple, filter.filter(featureTuple), "Should match when a value is checked for containment against an empty object")
    }

    @Test
    fun containsEmptyArrayQuery() {
        val request = ReadFeatures()
        val filter = PropertyFilter(request)
        val queryJson = "[]"
        request.query.properties = PQuery(Property("properties", "references"), AnyOp.CONTAINS, queryJson)
        assertEquals(featureTuple, filter.filter(featureTuple), "Should match when the contains query is an empty array")
    }
}