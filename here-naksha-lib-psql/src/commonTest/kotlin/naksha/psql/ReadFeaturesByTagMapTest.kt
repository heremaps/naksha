package naksha.psql

import naksha.model.Naksha
import naksha.model.RandomFeatures
import naksha.model.TagMap
import naksha.model.objects.Index
import naksha.model.objects.Member
import naksha.model.objects.MemberType
import naksha.model.objects.NakshaCollection
import naksha.model.objects.NakshaFeature
import naksha.model.request.ReadFeatures
import naksha.model.request.SuccessResponse
import naksha.model.request.ops.And
import naksha.model.request.ops.Not
import naksha.model.request.ops.Op
import naksha.model.request.ops.Or
import naksha.model.request.ops.TagEquals
import naksha.model.request.ops.TagGt
import naksha.model.request.ops.TagGte
import naksha.model.request.ops.TagIsNull
import naksha.model.request.ops.TagLt
import naksha.model.request.ops.TagLte
import naksha.model.request.ops.TagMapHasAllOf
import naksha.model.request.ops.TagMapHasAnyOf
import naksha.model.request.ops.TagMapHasKey
import naksha.model.request.ops.TagMatches
import naksha.model.request.ops.TagStartsWith
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private val SEARCH_TAGS_MEMBER = Member("search_tags", MemberType.TAG_MAP)

