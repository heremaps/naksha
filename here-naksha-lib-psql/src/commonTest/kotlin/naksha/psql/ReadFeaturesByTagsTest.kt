package naksha.psql

import naksha.model.TagList
import naksha.model.objects.NakshaCollection
import naksha.model.objects.NakshaFeature
import naksha.model.request.ReadFeatures
import naksha.model.request.SuccessResponse
import naksha.model.request.query.TagExists
import naksha.model.request.query.TagQuery
import naksha.model.request.query.TagValueIsString
import naksha.psql.base.PgTestBase
import naksha.psql.util.ProxyFeatureGenerator
import kotlin.test.Test
import kotlin.test.assertEquals

class ReadFeaturesByTagsTest : PgTestBase(NakshaCollection("read_by_tags_test")) {

    @Test
    fun shouldReturnFeaturesWithExistingTag() {
        // Given:
        val inputFeature = randomFeatureWithTags("foo=bar")

        // When:
        insertFeature(feature = inputFeature)

        // And:
        val featuresWithFooTag = executeTagsQuery(
            TagExists("foo")
        ).features

        // Then:
        assertEquals(1, featuresWithFooTag.size)
        assertEquals(inputFeature.id, featuresWithFooTag[0]!!.id)
    }

    @Test
    fun shouldNotReturnFeaturesWithMissingTag() {
        // Given:
        val inputFeature = randomFeatureWithTags("foo")

        // When:
        insertFeature(feature = inputFeature)

        // And:
        val featuresWithFooTag = executeTagsQuery(
            TagExists("bar")
        ).features

        // Then:
        assertEquals(0, featuresWithFooTag.size)
    }

    @Test
    fun shouldReturnFeaturesWithTagValue() {
        // Given:
        val inputFeature = randomFeatureWithTags("foo=bar")

        // When:
        insertFeature(feature = inputFeature)

        // And:
        val featuresWithFooTag = executeTagsQuery(
            TagValueIsString(name = "foo", value = "bar")
        ).features

        // Then:
        assertEquals(1, featuresWithFooTag.size)
        assertEquals(inputFeature.id, featuresWithFooTag[0]!!.id)
    }

    private fun randomFeatureWithTags(vararg tags: String): NakshaFeature {
        return ProxyFeatureGenerator.generateRandomFeature().apply {
            properties.xyz.tags = TagList(*tags)
        }
    }

    private fun executeTagsQuery(tagQuery: TagQuery): SuccessResponse {
        return executeRead(ReadFeatures().apply {
            collectionIds += collection!!.id
            query.tags = tagQuery
        })
    }
}