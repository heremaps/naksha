package naksha.psql

import naksha.model.Action
import naksha.model.objects.NakshaCollection
import naksha.model.objects.NakshaFeature
import naksha.model.request.*
import naksha.psql.base.PgTestBase
import naksha.model.RandomFeatures.RandomFeatures_C.randomFeatures
import naksha.psql.PgTest.PgTest_C.TEST_MAP_ID
import kotlin.test.*

class ReadFeaturesAll : PgTestBase(NakshaCollection("read_features_all", TEST_MAP_ID)) {

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
        assertEquals(COUNT, writeFeaturesResp.features.size)
        for (feature in writeFeaturesResp.features) {
            assertNotNull(feature)
            assertNull(allFeatures[feature.id])
            assertEquals(Action.CREATED, feature.properties.xyz.action)
            allFeatures[feature.id] = feature
        }
    }

    @Test
    fun shouldReturnAllFeatures() {
        executeRead(ReadFeatures().apply {
            mapId = collection.mapId
            collectionIds += collection.id
        }).apply {
            assertEquals(COUNT, features.size)
            for (feature in features) {
                assertNotNull(feature)
                assertNotNull(allFeatures[feature.id])
                allFeatures.remove(feature.id)
            }
            assertEquals(0, allFeatures.size)
        }
    }

}
