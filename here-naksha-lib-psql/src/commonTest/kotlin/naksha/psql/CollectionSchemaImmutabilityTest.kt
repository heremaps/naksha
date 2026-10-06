package naksha.psql

import naksha.base.NakshaError
import naksha.model.objects.Index
import naksha.model.objects.IndexList
import naksha.model.objects.JsonPath
import naksha.model.objects.Member
import naksha.model.objects.MemberList
import naksha.model.objects.MemberType
import naksha.model.objects.NakshaCollection
import naksha.model.request.Write
import naksha.model.request.WriteRequest
import kotlin.test.*

/**
 * Verifies [PgCollection.verifyNewHeadState]: the schema of an existing collection must not change on UPSERT.
 */
class CollectionSchemaImmutabilityTest : PgTestBase(collection = null, catalogId = "") {

    private val collectionId = "schema_immutability_test"

    private fun definition(
        type: MemberType = MemberType.INT32,
        path: JsonPath = JsonPath("properties", "score"),
        indexName: String = "score_idx"
    ) = NakshaCollection(collectionId, catalog.id).apply {
        members = MemberList(Member("score", type, path))
        indices = IndexList(Index(indexName, "score"))
    }

    private fun upsert(collection: NakshaCollection) = WriteRequest().add(Write().upsertCollection(collection))

    @Test
    fun shouldAllowUnchangedSchemaAndRejectSchemaChanges() {
        executeWrite(WriteRequest().add(Write().createCollection(definition())))

        // The same schema, also with changed metadata, is accepted.
        executeWrite(upsert(definition()))
        executeWrite(upsert(definition().apply { this["description"] = "updated" }))

        val changes = listOf(
            "shift" to definition().apply { shift = shift + 1 },
            "partitions" to definition().apply { partitions = 2 },
            "storageClass" to definition().apply { storageClass = "brittle" },
            "members" to definition(type = MemberType.STRING),
            "members" to definition(path = JsonPath("properties", "other")),
            "indices" to definition(indexName = "other_idx"),
        )
        for ((field, changed) in changes) {
            val error = executeWriteErrorResponse(upsert(changed)).error
            assertEquals(NakshaError.CONFLICT, error.code, field)
            assertContains(error.msg, "'$field'")
        }
    }
}
