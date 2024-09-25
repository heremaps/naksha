package naksha.psql

import naksha.model.Naksha
import naksha.model.objects.NakshaCollection
import naksha.model.request.ReadFeatures
import naksha.model.request.Write
import naksha.model.request.WriteRequest
import naksha.psql.base.PgTestBase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

class DropCollectionTest : PgTestBase(collection = null) {

    @Test
    fun shouldDropCollection() {
        // Given: collection that will be tested
        val collection = NakshaCollection("drop_collection_test")

        // When: creating empty collection
        executeWrite(
            WriteRequest().add(
                Write().createCollection(null, collection)
            )
        )

        // Then: this collection is queryable and empty
        val readAllFromCollection = ReadFeatures().apply { collectionIds += collection.id }
        val collectionContent = executeRead(readAllFromCollection)
        assertEquals(0, collectionContent.features.size)

        // And: Virtual Collections contain the created collection
        val selectCollectionFromVirt = ReadFeatures().apply {
            collectionIds += Naksha.VIRT_COLLECTIONS
            featureIds += collection.id
        }
        val virtBeforeDelete = executeRead(selectCollectionFromVirt)
        assertEquals(1, virtBeforeDelete.features.size)

        // When: Collection gets deleted
        executeWrite(
            WriteRequest().add(
                Write().deleteCollectionById(null, collectionId = collection.id)
            )
        )

        // Then: it is not present in Virtual Collections anymore
        val virtAfterDelete = executeRead(selectCollectionFromVirt)
        assertEquals(0, virtAfterDelete.features.size)

        // And: reading from this collection fails
        assertFails("ERROR: relation \"${collection.id}\" does not exist") {
            executeRead(readAllFromCollection)
        }
    }
}