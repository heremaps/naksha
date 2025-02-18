package naksha.model

import naksha.base.AnyObject
import naksha.base.Int64
import naksha.jbon.JbEncoder
import naksha.model.Naksha.NakshaCompanion.featureNumber
import naksha.model.Naksha.NakshaCompanion.hashId
import naksha.model.Naksha.NakshaCompanion.partitionNumber
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
            feature.properties["foo"] = "bar"
            feature.properties["number"] = 1.1
            nestedJson["bool"] = true
            nestedJson["nullProps"] = null
            val innerJson = AnyObject()
            innerJson["a"] = 1
            nestedJson["array"] = arrayOf("one", "two", "three", innerJson)
            feature.properties["json"] = nestedJson
            // build tuple containing the feature
            val encoder = JbEncoder()
            val featureBytes = encoder.buildFeatureFromMap(feature)
            val storageNumber = Int64(1)
            val mapNumber = 0
            val collectionNumber = 0
            val version = Version(0)
            val flags = Flags()
            val tupleNumber = TupleNumber(storageNumber, mapNumber, collectionNumber, featureNumber(hashId(feature.id)), version,0)
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
        request.query.properties = PQuery(Property("foo"),StringOp.EQUALS,"bar")
        assertEquals(featureTuple,filter.filter(featureTuple))
    }

    @Test
    fun stringNotEqual() {
        val request = ReadFeatures()
        val filter = PropertyFilter(request)
        request.query.properties = PQuery(Property("foo"),StringOp.EQUALS,"foooooo")
        assertEquals(null,filter.filter(featureTuple))
    }

    @Test
    fun stringStartWith() {
        val request = ReadFeatures()
        val filter = PropertyFilter(request)
        request.query.properties = PQuery(Property("foo"),StringOp.STARTS_WITH,"b")
        assertEquals(featureTuple,filter.filter(featureTuple))
    }

    @Test
    fun stringNotStartWith() {
        val request = ReadFeatures()
        val filter = PropertyFilter(request)
        request.query.properties = PQuery(Property("foo"),StringOp.STARTS_WITH,"a")
        assertEquals(null,filter.filter(featureTuple))
    }

    @Test
    fun numberEqual() {
        val request = ReadFeatures()
        val filter = PropertyFilter(request)
        request.query.properties = PQuery(Property("number"),DoubleOp.EQ,1.1)
        assertEquals(featureTuple,filter.filter(featureTuple))
    }

    @Test
    fun numberGreaterThan() {
        val request = ReadFeatures()
        val filter = PropertyFilter(request)
        request.query.properties = PQuery(Property("number"),DoubleOp.GT,1)
        assertEquals(featureTuple,filter.filter(featureTuple))
    }

    @Test
    fun numberNotLowerThan() {
        val request = ReadFeatures()
        val filter = PropertyFilter(request)
        request.query.properties = PQuery(Property("number"),DoubleOp.LT,1.1)
        assertEquals(null,filter.filter(featureTuple))
    }

    @Test
    fun numberNotGreaterThanOrEqual() {
        val request = ReadFeatures()
        val filter = PropertyFilter(request)
        request.query.properties = PQuery(Property("number"),DoubleOp.GTE,2)
        assertEquals(null,filter.filter(featureTuple))
    }

    @Test
    fun numberLowerThanOrEqual() {
        val request = ReadFeatures()
        val filter = PropertyFilter(request)
        request.query.properties = PQuery(Property("number"),DoubleOp.LTE,1.1)
        assertEquals(featureTuple,filter.filter(featureTuple))
    }

    @Test
    fun andQueryNumberString() {
        val request = ReadFeatures()
        val filter = PropertyFilter(request)
        request.query.properties = PAnd(
            PQuery(Property("number"),DoubleOp.LTE,1.1),
            PQuery(Property("foo"),StringOp.EQUALS,"bar")
        )
        assertEquals(featureTuple,filter.filter(featureTuple))
    }

    @Test
    fun andQueryNumberStringFilteredOut() {
        val request = ReadFeatures()
        val filter = PropertyFilter(request)
        request.query.properties = PAnd(
            PQuery(Property("number"),DoubleOp.LTE,0),
            PQuery(Property("foo"),StringOp.EQUALS,"bar")
        )
        assertEquals(null,filter.filter(featureTuple))
    }

    @Test
    fun orQueryNumberString() {
        val request = ReadFeatures()
        val filter = PropertyFilter(request)
        request.query.properties = POr(
            PQuery(Property("number"),DoubleOp.EQ,1.1),
            PQuery(Property("foo"),StringOp.EQUALS,"foooo")
        )
        assertEquals(featureTuple,filter.filter(featureTuple))
    }

    @Test
    fun orQueryNumberStringFilteredOut() {
        val request = ReadFeatures()
        val filter = PropertyFilter(request)
        request.query.properties = POr(
            PQuery(Property("number"),DoubleOp.EQ,0),
            PQuery(Property("foo"),StringOp.EQUALS,"foooo")
        )
        assertEquals(null,filter.filter(featureTuple))
    }

    @Test
    fun notQueryString() {
        val request = ReadFeatures()
        val filter = PropertyFilter(request)
        request.query.properties = PNot(PQuery(Property("foo"),StringOp.STARTS_WITH,"a"))
        assertEquals(featureTuple,filter.filter(featureTuple))
    }

    @Test
    fun propExists() {
        val request = ReadFeatures()
        val filter = PropertyFilter(request)
        request.query.properties = PQuery(Property("json"),AnyOp.EXISTS,null)
        assertEquals(featureTuple,filter.filter(featureTuple))
    }

    @Test
    fun propNotExists() {
        val request = ReadFeatures()
        val filter = PropertyFilter(request)
        request.query.properties = PQuery(Property("json","ololo"),AnyOp.EXISTS,null)
        assertEquals(null,filter.filter(featureTuple))
    }

    @Test
    fun booleanPropTrue() {
        val request = ReadFeatures()
        val filter = PropertyFilter(request)
        request.query.properties = PQuery(Property("json","bool"),AnyOp.IS_TRUE,null)
        assertEquals(featureTuple,filter.filter(featureTuple))
    }

    @Test
    fun booleanPropNotFalse() {
        val request = ReadFeatures()
        val filter = PropertyFilter(request)
        request.query.properties = PQuery(Property("json","bool"),AnyOp.IS_FALSE,null)
        assertEquals(null,filter.filter(featureTuple))
    }

    @Test
    fun valueIsNull() {
        val request = ReadFeatures()
        val filter = PropertyFilter(request)
        request.query.properties = PQuery(Property("json","nullProps"),AnyOp.IS_NULL,null)
        assertEquals(featureTuple,filter.filter(featureTuple))
    }

    @Test
    fun valueIsNotNull() {
        val request = ReadFeatures()
        val filter = PropertyFilter(request)
        request.query.properties = PQuery(Property("json","nullProps"),AnyOp.IS_NOT_NULL,null)
        assertEquals(null,filter.filter(featureTuple))
    }

    @Test
    fun valueIsAnyOf() {
        val request = ReadFeatures()
        val filter = PropertyFilter(request)
        request.query.properties = PQuery(Property("foo"),AnyOp.IS_ANY_OF, listOf("bar","barz"))
        assertEquals(featureTuple,filter.filter(featureTuple))
    }

    @Test
    fun valueIsNotAnyOf() {
        val request = ReadFeatures()
        val filter = PropertyFilter(request)
        request.query.properties = PQuery(Property("foo"),AnyOp.IS_ANY_OF, arrayOf("hoho","haha"))
        assertEquals(null,filter.filter(featureTuple))
    }

    @Test
    fun valueContainsNumber() {
        val request = ReadFeatures()
        val filter = PropertyFilter(request)
        request.query.properties = PQuery(Property("number"),AnyOp.CONTAINS, 1.1)
        assertEquals(featureTuple,filter.filter(featureTuple))
    }

    @Test
    fun valueContainsBoolean() {
        val request = ReadFeatures()
        val filter = PropertyFilter(request)
        request.query.properties = PQuery(Property("json","bool"),AnyOp.CONTAINS, true)
        assertEquals(featureTuple,filter.filter(featureTuple))
    }

    @Test
    fun valueArrayContainsString() {
        val request = ReadFeatures()
        val filter = PropertyFilter(request)
        request.query.properties = PQuery(Property("json","array"),AnyOp.CONTAINS, arrayOf("two", "three"))
        assertEquals(featureTuple,filter.filter(featureTuple))
    }

    @Test
    fun valueArrayNotContainsString() {
        val request = ReadFeatures()
        val filter = PropertyFilter(request)
        request.query.properties = PQuery(Property("json","array"),AnyOp.CONTAINS, arrayOf("four", "three"))
        assertEquals(null,filter.filter(featureTuple))
    }

    @Test
    fun valueContainsJson() {
        val request = ReadFeatures()
        val filter = PropertyFilter(request)
        request.query.properties = PQuery(Property("json"),AnyOp.CONTAINS, nestedJson.copy(true))
        assertEquals(featureTuple,filter.filter(featureTuple))
    }

    @Test
    fun nullQuery() {
        val request = ReadFeatures()
        val filter = PropertyFilter(request)
        request.query.properties = null
        assertEquals(featureTuple,filter.filter(featureTuple))
    }
}