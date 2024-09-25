package naksha.model

import naksha.base.Int64
import naksha.jbon.JbEncoder
import naksha.model.objects.NakshaFeature
import kotlin.test.Test

class PropertyFilterTest {

    @Test
    fun filter() {
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
        val storage = MockStorage("",SessionOptions(),0,)
        val tuple = Tuple(
            storage = storage,
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
    }

    class MockStorage(
        override val id: String,
        override val adminOptions: SessionOptions,
        override var hardCap: Int,
        override val defaultMap: IMap
    ) : IStorage {
        override fun isInitialized(): Boolean = true

        override fun initStorage(params: Map<String, *>?) {}

        override fun get(mapId: String): IMap {
            throw UnsupportedOperationException()
        }

        override fun contains(mapId: String): Boolean = false

        override fun getMapId(mapNumber: Int): String? = null

        override fun tupleToFeature(tuple: Tuple): NakshaFeature = NakshaFeature()

        override fun featureToTuple(feature: NakshaFeature): Tuple {
            throw UnsupportedOperationException()
        }

        override fun newWriteSession(options: SessionOptions?): IWriteSession {
            throw UnsupportedOperationException()
        }

        override fun newReadSession(options: SessionOptions?): IReadSession {
            throw UnsupportedOperationException()
        }

        override fun close() {}

    }

}