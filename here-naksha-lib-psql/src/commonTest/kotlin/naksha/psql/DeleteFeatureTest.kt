package naksha.psql

import naksha.model.objects.NakshaCollection
import naksha.model.objects.NakshaFeature
import naksha.model.request.ReadFeatures
import naksha.model.request.Write
import naksha.model.request.WriteRequest
import naksha.psql.base.PgTestBase
import kotlin.test.Test
import kotlin.test.assertEquals

class DeleteFeatureTest : PgTestBase(NakshaCollection("delete_feature_test_c")) {

    @Test
    fun shouldPerformDelete() {
        // Given: Initial state of feature
        val featureId = "feature_to_delete"
        val initialFeature = NakshaFeature().apply {
            id = featureId
        }
        val writeInitialFeature = WriteRequest().add(
            Write().createFeature(null, collection!!.id, initialFeature)
        )
        val deleteFeaturesReq = WriteRequest().add(
            Write().deleteFeatureById(null, collection.id, featureId)
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
    }
}