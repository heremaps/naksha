package naksha.psql

import naksha.model.*
import naksha.model.objects.NakshaCollection
import naksha.model.objects.NakshaFeature
import naksha.model.objects.StoreMode
import naksha.model.request.ReadFeatures
import naksha.model.request.Write
import naksha.model.request.WriteRequest
import naksha.psql.PgTest.PgTest_C.TEST_MAP_ID
import naksha.psql.base.PgTestBase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame

class DeleteFeatureTest : PgTestBase(NakshaCollection("delete_feature_test_c")) {

    @Test
    fun shouldPerformDelete() {
        // Given: Initial state of feature
        val featureId = "feature_to_delete"
        val initialFeature = NakshaFeature().apply {
            id = featureId
        }
        val writeInitialFeature = WriteRequest().add(
            Write().createFeature(collection.mapId!!, collection.id, initialFeature)
        )
        val deleteFeaturesReq = WriteRequest().add(
            Write().deleteFeatureById(collection.mapId!!, collection.id, featureId)
        )

        // When: Writing initial version of feature
        executeWrite(writeInitialFeature)

        // And: Deleting feature
        executeWrite(deleteFeaturesReq)

        // And: Retrieving feature by id
        val response = executeRead(ReadFeatures().apply {
            collectionIds += collection.id
            featureIds += initialFeature.id
        })
        val responseFeatures = response.features
        assertEquals(0, responseFeatures.size)

        // verify if history contains 2 versions
        val historyResponse = executeRead(ReadFeatures().apply {
            collectionIds += collection.id
            featureIds += initialFeature.id
            queryHistory = true
            versions = 10
        })
        assertEquals(2, historyResponse.features.size)
        assertSame(Action.DELETED, historyResponse.featureTupleList?.get(0)?.tuple?.meta?.flags?.actionEnum())
        assertSame(Action.CREATED, historyResponse.featureTupleList?.get(1)?.tuple?.meta?.flags?.actionEnum())

        // verify if delete table contains element
        val deleteTableResponse = executeRead(ReadFeatures().apply {
            collectionIds += collection.id
            featureIds += initialFeature.id
            queryDeleted = true
        })
        assertEquals(1, deleteTableResponse.features.size)
        val deletedFeature = assertNotNull(deleteTableResponse.features[0])
        assertEquals(initialFeature.id, deletedFeature.id)
        assertEquals(Action.DELETED, deletedFeature.properties.xyz.action)
    }

    @Test
    fun deleteWithoutHistoryButWithShadow() {
        // Create special test collection.
        val createCollectionReq = WriteRequest().add(
            Write().createCollection(
                NakshaCollection("delete_no_history_but_shadow", TEST_MAP_ID)
                    .withStoreDeleted(StoreMode.ON)
                    .withStoreMeta(StoreMode.OFF)
                    .withStoreHistory(StoreMode.OFF)
            )
        )
        val createCollectionResp = executeWrite(createCollectionReq)
        assertEquals(1, createCollectionResp.resultSize())
        assertEquals(1, createCollectionResp.features.size)
        val collection = assertNotNull(createCollectionResp.features[0]).proxy(NakshaCollection::class)
        assertEquals(TEST_MAP_ID, collection.mapId)
        assertEquals("delete_no_history_but_shadow", collection.id)

        // Create feature.
        val featureId = "feature_to_delete_without_history"
        val createFeatureReq = WriteRequest().add(
            Write().createFeature(collection, NakshaFeature(featureId))
        )
        val createFeatureResp = executeWrite(createFeatureReq)
        assertEquals(1, createFeatureResp.resultSize())
        assertEquals(1, createFeatureResp.features.size)
        val feature = assertNotNull(createFeatureResp.features[0])

        // Delete the feature.
        val deleteFeaturesReq = WriteRequest().add(
            Write().deleteFeatureById(collection, featureId)
        )
        val deleteFeatureResp = executeWrite(deleteFeaturesReq)
        assertEquals(1, deleteFeatureResp.resultSize())
        assertEquals(1, deleteFeatureResp.features.size)
        val deleteFeature = assertNotNull(deleteFeatureResp.features[0])
        assertEquals(feature.id, deleteFeature.id)
    }
}