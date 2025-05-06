package naksha.psql

import naksha.model.Action
import naksha.model.Naksha
import naksha.model.RandomFeatures.RandomFeatures_C.randomFeatures
import naksha.model.objects.NakshaCollection
import naksha.model.objects.NakshaFeature
import naksha.model.request.ReadFeatures
import naksha.model.request.Write
import naksha.model.request.WriteRequest
import naksha.psql.PgTest.PgTest_C.TEST_MAP_ID
import kotlin.test.*

class ReadHistoryTest : PgTestBase() {

    companion object ReadHistoryTest_C {
        private const val COUNT = 10
        private val allFeatures: MutableMap<String, NakshaFeature> = mutableMapOf()
        private const val ALIAS = "alias"
    }

    @BeforeTest
    fun prepareTestData() {
        // Create random features
        val featuresToCreate = randomFeatures(COUNT)
        val writeFeaturesReq = WriteRequest().apply {
            featuresToCreate.forEach { featureToCreate ->
                add(Write().createFeature(collection.mapId, collection.id, featureToCreate))
            }
        }
        val writeFeaturesResp = executeWrite(writeFeaturesReq)
        assertEquals(COUNT, writeFeaturesResp.features.size)
        for (feature in writeFeaturesResp.features) {
            assertNotNull(feature)
            assertNull(allFeatures[feature.id])
            assertEquals(Action.CREATED, feature.properties.xyz.action)
            allFeatures[feature.id] = feature
        }
    }

    @Test
    fun checkSingleFeatureHistory() {
        // Pick one feature
        val createdFeature = allFeatures.firstNotNullOf { it.value }
        assertEquals(Action.CREATED, createdFeature.properties.xyz.guid?.tupleNumber?.action)
        val featureId = createdFeature.id

        // Update it.
        var updatedFeature1 = createdFeature.copy<NakshaFeature>(true)
        updatedFeature1.properties[ALIAS] = "first_update"
        executeWrite(WriteRequest().apply {
            add(Write().updateFeature(collection, updatedFeature1, true))
        }).apply {
            assertEquals(1, features.size)
            updatedFeature1 = assertNotNull(features.first())
            assertEquals(featureId, updatedFeature1.id)
            assertEquals(Action.UPDATED, updatedFeature1.properties.xyz.guid?.tupleNumber?.action)
        }

        // Update it a second time.
        var updatedFeature2 = updatedFeature1.copy<NakshaFeature>(true)
        updatedFeature2.properties[ALIAS] = "second_update"
        executeWrite(WriteRequest().apply {
            add(Write().updateFeature(collection, updatedFeature2, true))
        }).apply {
            assertEquals(1, features.size)
            updatedFeature2 = assertNotNull(features.first())
            assertEquals(featureId, updatedFeature2.id)
            assertEquals(Action.UPDATED, updatedFeature2.properties.xyz.guid?.tupleNumber?.action)
        }

        // Delete it.
        var deletedFeature: NakshaFeature
        executeWrite(WriteRequest().apply {
            add(Write().deleteFeatureById(collection, createdFeature.id))
        }).apply {
            assertEquals(1, features.size)
            deletedFeature = assertNotNull(features.first())
            assertEquals(featureId, deletedFeature.id)
            assertEquals(Action.DELETED, deletedFeature.properties.xyz.guid?.tupleNumber?.action)
        }

        // Clear cache, and read the history of the feature.
        Naksha.cache.clear()
        executeRead(ReadFeatures().apply {
            mapId = collection.mapId
            collectionIds.add(collection.id)
            featureIds.add(featureId)
            queryHistory = true
            versions = 10
        }).apply {
            // We expect to get 4 versions back, in descending order: deleted, updated2, updated1, created
            assertEquals(4, features.size)

            val delete = assertNotNull(features[0])
            val update2 = assertNotNull(features[1])
            val update1 = assertNotNull(features[2])
            val create = assertNotNull(features[3])

            assertEquals(featureId, delete.id)
            assertEquals(Action.DELETED, delete.properties.xyz.action)
            assertEquals(Action.DELETED, delete.properties.xyz.guid?.tupleNumber?.action)
            assertEquals(delete.properties.xyz.pguid, update2.properties.xyz.guid)
            assertEquals(delete.properties.xyz.nguid, delete.properties.xyz.guid)

            assertEquals(featureId, update2.id)
            assertEquals(Action.UPDATED, update2.properties.xyz.action)
            assertEquals(Action.UPDATED, update2.properties.xyz.guid?.tupleNumber?.action)
            assertEquals("second_update", update2.properties[ALIAS])
            assertEquals(update2.properties.xyz.pguid, update1.properties.xyz.guid)
            assertEquals(update2.properties.xyz.nguid, delete.properties.xyz.guid)

            assertEquals(featureId, update1.id)
            assertEquals(Action.UPDATED, update1.properties.xyz.action)
            assertEquals(Action.UPDATED, update1.properties.xyz.guid?.tupleNumber?.action)
            assertEquals("first_update", update1.properties[ALIAS])
            assertEquals(update1.properties.xyz.pguid, create.properties.xyz.guid)
            assertEquals(update1.properties.xyz.nguid, update2.properties.xyz.guid)

            assertEquals(featureId, create.id)
            assertEquals(Action.CREATED, create.properties.xyz.action)
            assertEquals(Action.CREATED, create.properties.xyz.guid?.tupleNumber?.action)
            assertNull(create.properties[ALIAS])
            assertNull(create.properties.xyz.pguid)
            assertEquals(create.properties.xyz.nguid, update1.properties.xyz.guid)
        }

        executeRead(ReadFeatures().apply {
            mapId = collection.mapId
            collectionIds.add(collection.id)
            featureIds.add(featureId)
            queryHistory = true
            versions = 2
        }).apply {
            // We expect to have 4 versions, but only want the latest 2 back
            // As specified, we expect descending order: deleted, updated2[, updated1, created]
            assertEquals(2, features.size)

            val delete = assertNotNull(features[0])
            val update2 = assertNotNull(features[1])

            assertEquals(featureId, delete.id)
            assertEquals(Action.DELETED, delete.properties.xyz.action)
            assertEquals(delete.properties.xyz.pguid, update2.properties.xyz.guid)
            assertEquals(delete.properties.xyz.nguid, delete.properties.xyz.guid)

            assertEquals(Action.UPDATED, update2.properties.xyz.action)
        }
    }
}
