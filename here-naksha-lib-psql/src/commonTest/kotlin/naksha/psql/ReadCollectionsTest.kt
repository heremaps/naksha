package naksha.psql

import naksha.model.objects.NakshaCollection
import naksha.model.objects.StoreMode.StoreMode_C.ON
import naksha.model.request.ReadCollections
import kotlin.test.Test
import kotlin.test.assertEquals

class ReadCollectionsTest : PgTestBase(collection = NakshaCollection()
    .withPartitions(2)
    .withStoreDeleted(ON)
    .withStoreHistory(ON)
    .withStoreMeta(ON)
) {

    @Test
    fun shouldReadCollectionMeta() {
        // When
        val retrievedCollectionMeta = executeReadAndLoadTuple(ReadCollections(catalog).readCollection(collection.id))

        // Then
        assertEquals(1, retrievedCollectionMeta.asFeatures.size)
        val collectionFeature = retrievedCollectionMeta.asFeatures[0]!!.proxy(NakshaCollection::class)
        assertEquals(collection.id, collectionFeature.id)
        assertEquals(collection.partitions, collectionFeature.partitions)
        assertEquals(collection.storeDeleted, collectionFeature.storeDeleted)
        assertEquals(collection.storeMeta, collectionFeature.storeMeta)
        assertEquals(collection.storeHistory, collectionFeature.storeHistory)
    }
}
