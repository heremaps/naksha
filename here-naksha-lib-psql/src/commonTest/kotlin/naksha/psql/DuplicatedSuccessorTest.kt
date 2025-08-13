package naksha.psql

import naksha.model.RandomFeatures.RandomFeatures_C.randomFeature
import naksha.model.objects.NakshaCollection
import naksha.model.objects.NakshaFeatureList
import naksha.model.objects.NakshaObjectList
import naksha.model.request.ErrorResponse
import naksha.model.request.Write
import naksha.model.request.WriteRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class DuplicatedSuccessorTest : PgTestBase(NakshaCollection("duplicate_successor_test_col")) {

    @Test
    fun shouldNotAllowMultipleFeaturesToSucceedOneVersion() {
        // Given: created feature in DB
        val initialFeature = insertFeatures(randomFeature()).getFeatures(NakshaObjectList.TYPE).first()!!

        // And: multiple features that want to succeed it
        val firstSuccessor = initialFeature.apply { title = "first successor" }
        val secondSuccessor = initialFeature.apply { title = "second successor" }
        val thirdSuccessor = initialFeature.apply { title = "third successor" }

        // When:
        val updateAllAtomically = WriteRequest()
            .add(Write().updateFeature(collection, firstSuccessor, atomic = true))
            .add(Write().updateFeature(collection, secondSuccessor, atomic = true))
            .add(Write().updateFeature(collection, thirdSuccessor, atomic = true))
        val updateResp = storage.newWriteSession(newSessionOptions()).use {
            it.execute(updateAllAtomically)
        }

        // Then:
        assertIs<ErrorResponse>(updateResp)
    }

    @Test
    fun shouldNotAllowSuccessorsWithDifferentFeatureId() {
        // Given: created feature in DB
        val initialFeatureA = insertFeatures(randomFeature("f_a")).getFeatures(NakshaObjectList.TYPE).first()!!
        val initialFeatureB = insertFeatures(randomFeature("f_b")).getFeatures(NakshaObjectList.TYPE).first()!!

        // And: correct successor of A
        val firstSuccessor = initialFeatureA.apply { title = "successor of A" }
        assertEquals(firstSuccessor.properties.xyz.uuid, initialFeatureA.properties.xyz.uuid)

        // And: incorrect successor of B
        val secondSuccessor = initialFeatureA.apply {
            id = initialFeatureB.id // invalid ID - base is A (including UUID), id is B
            title = "second successor"
        }
        assertEquals(secondSuccessor.properties.xyz.uuid, initialFeatureA.properties.xyz.uuid)

        // When:
        val updateAllAtomically = WriteRequest()
            .add(Write().updateFeature(collection, firstSuccessor, atomic = true))
            .add(Write().updateFeature(collection, secondSuccessor, atomic = true))
        val updateResp = storage.newWriteSession(newSessionOptions()).use {
            it.execute(updateAllAtomically)
        }

        // Then:
        assertIs<ErrorResponse>(updateResp)
    }
}