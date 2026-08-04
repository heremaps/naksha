package naksha.psql

import naksha.base.Action
import naksha.base.Id
import naksha.model.objects.NakshaFeature
import naksha.model.objects.StandardMembers
import naksha.model.request.OrderBy
import naksha.model.request.ReadFeatures
import naksha.model.request.Write
import naksha.model.request.WriteRequest
import naksha.model.request.query.SortOrder
import naksha.psql.PgTest.PgTest_C.TEST_MAP_ID
import naksha.model.RandomFeatures.RandomFeatures_C.randomFeatures
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
            allFeaturesOrderedByIdAsc.add(feature)
            allFeaturesOrderedByIdDesc.add(feature)
        }
        allFeaturesOrderedByIdAsc.sortWith { f1, f2 -> f1.id.compareTo(f2.id) }
        allFeaturesOrderedByIdDesc.sortWith { f1, f2 -> f1.id.compareTo(f2.id)*-1 }
    }

    @Test
    fun searchOrderedById() {
        executeReadAndLoadTuple(ReadFeatures().apply {
            catalogId = Id(TEST_MAP_ID)
            collectionId = collection.id
            orderBy = OrderBy.id()
            limit = ORDER_BY_ID_LIMIT
        }).apply {
            assertEquals(ORDER_BY_ID_LIMIT, asFeatures.size)
            for (entry in asFeatures.withIndex()) {
                val expected = allFeaturesOrderedByIdDesc[entry.index]
                val feature = assertNotNull(entry.value)
                assertEquals(expected.id, feature.id)
            }
        }

        executeReadAndLoadTuple(ReadFeatures().apply {
            catalogId = Id(TEST_MAP_ID)
            collectionId = collection.id
            orderBy = OrderBy(StandardMembers.IdMember, order = SortOrder.ASCENDING)
            limit = ORDER_BY_ID_LIMIT
        }).apply {
            assertEquals(ORDER_BY_ID_LIMIT, asFeatures.size)
            for (entry in asFeatures.withIndex()) {
                val expected = allFeaturesOrderedByIdAsc[entry.index]
                val feature = assertNotNull(entry.value)
                assertEquals(expected.id, feature.id)
            }
        }
    }
}