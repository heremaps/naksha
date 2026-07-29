package naksha.psql

import naksha.base.Int64
import naksha.model.RandomFeatures.RandomFeatures_C.randomFeatures
import naksha.model.objects.NakshaCollection
import naksha.model.objects.StandardMembers
import naksha.model.request.ReadFeatures
import naksha.model.request.Write
import naksha.model.request.WriteRequest
import naksha.model.request.ops.And
import naksha.model.request.ops.Equals
import naksha.model.request.ops.IsAnyOf
import naksha.psql.assertions.NakshaFeatureFluidAssertions.Companion.assertThatFeature
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ReadFeaturesByOtherTns : PgTestBase(
    NakshaCollection("read_features_by_tns_test")
) {

    @Test
    fun shouldGetFeaturesByNextTns() {
        // Given: some freshly created features
        val initialFeatures = insertFeatures(randomFeatures(5).apply {
            forEachIndexed { ind, feature -> feature.title = "f_$ind" }
        }).features

        // And: updates to these features
        val update = WriteRequest()
        initialFeatures.forEachIndexed { ind, feature ->
            update.add(
                Write().updateFeature(
                    collection,
                    feature!!.apply { title = "updated_f_$ind" },
                    atomic = true
                )
            )
        }
        val updateResp = executeWrite(update)

        // And: the shared `next_version` of all updated features (all 5 updates ran in one transaction).
        val updatedVersion: Int64 = updateResp.features[0]!!.properties.xyz.guid!!.tupleNumber.version

        // When: querying for features whose `next_version` matches that version
        val byNextTnResp = executeRead(ReadFeatures().apply {
            catalogId = collection.catalogId
            collectionId = collection.id
            queryMembers = IsAnyOf(StandardMembers.NextVersion, updatedVersion)
            queryHistory = true
        })

        // Then: all 5 initial features are returned — they share next_version since they were demoted together.
        val fetchedFeatures = byNextTnResp.features
        assertEquals(5, fetchedFeatures.size)
        val expectedIds = initialFeatures.map { it!!.id }.toSet()
        fetchedFeatures.forEach { fetchedPredecessor ->
            assertNotNull(fetchedPredecessor)
            assertTrue(fetchedPredecessor.id in expectedIds)
            val initialState =
                assertNotNull(initialFeatures.find { it!!.id == fetchedPredecessor.id })
            assertThatFeature(fetchedPredecessor).isIdenticalTo(initialState)
        }
    }

    @Test
    fun shouldGetOnePredecessorByFeatureNumberAndNextVersion() {
        val initialFeatures = insertFeatures(randomFeatures(5)).features.filterNotNull()
        val update = WriteRequest()
        initialFeatures.forEach { feature ->
            update.add(Write().updateFeature(collection, feature.apply { title = "updated_$id" }, atomic = true))
        }
        val updateResponse = executeWrite(update)
        val target = initialFeatures.first()
        val targetSuccessorTn = updateResponse.features.first()!!.properties.xyz.guid!!.tupleNumber
        val successorVersion = targetSuccessorTn.version
        val targetFeatureNumber = targetSuccessorTn.featureNumber

        val response = executeRead(ReadFeatures().apply {
            catalogId = collection.catalogId
            collectionId = collection.id
            queryMembers = And(
                Equals(StandardMembers.NextVersion.name, successorVersion),
                Equals(StandardMembers.FeatureNumber.name, targetFeatureNumber),
            )
            queryHistory = true
        })

        assertEquals(1, response.features.size)
        assertEquals(target.id, response.features.first()!!.id)
    }
}
