package naksha.psql

import naksha.model.TagList
import naksha.model.objects.NakshaFeature
import naksha.model.request.ReadFeatures
import naksha.model.request.SuccessResponse
import naksha.model.request.query.*
import naksha.model.RandomFeatures
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Query semantics for the standard `tags` member, which is now a [naksha.model.objects.MemberType.SET]
 * (a `jsonb` array of unique strings). Element-exists queries (`?`, `?|`, `?&`) work natively.
 *
 * Value-shaped queries (`TagValueIs*`, `TagValueMatches`) still emit their original `tags->>'k' = …`
 * SQL — that SQL is valid against a `jsonb` object (TAGS / TAGS_FROM_ARRAY) but evaluates to NULL/false
 * on a `jsonb` array (SET), so it simply matches no rows on a SET-stored standard tags column. That's
 * intentional: a custom collection that declares its `tags` member as TAGS or TAGS_FROM_ARRAY keeps the
 * full key/value query surface.
 */
class ReadFeaturesByTagsTest : PgTestBase() {

    @Test
    fun shouldReturnFeaturesWithExistingTag() {
        val inputFeature = randomFeatureWithTags("sample")
        insertFeature(feature = inputFeature)

        val featuresWithFooTag = executeTagsQuery(TagExists("sample")).features

        assertEquals(1, featuresWithFooTag.size)
        assertEquals(inputFeature.id, featuresWithFooTag[0]!!.id)
    }

    @Test
    fun shouldNotReturnFeaturesWithMissingTag() {
        val inputFeature = randomFeatureWithTags("existing")
        insertFeature(feature = inputFeature)

        val featuresWithFooTag = executeTagsQuery(TagExists("non-existing")).features

        assertEquals(0, featuresWithFooTag.size)
    }

    @Test
    fun shouldMatchFullTagStringEntries() {
        // SET treats each entry as an opaque string — "foo=bar" matches as a single set element.
        val inputFeature = randomFeatureWithTags("foo=bar")
        insertFeature(feature = inputFeature)

        val matched = executeTagsQuery(TagExists("foo=bar")).features

        assertEquals(1, matched.size)
        assertEquals(inputFeature.id, matched[0]!!.id)
    }

    @Test
    fun shouldReturnNoRowsForMapShapedQueriesAgainstSetTags() {
        // The standard `tags` column is now SET-shaped (jsonb array). Value-shaped queries still
        // emit valid SQL (`tags->>'k' = 'v'`, etc.), but on a jsonb array those expressions
        // evaluate to NULL, so no rows match. The queries do not error.
        val feature = randomFeatureWithTags("flag:=true", "foo=bar")
        insertFeature(feature = feature)

        assertTrue(executeTagsQuery(TagValueIsBool(name = "flag", value = true)).features.isEmpty())
        assertTrue(executeTagsQuery(TagValueIsString(name = "foo", value = "bar")).features.isEmpty())
        assertTrue(executeTagsQuery(TagValueIsDouble("flag", DoubleOp.GT, 0.0)).features.isEmpty())
        assertTrue(executeTagsQuery(TagValueMatches(name = "foo", regex = "ba.+")).features.isEmpty())
    }

    @Test
    fun shouldComposeWithAndOr() {
        // AND-of-TagExists / OR-of-TagExists collapse to the optimised `?&` / `?|` operators.
        val activeJohn = randomFeatureWithTags("username=john_doe", "is_active:=true")
        val activeNick = randomFeatureWithTags("username=nick_foo", "is_active:=true")
        val inactiveJohn = randomFeatureWithTags("username=john_bar", "is_active:=false")
        insertFeatures(activeJohn, activeNick, inactiveJohn)

        val both = executeTagsQuery(TagAnd(TagExists("username=john_doe"), TagExists("is_active:=true"))).features
        assertEquals(1, both.size)
        assertEquals(activeJohn.id, both[0]!!.id)

        val either = executeTagsQuery(TagOr(TagExists("username=john_doe"), TagExists("username=nick_foo"))).features
        assertEquals(2, either.size)
        val ids = either.map { it!!.id }
        assertTrue(ids.containsAll(listOf(activeJohn.id, activeNick.id)))
    }

    @Test
    fun shouldTreatRefAsValueless() {
        val feature = randomFeatureWithTags("ref_lorem=ipsum")
        insertFeatures(feature)

        // The set is the opaque entry "ref_lorem=ipsum" — partial name lookup misses, full match hits.
        assertTrue(executeTagsQuery(TagExists("ref_lorem")).features.isEmpty())

        val byFullTag = executeTagsQuery(TagExists("ref_lorem=ipsum")).features
        assertEquals(1, byFullTag.size)
        assertEquals(feature.id, byFullTag[0]!!.id)
    }

    @Test
    fun shouldTreatSourceIDAsValueless() {
        val feature = randomFeatureWithTags("sourceID:=123")
        insertFeatures(feature)

        assertTrue(executeTagsQuery(TagExists("sourceID")).features.isEmpty())

        val byFullTag = executeTagsQuery(TagExists("sourceID:=123")).features
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
