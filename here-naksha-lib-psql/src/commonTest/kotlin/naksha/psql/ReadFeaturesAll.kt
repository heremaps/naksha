package naksha.psql

import naksha.base.Action
import naksha.model.objects.NakshaFeature
import naksha.model.request.*
import naksha.model.RandomFeatures.RandomFeatures_C.randomFeatures
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
                add(Write().createFeature(collection.catalogId, collection.id, featureToCreate))
            }
        }
        val writeFeaturesResp = executeWrite(writeFeaturesReq)
        assertEquals(COUNT, writeFeaturesResp.features.size)
        for (feature in writeFeaturesResp.features) {
            assertNotNull(feature)
            assertNull(allFeatures[feature.id])
            assertEquals(Action.CREATE, feature.properties.xyz.action)
            allFeatures[feature.id] = feature
        }
    }

    @Test
    fun shouldReturnAllFeatures() {
        executeRead(ReadFeatures().apply {
            catalogId = collection.catalogId
            collectionId = collection.id
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