class ReadFeaturesByTagMapTest : PgTestBase(
    collection = NakshaCollection("").apply {
        addMember(SEARCH_TAGS_MEMBER)
        addIndex(Index("idx_search_tags", SEARCH_TAGS_MEMBER.name))
    }
) {

    @Test
    fun shouldFindFeatureWithTagMapKey() {
        val matching = featureWithTags("has_key" to "value")
        val other = featureWithTags("other_has_key" to "value")
        insertFeatures(matching, other)

        assertReturnedIds(TagMapHasKey(SEARCH_TAGS_MEMBER, "has_key"), matching.id)
        assertReturnedIds(TagMapHasKey(SEARCH_TAGS_MEMBER, "missing_has_key"))
    }

    @Test
    fun shouldFindFeatureWithAnyTagMapKey() {
        val first = featureWithTags("any_first" to true)
        val second = featureWithTags("any_second" to true)
        val neither = featureWithTags("any_neither" to true)
        insertFeatures(first, second, neither)

        assertReturnedIds(
            TagMapHasAnyOf(SEARCH_TAGS_MEMBER, "any_first", "any_second"),
            first.id,
            second.id,
        )
        assertReturnedIds(TagMapHasAnyOf(SEARCH_TAGS_MEMBER, "missing_any_first", "missing_any_second"))
    }

    @Test
    fun shouldFindFeatureWithAllTagMapKeys() {
        val both = featureWithTags("all_first" to 1, "all_second" to 2)
        val firstOnly = featureWithTags("all_first" to 1)
        val secondOnly = featureWithTags("all_second" to 2)
        insertFeatures(both, firstOnly, secondOnly)

        assertReturnedIds(TagMapHasAllOf(SEARCH_TAGS_MEMBER, "all_first", "all_second"), both.id)
        assertReturnedIds(TagMapHasAllOf(SEARCH_TAGS_MEMBER, "all_first", "missing_all"))
    }

    @Test
    fun shouldFindTagMapNullValue() {
        val nullValue = featureWithTags("nullable_value" to null)
        val nonNullValue = featureWithTags("nullable_value" to "value")
        val missingKey = featureWithTags("other_nullable_value" to null)
        insertFeatures(nullValue, nonNullValue, missingKey)

        assertReturnedIds(TagIsNull(SEARCH_TAGS_MEMBER, "nullable_value"), nullValue.id)
        assertReturnedIds(TagEquals(SEARCH_TAGS_MEMBER, "nullable_value", null), nullValue.id)
    }

    @Test
    fun shouldFindEqualTagMapValues() {
        val stringValue = featureWithTags("equals_value" to "5")
        val integerValue = featureWithTags("equals_value" to 5)
        val doubleValue = featureWithTags("equals_value" to 5.5)
        val booleanValue = featureWithTags("equals_value" to true)
        val nullValue = featureWithTags("equals_value" to null)
        insertFeatures(stringValue, integerValue, doubleValue, booleanValue, nullValue)

        assertReturnedIds(TagEquals(SEARCH_TAGS_MEMBER, "equals_value", "5"), stringValue.id)
        assertReturnedIds(TagEquals(SEARCH_TAGS_MEMBER, "equals_value", 5), integerValue.id)
        assertReturnedIds(TagEquals(SEARCH_TAGS_MEMBER, "equals_value", 5.5), doubleValue.id)
        assertReturnedIds(TagEquals(SEARCH_TAGS_MEMBER, "equals_value", true), booleanValue.id)
        assertReturnedIds(TagEquals(SEARCH_TAGS_MEMBER, "equals_value", null), nullValue.id)
    }

    @Test
    fun shouldCompareNumericTagMapValues() {
        val below = featureWithTags("numeric_value" to 4)
        val equal = featureWithTags("numeric_value" to 5)
        val above = featureWithTags("numeric_value" to 6)
        val stringValue = featureWithTags("numeric_value" to "6")
        insertFeatures(below, equal, above, stringValue)

        assertReturnedIds(TagGt(SEARCH_TAGS_MEMBER, "numeric_value", 5), above.id)
        assertReturnedIds(TagGte(SEARCH_TAGS_MEMBER, "numeric_value", 5), equal.id, above.id)
        assertReturnedIds(TagLt(SEARCH_TAGS_MEMBER, "numeric_value", 5), below.id)
        assertReturnedIds(TagLte(SEARCH_TAGS_MEMBER, "numeric_value", 5), below.id, equal.id)
    }

    @Test
    fun shouldFindTagMapValueByPrefix() {
        val first = featureWithTags("prefix_value" to "alpha-one")
        val second = featureWithTags("prefix_value" to "alpha-two")
        val other = featureWithTags("prefix_value" to "beta-one")
        insertFeatures(first, second, other)

        assertReturnedIds(TagStartsWith(SEARCH_TAGS_MEMBER, "prefix_value", "alpha-"), first.id, second.id)
        assertReturnedIds(TagStartsWith(SEARCH_TAGS_MEMBER, "prefix_value", "alpha-one"), first.id)
        assertReturnedIds(TagStartsWith(SEARCH_TAGS_MEMBER, "prefix_value", "missing-prefix"))
    }

    @Test
    fun shouldFindTagMapValueByRegex() {
        val first = featureWithTags("regex_value" to "alpha-123")
        val second = featureWithTags("regex_value" to "alpha-abc")
        val other = featureWithTags("regex_value" to "beta-123")
        insertFeatures(first, second, other)

        assertReturnedIds(TagMatches(SEARCH_TAGS_MEMBER, "regex_value", "^alpha-[0-9]+$"), first.id)
        assertReturnedIds(TagMatches(SEARCH_TAGS_MEMBER, "regex_value", "^missing-.*"))
    }

    @Test
    fun shouldNegateTagMapQueries() {
        val matching = featureWithTags(
            "negation_scope" to null,
            "negation_key" to "match",
            "negation_number" to 10,
        )
        val other = featureWithTags(
            "negation_scope" to null,
            "negation_key" to "other",
            "negation_number" to 2,
        )
        val missing = featureWithTags("negation_scope" to null)
        insertFeatures(matching, other, missing)

        assertReturnedIds(
            And(TagMapHasKey(SEARCH_TAGS_MEMBER, "negation_scope"), Not(TagMapHasKey(SEARCH_TAGS_MEMBER, "negation_key"))),
            missing.id,
        )
        assertReturnedIds(
            And(TagMapHasKey(SEARCH_TAGS_MEMBER, "negation_scope"), Not(TagEquals(SEARCH_TAGS_MEMBER, "negation_key", "match"))),
            other.id,
            missing.id,
        )
        assertReturnedIds(
            And(TagMapHasKey(SEARCH_TAGS_MEMBER, "negation_scope"), Not(TagGt(SEARCH_TAGS_MEMBER, "negation_number", 5))),
            other.id,
            missing.id,
        )
    }

    @Test
    fun shouldComposeTagMapQueries() {
        val activeAdmin = featureWithTags(
            "composition_scope" to null,
            "composition_active" to true,
            "composition_role" to "admin",
        )
        val activeUser = featureWithTags(
            "composition_scope" to null,
            "composition_active" to true,
            "composition_role" to "user",
        )
        val inactiveAdmin = featureWithTags(
            "composition_scope" to null,
            "composition_active" to false,
            "composition_role" to "admin",
        )
        insertFeatures(activeAdmin, activeUser, inactiveAdmin)

        assertReturnedIds(
            And(
                TagMapHasKey(SEARCH_TAGS_MEMBER, "composition_scope"),
                TagEquals(SEARCH_TAGS_MEMBER, "composition_active", true),
                TagEquals(SEARCH_TAGS_MEMBER, "composition_role", "admin"),
            ),
            activeAdmin.id,
        )
        assertReturnedIds(
            And(
                TagMapHasKey(SEARCH_TAGS_MEMBER, "composition_scope"),
                Or(
                    TagEquals(SEARCH_TAGS_MEMBER, "composition_active", false),
                    TagEquals(SEARCH_TAGS_MEMBER, "composition_role", "user"),
                ),
            ),
            activeUser.id,
            inactiveAdmin.id,
        )
    }

    @Test
    fun shouldReadWrittenTagMap() {
        val input = featureWithTags(
            "roundtrip_marker" to null,
            "roundtrip_string" to "value",
            "roundtrip_boolean" to true,
            "roundtrip_integer" to 42,
            "roundtrip_double" to 12.5,
        )
        insertFeature(input)

        val feature = executeTagMapQuery(TagMapHasKey(SEARCH_TAGS_MEMBER, "roundtrip_marker")).features.single()
        assertNotNull(feature)
        val tags = SEARCH_TAGS_MEMBER.readTagMap(feature)
        assertNotNull(tags)
        assertTrue(tags.contains("roundtrip_marker"))
        assertEquals(null, tags["roundtrip_marker"])
        assertEquals("value", tags["roundtrip_string"])
        assertEquals(true, tags["roundtrip_boolean"])
        assertEquals(42L, (tags["roundtrip_integer"] as Number).toLong())
        assertEquals(12.5, (tags["roundtrip_double"] as Number).toDouble())
    }

    private fun featureWithTags(vararg tags: Pair<String, Any?>): NakshaFeature =
        RandomFeatures.randomFeature().apply {
            properties[SEARCH_TAGS_MEMBER.name] = TagMap().apply {
                for ((key, value) in tags) put(key, value)
            }
        }

    private fun executeTagMapQuery(op: Op): SuccessResponse = executeRead(ReadFeatures().apply {
        catalogId = collection.catalogId
        collectionId = collection.id
        queryMembers = op
    })

    private fun assertReturnedIds(op: Op, vararg expectedIds: String) {
        val actualIds = executeTagMapQuery(op).features.mapNotNull { it?.id }.toSet()
        assertEquals(expectedIds.toSet(), actualIds)
    }
}
