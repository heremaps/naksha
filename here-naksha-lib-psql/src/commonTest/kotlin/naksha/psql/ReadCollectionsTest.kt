package naksha.psql

import naksha.model.objects.NakshaCollection
import naksha.model.objects.StoreMode.StoreMode_C.ON
import naksha.model.request.ReadCollections
import naksha.psql.PgTest.PgTest_C.TEST_MAP_ID
import naksha.psql.base.PgTestBase
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ReadCollectionsTest : PgTestBase(NakshaCollection(
    id = "read_collections_c",
    mapId = TEST_MAP_ID,
    partitions = 2,
    storeDeleted = ON,
    storeHistory = ON,
    storeMeta = ON,
)) {

    @Test
    fun shouldReadCollectionMeta() {
        // When
        val retrievedCollectionMeta = executeRead(ReadCollections().apply {
            collectionIds += collection.id
        })

        // Then
        assertEquals(1, retrievedCollectionMeta.features.size)
        val collectionFeature = retrievedCollectionMeta.features[0]!!.proxy(NakshaCollection::class)
        assertEquals(collection.id, collectionFeature.id)
        assertEquals(collection.partitions, collectionFeature.partitions)
        assertEquals(collection.storeDeleted, collectionFeature.storeDeleted)
        assertEquals(collection.storeMeta, collectionFeature.storeMeta)
        assertEquals(collection.storeHistory, collectionFeature.storeHistory)
    }
}
