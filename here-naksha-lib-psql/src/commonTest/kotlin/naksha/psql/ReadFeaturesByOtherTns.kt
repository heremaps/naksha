package naksha.psql

import naksha.base.Int64
import naksha.model.RandomFeatures.RandomFeatures_C.randomFeatures
import naksha.model.objects.NakshaCollection
import naksha.model.request.ReadFeatures
import naksha.model.request.Write
import naksha.model.request.WriteRequest
import naksha.model.request.query.AnyOp
import naksha.model.request.query.MetaColumn
import naksha.model.request.query.MetaQuery
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

        // And: bigint versions of updated tuples (next_version is now an int8 column, not a B128 byte-array)
        val selectedUpdatedFeatures = updateResp.features.subList(2, 4) // take 2 features from the middle
        val selectedNextVersions: Array<Int64> = selectedUpdatedFeatures
            .map { it!!.tupleNumber.version.txn }
            .toTypedArray()

        // When: querying for features whose `next_version` matches any of the selected versions
        val nextVersionQuery = MetaQuery(
            MetaColumn.nextVersion(),
            AnyOp.IS_ANY_OF,
            selectedNextVersions
        )
        val byNextTnResp = executeRead(ReadFeatures().apply {
            mapId = collection.mapId
            collectionIds += collection.id
            query.metadata = nextVersionQuery
            queryHistory = true
        })

        // Then: we fetched initial features based on `next_version` pointing to updated features
        val fetchedFeatures = byNextTnResp.features
        assertEquals(2, fetchedFeatures.size)
        val expectedIds = selectedUpdatedFeatures.map { it!!.id }.toSet()
        fetchedFeatures.forEach { fetchedPredecessor ->
            assertNotNull(fetchedPredecessor)
            assertTrue(fetchedPredecessor.id in expectedIds)
            val initialState =
                assertNotNull(initialFeatures.find { it!!.id == fetchedPredecessor.id })
            assertThatFeature(fetchedPredecessor).isIdenticalTo(initialState)
        }
    }
}