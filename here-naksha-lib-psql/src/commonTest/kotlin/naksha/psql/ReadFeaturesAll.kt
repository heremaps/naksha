package naksha.psql

import naksha.model.Action
import naksha.model.objects.NakshaFeature
import naksha.model.request.*
import naksha.model.RandomFeatures.RandomFeatures_C.randomFeatures
import naksha.model.objects.NakshaFeatureList
import kotlin.test.*

class ReadFeaturesAll : PgTestBase() {

    companion object ReadFeaturesAll_C {
        private const val COUNT = 100
        private val allFeatures: MutableMap<String, NakshaFeature> = mutableMapOf()
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
    fun shouldReturnAllFeatures() {
        executeRead(ReadFeatures().apply {
            mapId = collection.mapId
            collectionIds += collection.id
        }).apply {
            assertEquals(COUNT, getFeatures(NakshaFeatureList.TYPE).size)
            for (feature in getFeatures(NakshaFeatureList.TYPE)) {
                assertNotNull(feature)
                assertNotNull(allFeatures[feature.id])
                allFeatures.remove(feature.id)
            }
            assertEquals(0, allFeatures.size)
        }
    }

}
