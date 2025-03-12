package naksha.psql

import naksha.model.Action
import naksha.model.objects.NakshaCollection
import naksha.model.objects.NakshaFeature
import naksha.model.request.OrderBy
import naksha.model.request.ReadFeatures
import naksha.model.request.Write
import naksha.model.request.WriteRequest
import naksha.model.request.query.MetaColumn
import naksha.model.request.query.SortOrder
import naksha.psql.PgTest.PgTest_C.TEST_MAP_ID
import naksha.psql.base.PgTestBase
import naksha.psql.util.ProxyFeatureGenerator.generateRandomFeatures
import kotlin.test.*

class ReadOrderedTest : PgTestBase(NakshaCollection("read_ordered_test")) {

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
        val featuresToCreate = generateRandomFeatures(COUNT)
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
            assertEquals(ORDER_BY_ID_LIMIT, features.size)
            for (entry in features.withIndex()) {
                val expected = allFeaturesOrderedByIdAsc[entry.index]
                val feature = assertNotNull(entry.value)
                assertEquals(expected.id, feature.id)
            }
        }
    }
}