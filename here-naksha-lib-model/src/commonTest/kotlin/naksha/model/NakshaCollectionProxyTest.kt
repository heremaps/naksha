package naksha.model

import naksha.model.objects.NakshaCollection
import naksha.model.objects.StoreMode
import kotlin.test.Test
import kotlin.test.assertEquals

class NakshaCollectionProxyTest {

    @Test
    fun shouldCreateObjectWithSecondaryConstructor() {
        // when
        val collection = NakshaCollection(
            id = "ID",
            mapId = "MAP_ID",
            partitions = 3,
            storeDeleted = StoreMode.SUSPEND,
            storeHistory = StoreMode.OFF
        )
        collection.maxAge = 42L

        // expect
        assertEquals("ID", collection.id)
        assertEquals(3, collection.partitions)
        assertEquals(42, collection.maxAge.toInt())
        assertEquals(StoreMode.SUSPEND, collection.storeDeleted)
        assertEquals(StoreMode.OFF, collection.storeHistory)
    }
}
