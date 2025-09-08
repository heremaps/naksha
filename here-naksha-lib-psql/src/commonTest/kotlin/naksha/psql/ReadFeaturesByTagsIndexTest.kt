package naksha.psql

import naksha.model.objects.NakshaCollection
import naksha.model.objects.NakshaFeature
import naksha.model.request.ReadFeatures
import naksha.model.request.query.TagExists
import naksha.model.request.query.TagOr
import kotlin.test.Test
import kotlin.test.assertEquals

class ReadFeaturesByTagsIndexTest : PgTestBase(
    NakshaCollection("read_by_tags_index_test")
) {

    @Test
    fun shouldHitIndex() {
        // Given
        val tagPrefix = "ref_id_urn:here::here:Topology:"
        val tags = (1..100).map { tagPrefix + it }

        // And
        val featuresToInsert = (1..10).map { ind ->
            NakshaFeature().apply {
                properties.xyz.addAndNormalizeTags(tags[ind])
            }
        }
        insertFeatures(featuresToInsert)

        // And:
        val tagSubqueries = tags.take(100).map { TagExists(it) } // ..100 will fail!
        val tagQuery = TagOr().apply {
            addAll(tagSubqueries)
        }
        val readByTags = ReadFeatures().apply {
            mapId = collection.mapId
            collectionIds += collection.id
            query.tags = tagQuery
        }

        // When
        val fetchedFeatures = executeRead(readByTags)

        // Then
        assertEquals(10, fetchedFeatures.length)
    }
}