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
                add(Write().createFeature(collection, featureToCreate))
            }
        }
        val writeFeaturesResp = executeWriteAndLoadTuples(writeFeaturesReq)
        assertEquals(COUNT, writeFeaturesResp.asFeatures.size)
        for (feature in writeFeaturesResp.asFeatures) {
            assertNotNull(feature)
            assertNull(allFeatures[feature.id.text])
            assertEquals(Action.CREATE, feature.properties.xyz.action)
            allFeatures[feature.id.text] = feature
        }
    }

    @Test
    fun shouldReturnAllFeatures() {
        executeReadAndLoadTuple(ReadFeatures().apply {
            catalogId = collection.catalogId
            collectionId = collection.id
        }).apply {
            assertEquals(COUNT, asFeatures.size)
            for (feature in asFeatures) {
                assertNotNull(feature)
                assertNotNull(allFeatures[feature.id.text])
                allFeatures.remove(feature.id.text)
            }
            assertEquals(0, allFeatures.size)
        }
    }
}