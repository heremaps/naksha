package naksha.psql

import naksha.model.*
import naksha.model.objects.NakshaCollection
import naksha.model.objects.NakshaFeature
import naksha.model.objects.StoreMode
import naksha.model.request.ReadFeatures
import naksha.model.request.Write
import naksha.model.request.WriteRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame

class DeleteFeatureTest : PgTestBase() {

    @Test
    fun shouldPerformDelete() {
        val featureId = "feature_to_delete"
        val initialFeature = executeWrite(
            WriteRequest().add(
                Write().createFeature(collection.mapId, collection.id, NakshaFeature(featureId))
            )
        ).let { // this = SuccessResponse
            val features = assertNotNull(it.features)
            assertEquals(1, features.size)
            assertNotNull(features.first())
        }
        assertEquals(featureId, initialFeature.id)
        assertEquals(1 ,initialFeature.properties.xyz.changeCount)

        val deletedFeatures = executeWrite(
            WriteRequest().add(
                Write().deleteFeatureById(collection.mapId, collection.id, featureId)
            )
        ).let { // this = SuccessResponse
            val features = assertNotNull(it.features)
            assertEquals(1, features.size)
            assertNotNull(features.first())
        }
        assertEquals(featureId, deletedFeatures.id)
        assertEquals(2 ,deletedFeatures.properties.xyz.changeCount)

        // Verify that the feature does not exist
        Naksha.cache.clear()
        executeRead(ReadFeatures().apply {
            mapId = collection.mapId
            collectionIds += collection.id
            featureIds += initialFeature.id
        }).let { // this = SuccessResponse
            val features = assertNotNull(it.features)
            assertEquals(0, features.size)
        }

        // verify if history contains 2 versions
        executeRead(ReadFeatures().apply {
            mapId = collection.mapId
            collectionIds += collection.id
            featureIds += initialFeature.id
            queryHistory = true
            versions = 10
        }).apply { // this = SuccessResponse
            assertEquals(2, features.size)
            assertSame(Action.DELETED, featureTupleList[0]?.tuple?.meta?.flags?.actionEnum())
            assertSame(Action.CREATED, featureTupleList[1]?.tuple?.meta?.flags?.actionEnum())
        }

        // verify if delete table contains element
        executeRead(ReadFeatures().apply {
            mapId = collection.mapId
            collectionIds += collection.id
            featureIds += initialFeature.id
            queryDeleted = true
        }).apply { // this = SuccessResponse
            assertEquals(1, features.size)
            val deletedFeature = assertNotNull(features[0])
            assertEquals(initialFeature.id, deletedFeature.id)
            assertEquals(Action.DELETED, deletedFeature.properties.xyz.action)
        }
    }

    @Test
    fun deleteWithoutHistoryButWithShadow() {
        // Create special test collection.
        val createCollectionReq = WriteRequest().add(
            Write().createCollection(
                NakshaCollection("delete_no_history_but_shadow", map.id)
                    .withStoreDeleted(StoreMode.ON)
                    .withStoreMeta(StoreMode.OFF)
                    .withStoreHistory(StoreMode.OFF)
            )
        )
        val createCollectionResp = executeWrite(createCollectionReq)
        assertEquals(1, createCollectionResp.length)
        assertEquals(1, createCollectionResp.features.size)
        val collection = assertNotNull(createCollectionResp.features[0]).proxy(NakshaCollection::class)
        assertEquals(map.id, collection.mapId)
        assertEquals("delete_no_history_but_shadow", collection.id)

        // Create feature.
        val featureId = "feature_to_delete_without_history"
        val createFeatureReq = WriteRequest().add(
            Write().createFeature(collection, NakshaFeature(featureId))
        )
        val createFeatureResp = executeWrite(createFeatureReq)
        assertEquals(1, createFeatureResp.length)
        assertEquals(1, createFeatureResp.features.size)
        val feature = assertNotNull(createFeatureResp.features[0])

        // Delete the feature.
        val deleteFeaturesReq = WriteRequest().add(
            Write().deleteFeatureById(collection, featureId)
        )
        val deleteFeatureResp = executeWrite(deleteFeaturesReq)
        assertEquals(1, deleteFeatureResp.length)
        assertEquals(1, deleteFeatureResp.features.size)
        val deleteFeature = assertNotNull(deleteFeatureResp.features[0])
        assertEquals(feature.id, deleteFeature.id)
    }
}