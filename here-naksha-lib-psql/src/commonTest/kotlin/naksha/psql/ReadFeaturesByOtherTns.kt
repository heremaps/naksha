package naksha.psql

import naksha.model.RandomFeatures.RandomFeatures_C.randomFeatures
import naksha.model.TupleNumberVariant
import naksha.model.objects.NakshaCollection
import naksha.model.objects.NakshaObjectList
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
        val initialFeatures = insertFeatureList(randomFeatures(5, NakshaObjectList.TYPE).apply {
            forEachIndexed { ind, feature -> feature!!.title = "f_$ind" }
        }).getFeatures(NakshaObjectList.TYPE)

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

        // And: tuple numbers of updated version
        val selectedUpdatedFeatures = updateResp.getFeatures(NakshaObjectList.TYPE).subList(2, 4) // take 2 features from the middle
        val selectedTns = selectedUpdatedFeatures.map { it!!.tupleNumber }
        val serializedTns: Array<ByteArray> = selectedTns
            .map { it.toByteArray(TupleNumberVariant.B96) } // `next_tn` is 96-bit encoded
            .toTypedArray()

        // When: querying for features which `nextTn` is specified
        val nextTnQuery = MetaQuery(
            MetaColumn.nextVersion(),
            AnyOp.IS_ANY_OF,
            serializedTns
        )
        val byNextTnResp = executeRead(ReadFeatures().apply {
            mapId = collection.mapId
            collectionIds += collection.id
            query.metadata = nextTnQuery
            queryHistory = true
        })

        // Then: we fetched initial features based on `next_tn` pointing to updated features
        val fetchedFeatures = byNextTnResp.getFeatures(NakshaObjectList.TYPE)
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