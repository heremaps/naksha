package naksha.psql

import naksha.model.GuidList
import naksha.model.RandomFeatures.RandomFeatures_C.randomFeature
import naksha.model.objects.NakshaCollection
import naksha.model.request.ReadFeatures
import naksha.psql.assertions.NakshaFeatureFluidAssertions.Companion.assertThatFeature
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ReadFeaturesByGuuidTest :
    PgTestBase(collection = NakshaCollection("read_features_by_guuid_test")) {

    @Test
    fun shouldReadFeaturesByGuuid() {
        // Given:
        val inputFeature1 = randomFeature("f1")
        val inputFeature2 = randomFeature("f2")
        val inputFeature3 = randomFeature("f3")

        // And
        val createResp = insertFeatures(listOf(inputFeature1, inputFeature2, inputFeature3))
        val guuidById = createResp.features.filterNotNull().associate { it.id to it.guid }

        // When
        val readByGuid = ReadFeatures().apply {
            mapId = collection.catalogId
            collectionIds += collection.id
            guids = GuidList().apply {
                add(guuidById[inputFeature1.id])
                add(guuidById[inputFeature3.id])
            }
        }
        val readResp = executeRead(readByGuid)

        // Then
        val fetchedFeatures = readResp.features
        assertEquals(2, fetchedFeatures.size)
        val fetched1 = assertNotNull(fetchedFeatures.find { it!!.id == inputFeature1.id })
        val fetched3 = assertNotNull(fetchedFeatures.find { it!!.id == inputFeature3.id })
        assertThatFeature(fetched1).isIdenticalTo(inputFeature1)
        assertThatFeature(fetched3).isIdenticalTo(inputFeature3)
    }
}