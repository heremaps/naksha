package naksha.model

import naksha.base.Int64
import naksha.jbon.JbEncoder
import naksha.model.objects.NakshaFeature
import naksha.model.request.ExecutedOp
import naksha.model.request.PropertyFilter
import naksha.model.request.ReadFeatures
import naksha.model.request.ResultTuple
import naksha.model.request.query.PQuery
import naksha.model.request.query.Property
import naksha.model.request.query.StringOp
import org.mockito.kotlin.mock
import kotlin.test.Test
import kotlin.test.assertEquals

class PropertyFilterTest {

    @Test
    fun testFilter() {
        val feature = NakshaFeature()
        feature.properties["foo"] = "bar"
        val encoder = JbEncoder()
        val byteArray = encoder.buildFeatureFromMap(feature)
        val storeNumber = StoreNumber(0, Int64(0))
        val version = Version(0)
        val flag = Flags(0)
        val tupleNumber = TupleNumber(
            storeNumber = storeNumber,
            uid = 0,
            version = version,
        )
        val mockStorage = mock<IStorage>()
        val tuple = Tuple(
            storage = mockStorage,
            tupleNumber = tupleNumber,
            Metadata(
                storeNumber = storeNumber,
                updatedAt = Int64(0),
                uid = 0,
                id = "",
                appId = "",
                author = null,
                version = version,
                type = null,
                flags = flag,
                ),
            byteArray
        )
        val resulTuple = ResultTuple(
            storage = mockStorage,
            tupleNumber = tupleNumber,
            op = ExecutedOp.READ,
            featureId = null,
            tuple = tuple
        )
        val request = ReadFeatures()
        request.query.properties = PQuery(Property("foo"),StringOp.EQUALS,"bar")
        val filter = PropertyFilter(request)
        val outputTuple = filter.call(resulTuple)
        assertEquals(resulTuple,outputTuple)
    }
}