package naksha.psql

import naksha.model.*
import naksha.model.objects.NakshaCollection
import naksha.model.objects.NakshaFeature
import naksha.model.request.ReadFeatures
import naksha.model.request.Write
import naksha.model.request.WriteRequest
import naksha.psql.base.PgTestBase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame

class DeleteFeatureTest : PgTestBase(NakshaCollection("delete_feature_test_c")) {

    @Test
    fun shouldPerformDelete() {
        // Given: Initial state of feature
        val featureId = "feature_to_delete"
        val initialFeature = NakshaFeature().apply {
            id = featureId
        }
        val writeInitialFeature = WriteRequest().add(
            Write().createFeature(collection.mapId!!, collection.id, initialFeature)
        )
        val deleteFeaturesReq = WriteRequest().add(
            Write().deleteFeatureById(collection.mapId!!, collection.id, featureId)
        )

        // When: Writing initial version of feature
        executeWrite(writeInitialFeature)

        // And: Deleting feature
        executeWrite(deleteFeaturesReq)

        // And: Retrieving feature by id
        val response = executeRead(ReadFeatures().apply {
            collectionIds += collection.id
            featureIds += initialFeature.id
        })
        val responseFeatures = response.features
        assertEquals(0, responseFeatures.size)

        // verify if history contains 2 versions
        val historyResponse = executeRead(ReadFeatures().apply {
            collectionIds += collection.id
            featureIds += initialFeature.id
            queryHistory = true
        })
        assertEquals(2, historyResponse.features.size)
        assertSame(Action.CREATED, historyResponse.tupleList?.get(0)?.meta?.flags?.actionEnum())
        assertSame(Action.DELETED, historyResponse.tupleList?.get(1)?.meta?.flags?.actionEnum())

        // verify if delete table contains element
        val deleteTableResponse = executeRead(ReadFeatures().apply {
            collectionIds += collection.id
            featureIds += initialFeature.id
            queryDeleted = true
        })
        assertEquals(1, deleteTableResponse.features.size)
    }
}