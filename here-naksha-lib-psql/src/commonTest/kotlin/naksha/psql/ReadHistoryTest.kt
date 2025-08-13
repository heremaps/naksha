package naksha.psql

import naksha.model.Action
import naksha.model.Naksha
import naksha.model.RandomFeatures.RandomFeatures_C.randomFeatures
import naksha.model.objects.NakshaFeature
import naksha.model.objects.NakshaFeatureList
import naksha.model.objects.NakshaObjectList
import naksha.model.request.ReadFeatures
import naksha.model.request.Write
import naksha.model.request.WriteRequest
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
        assertEquals(COUNT, writeFeaturesResp.getFeatures(NakshaFeatureList.TYPE).size)
        for (feature in writeFeaturesResp.getFeatures(NakshaFeatureList.TYPE)) {
            assertNotNull(feature)
            assertNull(allFeatures[feature.id])
            assertEquals(Action.CREATE, feature.properties.xyz.action)
            allFeatures[feature.id] = feature
        }
    }

    @Test
    fun checkSingleFeatureHistory() {
        // Pick one feature
        val createdFeature = allFeatures.firstNotNullOf { it.value }
        assertEquals(Action.CREATE, createdFeature.properties.xyz.guid?.tupleNumber?.action)
        val featureId = createdFeature.id

        // Update it.
        var updatedFeature1 = createdFeature.copy<NakshaFeature>(true)
        updatedFeature1.properties[ALIAS] = "first_update"
        executeWrite(WriteRequest().apply {
            add(Write().updateFeature(collection, updatedFeature1, true))
        }).apply {
            assertEquals(1, getFeatures(NakshaFeatureList.TYPE).size)
            updatedFeature1 = assertNotNull(getFeatures(NakshaFeatureList.TYPE).first())
            assertEquals(featureId, updatedFeature1.id)
            assertEquals(Action.UPDATE, updatedFeature1.properties.xyz.guid?.tupleNumber?.action)
        }

        // Update it a second time.
        var updatedFeature2 = updatedFeature1.copy<NakshaFeature>(true)
        updatedFeature2.properties[ALIAS] = "second_update"
        executeWrite(WriteRequest().apply {
            add(Write().updateFeature(collection, updatedFeature2, true))
        }).apply {
            assertEquals(1, getFeatures(NakshaFeatureList.TYPE).size)
            updatedFeature2 = assertNotNull(getFeatures(NakshaFeatureList.TYPE).first())
            assertEquals(featureId, updatedFeature2.id)
            assertEquals(Action.UPDATE, updatedFeature2.properties.xyz.guid?.tupleNumber?.action)
        }

        // Delete it.
        var deletedFeature: NakshaFeature
        executeWrite(WriteRequest().apply {
            add(Write().deleteFeatureById(collection, createdFeature.id))
        }).apply {
            assertEquals(1, getFeatures(NakshaFeatureList.TYPE).size)
            deletedFeature = assertNotNull(getFeatures(NakshaFeatureList.TYPE).first())
            assertEquals(featureId, deletedFeature.id)
            assertEquals(Action.DELETE, deletedFeature.properties.xyz.guid?.tupleNumber?.action)
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
            assertEquals(4, getFeatures(NakshaFeatureList.TYPE).size)

            val delete = assertNotNull(getFeatures(NakshaFeatureList.TYPE)[0])
            val update2 = assertNotNull(getFeatures(NakshaFeatureList.TYPE)[1])
            val update1 = assertNotNull(getFeatures(NakshaFeatureList.TYPE)[2])
            val create = assertNotNull(getFeatures(NakshaFeatureList.TYPE)[3])

            assertEquals(featureId, delete.id)
            assertEquals(Action.DELETE, delete.properties.xyz.action)
            assertEquals(Action.DELETE, delete.properties.xyz.guid?.tupleNumber?.action)
            assertEquals(delete.properties.xyz.pguid, update2.properties.xyz.guid)
            assertEquals(delete.properties.xyz.nguid, delete.properties.xyz.guid)

            assertEquals(featureId, update2.id)
            assertEquals(Action.UPDATE, update2.properties.xyz.action)
            assertEquals(Action.UPDATE, update2.properties.xyz.guid?.tupleNumber?.action)
            assertEquals("second_update", update2.properties[ALIAS])
            assertEquals(update2.properties.xyz.pguid, update1.properties.xyz.guid)
            assertEquals(update2.properties.xyz.nguid, delete.properties.xyz.guid)

            assertEquals(featureId, update1.id)
            assertEquals(Action.UPDATE, update1.properties.xyz.action)
            assertEquals(Action.UPDATE, update1.properties.xyz.guid?.tupleNumber?.action)
            assertEquals("first_update", update1.properties[ALIAS])
            assertEquals(update1.properties.xyz.pguid, create.properties.xyz.guid)
            assertEquals(update1.properties.xyz.nguid, update2.properties.xyz.guid)

            assertEquals(featureId, create.id)
            assertEquals(Action.CREATE, create.properties.xyz.action)
            assertEquals(Action.CREATE, create.properties.xyz.guid?.tupleNumber?.action)
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
            assertEquals(2, getFeatures(NakshaFeatureList.TYPE).size)

            val delete = assertNotNull(getFeatures(NakshaFeatureList.TYPE)[0])
            val update2 = assertNotNull(getFeatures(NakshaFeatureList.TYPE)[1])

            assertEquals(featureId, delete.id)
            assertEquals(Action.DELETE, delete.properties.xyz.action)
            assertEquals(delete.properties.xyz.pguid, update2.properties.xyz.guid)
            assertEquals(delete.properties.xyz.nguid, delete.properties.xyz.guid)

            assertEquals(Action.UPDATE, update2.properties.xyz.action)
        }

        executeRead(ReadFeatures().apply {
            mapId = collection.mapId
            collectionIds.add(collection.id)
            featureIds.add(featureId)
            queryHistory = true
            version = updatedFeature2.guid!!.tupleNumber.version
            versions = 2
        }).apply {
            // We expect to have 4 versions, but only want the middle 2 back
            // As specified, we expect descending order: [deleted, ] updated2, updated1 [, created]
            assertEquals(2, getFeatures(NakshaObjectList.TYPE).size)

            val update2 = assertNotNull(getFeatures(NakshaObjectList.TYPE)[0])
            val update1 = assertNotNull(getFeatures(NakshaObjectList.TYPE)[1])

            assertEquals(featureId, update1.id)
            assertEquals(Action.UPDATE, update1.properties.xyz.action)

            assertEquals(featureId, update2.id)
            assertEquals(Action.UPDATE, update2.properties.xyz.action)

            assertEquals(update2.guid, update1.properties.xyz.nguid)
            assertEquals(update1.guid, update2.properties.xyz.pguid)
        }
    }
}
