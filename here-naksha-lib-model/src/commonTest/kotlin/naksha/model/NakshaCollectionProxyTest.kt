package naksha.model

import naksha.base.Int64
import naksha.base.Platform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NakshaCollectionProxyTest {

    @Test
    fun shouldCreateObjectWithSecondaryConstructor() {
        // when
        val collection = NakshaCollectionProxy(
            id = "ID",
            partitions = 3,
            autoPurge = true,
            disableHistory = true
        )
        collection.maxAge = Int64(42)

        // expect
        assertEquals("ID", collection.id)
        assertEquals(3, collection.partitions)
        assertEquals(42, collection.maxAge.toInt())
        assertTrue(collection.autoPurge)
        assertTrue(collection.disableHistory)
    }
}