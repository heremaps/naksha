package naksha.model

import naksha.base.Int64
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
            partitions = 3,
            storeDeleted = StoreMode.SUSPEND,
            storeHistory = StoreMode.OFF
        )
        collection.maxAge = Int64(42)

        // expect
        assertEquals("ID", collection.id)
        assertEquals(3, collection.partitions)
        assertEquals(42, collection.maxAge.toInt())
        assertEquals(StoreMode.SUSPEND, collection.storeDeleted)
        assertEquals(StoreMode.OFF, collection.storeHistory)
    }
}