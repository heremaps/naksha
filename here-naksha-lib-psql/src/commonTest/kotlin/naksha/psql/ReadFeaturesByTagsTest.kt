package naksha.psql

import naksha.model.TagList
import naksha.model.objects.NakshaCollection
import naksha.model.objects.NakshaFeature
import naksha.model.request.ReadFeatures
import naksha.model.request.SuccessResponse
import naksha.model.request.query.*
import naksha.model.RandomFeatures
import naksha.psql.PgTest.PgTest_C.TEST_MAP_ID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReadFeaturesByTagsTest : PgTestBase() {

    @Test
    fun shouldReturnFeaturesWithExistingTag() {
        // Given:
        val inputFeature = randomFeatureWithTags("sample")

        // When:
        insertFeature(feature = inputFeature)

        // And:
        val featuresWithFooTag = executeTagsQuery(
            TagExists("sample")
        ).features

        // Then:
        assertEquals(1, featuresWithFooTag.size)
        assertEquals(inputFeature.id, featuresWithFooTag[0]!!.id)
    }

    @Test
    fun shouldNotReturnFeaturesWithMissingTag() {
        // Given:
        val inputFeature = randomFeatureWithTags("existing")

        // When:
        insertFeature(feature = inputFeature)

        // And:
        val featuresWithFooTag = executeTagsQuery(
            TagExists("non-existing")
        ).features

        // Then:
        assertEquals(0, featuresWithFooTag.size)
    }

    /**
     * The default tags member is a [naksha.model.objects.MemberType.SET]: the tags array is stored
     * unmodified, the values are never split into key/value pairs. Therefore only full elements can
     * be matched — `TagExists("fullelem")` does not find a feature tagged `["fullelem=bar"]`.
     */
    @Test
    fun shouldMatchOnlyFullElements() {
        // Given:
        val inputFeature = randomFeatureWithTags("fullelem=bar")

        // When:
        insertFeature(feature = inputFeature)

        // Then: the full element matches.
        val byFullElement = executeTagsQuery(TagExists("fullelem=bar")).features
        assertEquals(1, byFullElement.size)
        assertEquals(inputFeature.id, byFullElement[0]!!.id)

        // And: the key alone does not (the tag is not split).
        assertTrue(executeTagsQuery(TagExists("fullelem")).features.isEmpty())
    }

    @Test
    fun shouldReturnFeaturesBySetContainment() {
        // Given:
        val enabledFeatureA = randomFeatureWithTags("flag:=true")
        val enabledFeatureB = randomFeatureWithTags("flag:=true")
        val disabledFeature = randomFeatureWithTags("flag:=false")

        // When:
        insertFeatures(enabledFeatureA, enabledFeatureB, disabledFeature)

        // And:
        val enabledFeatures = executeTagsQuery(
            TagSetContains("flag:=true")
        ).features

        // Then:
        assertEquals(2, enabledFeatures.size)
        val fetchedIds = enabledFeatures.map { it!!.id }
        assertTrue(fetchedIds.containsAll(listOf(enabledFeatureA.id, enabledFeatureB.id)))
    }

    @Test
    fun shouldPreserveTagOrderOnReadBack() {
        // Given: tags in an order that map-based storage would not preserve.
        val inputFeature = randomFeatureWithTags("zulu", "alpha", "mike", "bravo")

        // When:
        insertFeature(feature = inputFeature)

        // And:
        val readFeatures = executeTagsQuery(TagExists("zulu")).features

        // Then: the set guarantees the exact element order given at write time.
        assertEquals(1, readFeatures.size)
        val readTags = readFeatures[0]!!.properties.xyz.tags
        assertEquals(listOf("zulu", "alpha", "mike", "bravo"), readTags.filterNotNull())
    }

    @Test
    fun shouldReturnFeaturesForComposedTagQuery() {
        // Given:
        val activeJohn = randomFeatureWithTags(
            "username=john_doe",
            "is_active:=true",
        )
        val activeNick = randomFeatureWithTags(
            "username=nick_foo",
            "is_active:=true",
        )
        val inactiveJohn = randomFeatureWithTags(
            "username=john_doe",
            "is_active:=false",
        )
        val oldAdmin = randomFeatureWithTags(
            "username=some_admin",
            "role=admin"
        )
        val invalidUserWithoutId = randomFeatureWithTags("is_active:=true")

        // And:
        insertFeatures(activeJohn, activeNick, inactiveJohn, oldAdmin, invalidUserWithoutId)

        // When:
        val activeJohnOrAdmin = TagOr(
            TagAnd(
                TagExists("username=john_doe"),
                TagSetContains("is_active:=true")
            ),
            TagSetContains("role=admin")
        )
        val features = executeTagsQuery(activeJohnOrAdmin).features

        // Then:
        assertEquals(2, features.size)
        val featureIds = features.map { it!!.id }
        assertTrue(featureIds.containsAll(listOf(activeJohn.id, oldAdmin.id)))
    }

    @Test
    fun shouldReturnFeaturesForAllOfTagSet() {
        // Given:
        val taggedBoth = randomFeatureWithTags("seta", "setb")
        val taggedFoo = randomFeatureWithTags("seta")
        val taggedBar = randomFeatureWithTags("setb")

        // When:
        insertFeatures(taggedBoth, taggedFoo, taggedBar)

        // And: TagAnd of pure TagExists uses the `?&` (jsonb_exists_all) operator.
        val withBoth = executeTagsQuery(
            TagAnd(TagExists("seta"), TagExists("setb"))
        ).features

        // Then:
        assertEquals(1, withBoth.size)
        assertEquals(taggedBoth.id, withBoth[0]!!.id)

        // And: TagOr of pure TagExists uses the `?|` (jsonb_exists_any) operator.
        val withAny = executeTagsQuery(
            TagOr(TagExists("seta"), TagExists("setb"))
        ).features

        // Then:
        assertEquals(3, withAny.size)
    }

    @Test
    fun shouldTreatRefAsValueless() {
        // Given:
        val feature = randomFeatureWithTags("ref_lorem=ipsum")
        insertFeatures(feature)

        // When
        val byTagName = executeTagsQuery(TagExists("ref_lorem")).features

        // Then
        assertTrue(byTagName.isEmpty())

        // When
        val byFullTag = executeTagsQuery(TagExists("ref_lorem=ipsum")).features

        // Then
        assertEquals(1, byFullTag.size)
        assertEquals(feature.id, byFullTag[0]!!.id)
    }

    @Test
    fun shouldTreatSourceIDAsValueless() {
        // Given:
        val feature = randomFeatureWithTags("sourceID:=123")
        insertFeatures(feature)

        // When
        val byTagName = executeTagsQuery(TagExists("sourceID")).features

        // Then
        assertTrue(byTagName.isEmpty())

        // When
        val byFullTag = executeTagsQuery(TagExists("sourceID:=123")).features

        // Then
        assertEquals(1, byFullTag.size)
        assertEquals(feature.id, byFullTag[0]!!.id)
    }

    private fun randomFeatureWithTags(vararg tags: String): NakshaFeature {
        return RandomFeatures.randomFeature().apply {
            properties.xyz.tags = TagList(*tags)
        }
    }

    private fun executeTagsQuery(tagQuery: ITagQuery): SuccessResponse {
        return executeRead(ReadFeatures().apply {
            mapId = collection.mapId
            collectionIds += collection!!.id
            query.tags = tagQuery
        })
    }
}