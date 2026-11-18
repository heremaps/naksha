package naksha.psql

import naksha.base.NakshaError
import naksha.base.Id
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
 * Verifies that collection UPSERT compares normalized layouts and rejects actual schema changes.
 */
class CollectionSchemaImmutabilityTest : PgTestBase(collection = null, catalogId = "") {

    private val collectionId = "schema_immutability_test"

    private fun collectionDefinition(
        memberName: String = "score",
        type: MemberType = MemberType.INT32,
        path: JsonPath = JsonPath("properties", "score"),
        indexName: String = "score_idx"
    ) = NakshaCollection(collectionId, catalog.id).apply {
        members = MemberList(Member(memberName, type, path))
        indices = IndexList(Index(indexName, memberName))
    }

    private fun upsert(collection: NakshaCollection) = WriteRequest().add(Write().upsertCollection(collection))

    @Test
    fun shouldAllowUnchangedSchemaAndRejectSchemaChanges() {
        executeWrite(WriteRequest().add(Write().createCollection(collectionDefinition())))

        // The same schema, also with changed metadata, is accepted.
        executeWrite(upsert(collectionDefinition()))
        executeWrite(upsert(collectionDefinition().apply { this["description"] = "updated" }))

        val changes = listOf(
            "shift" to collectionDefinition().apply { shift = shift + 1 },
            "partitions" to collectionDefinition().apply { partitions = 2 },
            "storageClass" to collectionDefinition().apply { storageClass = "brittle" },
            "members" to collectionDefinition(type = MemberType.STRING),
            "members" to collectionDefinition(path = JsonPath("properties", "other")),
            "indices" to collectionDefinition(indexName = "other_idx"),
        )
        for ((field, changed) in changes) {
            val error = executeWriteErrorResponse(upsert(changed)).error
            assertEquals(NakshaError.ILLEGAL_ARGUMENT, error.code, field)
            val category = if (field == "members" || field == "indices") field else "partition and storage"
            assertContains(error.msg, category)
        }
    }

  @Test
  fun shouldNormalizeDeclarationsToTheSamePhysicalColumnOrder() {
    val original = alignmentDefinition(false)
    executeWrite(WriteRequest().add(Write().createCollection(original)))
    val before = physicalLayout(original.id)
    executeWrite(upsert(alignmentDefinition(true)))
    assertEquals(before, physicalLayout(original.id))
    executeWrite(upsert(alignmentDefinition(false)))
    assertEquals(before, physicalLayout(original.id))
  }

  private fun alignmentDefinition(reverse: Boolean): NakshaCollection {
    val members = listOf(
      Member("zulu", MemberType.INT32), Member("alpha", MemberType.INT32),
      Member("wide", MemberType.INT64), Member("small", MemberType.INT8)
    )
    return NakshaCollection("schema_alignment_test", catalog.id)
      .withMembers(if (reverse) members.reversed() else members)
      .withIndices(Index("alpha_idx", "alpha", "zulu"))
  }

  private fun physicalLayout(id: String): Pair<List<String>, List<String>> =
    storage.newConnection(newSessionOptions(), true).use { conn ->
      val collection = assertNotNull(storage.database.getPgCollection(Id(catalog.id).intValue, Id(id).intValue, conn))
      assertEquals(listOf("wide", "alpha", "zulu", "small"),
        assertNotNull(collection.naksha.members).takeLast(4).map { assertNotNull(it).name })
      val columns = ArrayList<String>()
      conn.execute("SELECT attname FROM pg_attribute WHERE attrelid = $1::regclass AND attnum > 0 AND NOT attisdropped ORDER BY attnum",
        arrayOf(collection.qualifiedName)).fetch().use { cursor ->
        while (cursor.isRow()) {
          columns.add(cursor.get<String>("attname"))
          cursor.next()
        }
      }
      assertEquals(collection.columns.map { it.columnName }, columns)
      val indices = ArrayList<String>()
      conn.execute("SELECT pg_get_indexdef(indexrelid) AS definition FROM pg_index WHERE indrelid = $1::regclass ORDER BY indexrelid",
        arrayOf(collection.qualifiedName)).fetch().use { cursor ->
        while (cursor.isRow()) {
          indices.add(cursor.get<String>("definition"))
          cursor.next()
        }
      }
      Pair(columns, indices)
    }
}
