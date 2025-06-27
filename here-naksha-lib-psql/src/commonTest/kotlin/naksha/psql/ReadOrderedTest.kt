package naksha.psql

import naksha.model.Action
import naksha.model.objects.NakshaFeature
import naksha.model.request.OrderBy
import naksha.model.request.ReadFeatures
import naksha.model.request.Write
import naksha.model.request.WriteRequest
import naksha.model.request.query.MetaColumn
import naksha.model.request.query.SortOrder
import naksha.psql.PgTest.PgTest_C.TEST_MAP_ID
import naksha.model.RandomFeatures.RandomFeatures_C.randomFeatures
import naksha.model.objects.NakshaFeatureList
import kotlin.test.*

class ReadOrderedTest : PgTestBase() {

    companion object ReadOrderedTest_C {
        private const val COUNT = 100
        private const val ORDER_BY_ID_LIMIT = 50
        private val allFeatures: MutableMap<String, NakshaFeature> = mutableMapOf()
        private val allFeaturesOrderedByIdDesc: MutableList<NakshaFeature> = mutableListOf()
        private val allFeaturesOrderedByIdAsc: MutableList<NakshaFeature> = mutableListOf()
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
            allFeaturesOrderedByIdAsc.add(feature)
            allFeaturesOrderedByIdDesc.add(feature)
        }
        allFeaturesOrderedByIdAsc.sortWith { f1, f2 -> f1.id.compareTo(f2.id) }
        allFeaturesOrderedByIdDesc.sortWith { f1, f2 -> f1.id.compareTo(f2.id)*-1 }
    }

    @Test
    fun searchOrderedById() {
        executeRead(ReadFeatures().apply {
            mapId = TEST_MAP_ID
            collectionIds += collection.id
            orderBy = OrderBy.id()
            limit = ORDER_BY_ID_LIMIT
        }).apply {
            val features = getFeatures(NakshaFeatureList.TYPE)
            assertEquals(ORDER_BY_ID_LIMIT, features.size)
            for (entry in features.withIndex()) {
                val expected = allFeaturesOrderedByIdDesc[entry.index]
                val feature = assertNotNull(entry.value)
                assertEquals(expected.id, feature.id)
            }
        }

        executeRead(ReadFeatures().apply {
            mapId = TEST_MAP_ID
            collectionIds += collection.id
            orderBy = OrderBy(MetaColumn.id(), order = SortOrder.ASCENDING)
            limit = ORDER_BY_ID_LIMIT
        }).apply {
            val features = getFeatures(NakshaFeatureList.TYPE)
            assertEquals(ORDER_BY_ID_LIMIT, features.size)
            for (entry in features.withIndex()) {
                val expected = allFeaturesOrderedByIdAsc[entry.index]
                val feature = assertNotNull(entry.value)
                assertEquals(expected.id, feature.id)
            }
        }
    }
}