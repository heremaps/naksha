package naksha.psql

import naksha.model.objects.NakshaCollection
import naksha.model.request.ExecutedOp
import naksha.model.request.Write
import naksha.model.request.WriteRequest
import naksha.psql.base.PgTestBase
import naksha.psql.util.ProxyFeatureGenerator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ExecutedOpTest : PgTestBase(NakshaCollection("executed_op_test_c")) {

    @Test
    fun shouldReturnCorrectOp() {
        // Given: features to create
        val initialFeaturesToCreate = ProxyFeatureGenerator.generateRandomFeatures(count = 5)
        val writeFeaturesReq = WriteRequest().apply {
            initialFeaturesToCreate.forEach { featureToCreate ->
                add(Write().createFeature(null, collection!!.id, featureToCreate))
            }
        }

        // When: executing feature write request
        val initiallyCreatedTuples = executeWrite(writeFeaturesReq).tuples

        // Then
        assertEquals(5, initiallyCreatedTuples.size)
        val expectedIds = initialFeaturesToCreate.map { it.id }
        initiallyCreatedTuples.forEach { resultTuple ->
            assertNotNull(resultTuple)
            assertTrue(resultTuple.id() in expectedIds)
            assertEquals(ExecutedOp.CREATED, resultTuple.op)
        }

        // When
        val featuresToUpdate = initialFeaturesToCreate.subList(0, 2)
        featuresToUpdate.forEach { it.type = "updated_type" }
        val featureIdsToDelete = initialFeaturesToCreate
            .subList(2, 4)
            .map { it.id }
        val newFeature = ProxyFeatureGenerator.generateRandomFeature()
        val composedWriteReq = WriteRequest().apply {
            add(Write().updateFeature(null, collection!!.id, featuresToUpdate[0]))
            add(Write().updateFeature(null, collection.id, featuresToUpdate[1]))
            add(Write().deleteFeatureById(null, collection.id, featureIdsToDelete[0]))
            add(Write().deleteFeatureById(null, collection.id, featureIdsToDelete[0]))
            add(Write().createFeature(null, collection.id, newFeature))
        }

        // And
        val composedRespTuples = executeWrite(composedWriteReq).tuples

        // Then
        assertEquals(5, composedRespTuples.size)
        composedRespTuples[0].let { firstUpdated ->
            assertNotNull(firstUpdated)
            assertEquals(featuresToUpdate[0].id, firstUpdated.id())
            assertEquals(ExecutedOp.UPDATED, firstUpdated.op)
        }
        composedRespTuples[1].let { secondUpdated ->
            assertNotNull(secondUpdated)
            assertEquals(featuresToUpdate[1].id, secondUpdated.id())
            assertEquals(ExecutedOp.UPDATED, secondUpdated.op)
        }
        composedRespTuples[2].let { firstDeleted ->
            assertNotNull(firstDeleted)
            assertEquals(featureIdsToDelete[0], firstDeleted.id())
            assertEquals(ExecutedOp.DELETED, firstDeleted.op)
        }
        composedRespTuples[3].let { secondDeleted ->
            assertNotNull(secondDeleted)
            assertEquals(featureIdsToDelete[1], secondDeleted.id())
            assertEquals(ExecutedOp.DELETED, secondDeleted.op)
        }
        composedRespTuples[4].let { newlyCreated ->
            assertNotNull(newlyCreated)
            assertEquals(newFeature.id, newlyCreated.id())
            assertEquals(ExecutedOp.CREATED, newlyCreated.op)
        }
    }
}