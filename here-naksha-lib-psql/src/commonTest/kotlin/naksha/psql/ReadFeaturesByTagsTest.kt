package naksha.psql

import naksha.model.TagList
import naksha.model.objects.NakshaCollection
import naksha.model.objects.NakshaFeature
import naksha.model.request.ReadFeatures
import naksha.model.request.SuccessResponse
import naksha.model.request.query.*
import naksha.psql.base.PgTestBase
import naksha.psql.util.ProxyFeatureGenerator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReadFeaturesByTagsTest : PgTestBase(NakshaCollection("read_by_tags_test")) {

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

    @Test
    fun shouldReturnFeaturesWithStringValue() {
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

    @Test
    fun shouldReturnFeaturesByTagRegex() {
        // Given:
        val featureFrom2024 = randomFeatureWithTags("year=2024")
        val featureFrom2030 = randomFeatureWithTags("year=2030")

        // When:
        insertFeatures(featureFrom2024, featureFrom2030)

        // And:
        val featuresFromThisDecade = executeTagsQuery(
            TagValueMatches(name = "year", regex = "202[0-9]")
        ).features

        // Then:
        assertEquals(1, featuresFromThisDecade.size)
        assertEquals(featureFrom2024.id, featuresFromThisDecade[0]!!.id)
    }

    @Test
    fun shouldReturnFeaturesWithDoubleValue() {
        // Given:
        val inputFeatures = listOf(
            randomFeatureWithTags("some_number:=1").apply { id = "one" },
            randomFeatureWithTags("some_number:=5").apply { id = "five" },
        )

        // When:
        insertFeatures(inputFeatures)

        // And:
        val featuresGt2 = executeTagsQuery(
            TagValueIsDouble("some_number", DoubleOp.GT, 1.0)
        ).features

        // Then:
        assertEquals(1, featuresGt2.size)
        assertEquals("five", featuresGt2[0]!!.id)

        // When
        val featuresLte5 = executeTagsQuery(
            TagValueIsDouble("some_number", DoubleOp.LTE, 5.0)
        ).features

        // Then:
        assertEquals(2, featuresLte5.size)
        val lte5ids = featuresLte5.map { it!!.id }
        assertTrue(lte5ids.containsAll(listOf("one", "five")))

        // When:
        val featuresEq6 = executeTagsQuery(
            TagValueIsDouble("some_number", DoubleOp.EQ, 6.0)
        ).features

        // Then:
        assertTrue(featuresEq6.isEmpty())
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
            "username=john_bar",
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
        val activeJohnsOrAdmin = TagOr(
            TagAnd(
                TagValueMatches(name = "username", regex = "john.+"),
                TagValueIsBool(name = "is_active", value = true)
            ),
            TagValueIsString(name = "role", value = "admin")
        )
        val features = executeTagsQuery(activeJohnsOrAdmin).features

        // Then:
        assertEquals(2, features.size)
        val featureIds = features.map { it!!.id }
        featureIds.containsAll(
            listOf(
                activeJohn.id,
                oldAdmin.id
            )
        )
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
        return ProxyFeatureGenerator.generateRandomFeature().apply {
            properties.xyz.tags = TagList(*tags)
        }
    }

    private fun executeTagsQuery(tagQuery: ITagQuery): SuccessResponse {
        return executeRead(ReadFeatures().apply {
            collectionIds += collection!!.id
            query.tags = tagQuery
        })
    }
}