package naksha.psql

import naksha.model.objects.NakshaCollection
import naksha.model.request.ReadFeatures
import naksha.model.request.Write
import naksha.model.request.WriteRequest
import naksha.psql.base.PgTestBase
import naksha.psql.util.ProxyFeatureGenerator.generateRandomFeature
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReadHistoryTest: PgTestBase(NakshaCollection("read_history_test_c")) {

    @Test
    fun shouldPerformDelete() {
        // Given: Initial state of feature
        val feature = generateRandomFeature()

        // And: Writing initial version of feature
        executeWrite(WriteRequest().add(
            Write().createFeature(null, collection!!.id, feature)
        ))

        // And: Deleting feature
        executeWrite(WriteRequest().add(
            Write().deleteFeatureById(null, collection.id, feature.id)
        ))

        // When: Retrieving only head features
        val headTuples = executeRead(ReadFeatures().apply {
            collectionIds += collection.id
            featureIds += feature.id
            queryHistory = false
        }).tuples

        // Then: there are no head features
        assertEquals(0, headTuples.size)

        // When: Retrieving head and history features
        val headAndHistoryTuples = executeRead(ReadFeatures().apply {
            collectionIds += collection.id
            featureIds += feature.id
            queryHistory = true
        }).tuples

        // Then
        assertEquals(2, headAndHistoryTuples.size)
        val idSet = headAndHistoryTuples.map { it!!.id() }.toSet()
        assertEquals(1, idSet.size)
        assertTrue(feature.id in idSet)

        // And
        val metaList = headAndHistoryTuples.map { it!!.tuple!!.meta }
        val createdMeta = metaList.find { it!!.prevVersion == null }!!
        val deletedMeta = metaList.find { it!!.nextVersion == null }!!
        assertEquals(createdMeta.version, deletedMeta.prevVersion)
        assertEquals(deletedMeta.version, createdMeta.nextVersion)
    }
}