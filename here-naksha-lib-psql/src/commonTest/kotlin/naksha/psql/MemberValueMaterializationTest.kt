package naksha.psql

import naksha.base.Int64
import naksha.model.objects.JsonPath
import naksha.model.objects.Member
import naksha.model.objects.MemberType
import naksha.model.objects.NakshaCollection
import naksha.model.objects.NakshaFeature
import naksha.model.request.Write
import naksha.model.request.WriteRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests that custom [Member] values are actually materialized into their physical Postgres column
 * when features are written.
 */
class MemberValueMaterializationTest : PgTestBase(collection = null, mapId = "") {

    private fun featureJson(id: String, name: String, city: String, score: Long): String = """
        {
          "type": "Feature",
          "id": "$id",
          "geometry": null,
          "properties": {
            "name": "$name",
            "score": $score,
            "address": {
              "city": "$city"
            }
          }
        }
    """.trimIndent()

    private fun asLong(value: Any?): Long {
        assertNotNull(value, "expected a numeric column value, got null")
        return when (value) {
            is Int64 -> value.toLong()
            is Number -> value.toLong()
            else -> error("expected a numeric column value, got ${value::class.simpleName}")
        }
    }

    /**
     * Reads a single column for a single feature, directly from the collection's HEAD table.
     */
    private fun readColumn(collection: NakshaCollection, featureId: String, column: String): Any? {
        storage.adminConnection().use { conn ->
            conn.execute(
                """SELECT "$column" AS value FROM "${collection.mapId}"."${collection.id}" WHERE id = $1""",
                arrayOf(featureId)
            ).use { cursor ->
                assertTrue(cursor.next(), "No HEAD row found for feature '$featureId'")
                return cursor.column("value")
            }
        }
    }

    /**
     * Add a member on a specific (custom, nested) path, create the collection, insert 2 features,
     * and expect both members to be materialized into their respective columns.
     */
    @Test
    fun shouldMaterializeMembersOnInsert() {
        // Given: a collection with two members
        val collection = NakshaCollection("member_materialization_test", map.id).apply {
            addMember(Member("label", MemberType.STRING, JsonPath("properties", "name")))
            addMember(Member("city", MemberType.STRING, JsonPath("properties", "address", "city")))
        }
        executeWrite(WriteRequest().add(Write().createCollection(collection)))

        // And: 2 features to insert
        val first = NakshaFeature.fromJson(featureJson("feature-1", "Alice", "Berlin", 10))
        val second = NakshaFeature.fromJson(featureJson("feature-2", "Bob", "Munich", 20))

        // When
        executeWrite(WriteRequest().apply {
            add(Write().createFeature(collection.mapId, collection.id, first))
            add(Write().createFeature(collection.mapId, collection.id, second))
        })

        // Then
        val labelCol = PgCustomMemberValues.pgColumnName("label")
        val cityCol = PgCustomMemberValues.pgColumnName("city")

        assertEquals("Alice", readColumn(collection, "feature-1", labelCol))
        assertEquals("Berlin", readColumn(collection, "feature-1", cityCol))

        assertEquals("Bob", readColumn(collection, "feature-2", labelCol))
        assertEquals("Munich", readColumn(collection, "feature-2", cityCol))
    }

    /**
     * Members are also coerced to their declared type before being written: an [MemberType.INT64]
     * member on `properties.score` must end up as a numeric value in its `bigint` column.
     */
    @Test
    fun shouldMaterializeTypedMemberOnInsert() {
        // Given: a collection with a numeric member on properties.score
        val collection = NakshaCollection("member_typed_materialization_test", map.id).apply {
            addMember(Member("score", MemberType.INT64, JsonPath("properties", "score")))
        }
        executeWrite(WriteRequest().add(Write().createCollection(collection)))

        // And: 2 features to insert
        val first = NakshaFeature.fromJson(featureJson("typed-1", "Alice", "Berlin", 42))
        val second = NakshaFeature.fromJson(featureJson("typed-2", "Bob", "Munich", 7))

        // When
        executeWrite(WriteRequest().apply {
            add(Write().createFeature(collection.mapId, collection.id, first))
            add(Write().createFeature(collection.mapId, collection.id, second))
        })

        // Then
        val scoreCol = PgCustomMemberValues.pgColumnName("score")

        assertEquals(42L, asLong(readColumn(collection, "typed-1", scoreCol)))
        assertEquals(7L, asLong(readColumn(collection, "typed-2", scoreCol)))
    }
}
