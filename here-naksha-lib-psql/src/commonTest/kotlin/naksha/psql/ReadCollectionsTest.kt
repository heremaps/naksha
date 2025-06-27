package naksha.psql

import naksha.model.objects.NakshaCollection
import naksha.model.objects.NakshaFeatureList
import naksha.model.objects.StoreMode.StoreMode_C.ON
import naksha.model.request.ReadCollections
import kotlin.test.Test
import kotlin.test.assertEquals

class ReadCollectionsTest : PgTestBase(collection = NakshaCollection(
    id = "",
    partitions = 2,
    storeDeleted = ON,
    storeHistory = ON,
    storeMeta = ON,
)) {

    @Test
    fun shouldReadCollectionMeta() {
        // When
        val retrievedCollectionMeta = executeRead(ReadCollections().apply {
            mapId = map.id
            collectionIds += collection.id
        })

        // Then
        assertEquals(1, retrievedCollectionMeta.getFeatures(NakshaFeatureList.TYPE).size)
        val collectionFeature = retrievedCollectionMeta.getFeatures(NakshaFeatureList.TYPE)[0]!!.proxy(NakshaCollection.TYPE)
        assertEquals(collection.id, collectionFeature.id)
        assertEquals(collection.partitions, collectionFeature.partitions)
        assertEquals(collection.storeDeleted, collectionFeature.storeDeleted)
        assertEquals(collection.storeMeta, collectionFeature.storeMeta)
        assertEquals(collection.storeHistory, collectionFeature.storeHistory)
    }
}
