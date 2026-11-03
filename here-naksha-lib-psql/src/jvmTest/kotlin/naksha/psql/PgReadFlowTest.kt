@file:Suppress("DEPRECATION")

package naksha.psql

import java.sql.DriverManager
import naksha.base.Action
import naksha.base.Guid
import naksha.base.Id
import naksha.base.Version
import naksha.model.Naksha
import naksha.model.TagList
import naksha.model.Tuple
import naksha.model.objects.IndexList
import naksha.model.objects.Int64Member
import naksha.model.objects.Member
import naksha.model.objects.MemberList
import naksha.model.objects.NakshaCatalog
import naksha.model.objects.NakshaCollection
import naksha.model.objects.NakshaFeature
import naksha.model.objects.StandardMembers
import naksha.model.request.ErrorResponse
import naksha.model.request.OrderBy
import naksha.model.request.ReadFeatures
import naksha.model.request.SuccessResponse
import naksha.model.request.Write
import naksha.model.request.WriteRequest
import naksha.model.request.ops.And
import naksha.model.request.ops.Equals
import naksha.model.request.ops.Gte
import naksha.model.request.ops.IsAnyOf
import naksha.model.request.ops.Lte
import naksha.model.request.ops.Op
import naksha.model.request.query.SortOrder
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertTrue

@EnabledIfEnvironmentVariable(named = "NAKSHA_TEST_READ_DB_URL", matches = ".+")
class PgReadFlowTest {
  @Test
  fun readsReferencesAndMaterializesFeaturesBeforeCommitAndFromColdStorage() {
    val url = System.getenv("NAKSHA_TEST_READ_DB_URL")
    assertEquals(url, System.getenv("NAKSHA_TEST_PSQL_DB_URL"),
      "READ flow and storage bootstrap must use the same fresh dedicated database")
    DriverManager.getConnection(url).use { jdbc ->
      jdbc.createStatement().use { statement ->
        statement.executeQuery("SELECT 1 FROM pg_namespace WHERE nspname = 'naksha~admin'").use { rows ->
          assertFalse(rows.next(), "Requires a fresh disposable database; existing schemas are never cleared")
        }
      }
    }
    val config = PgConfig("read_flow_test").apply {
      className = "naksha.psql.PgStorage"
      master = PgInstanceConfig.fromUri(url)
    }
    val storage = PgStorage(config, true, false)
    assertTrue(storage.initialized)
    val catalog = NakshaCatalog(storage.id, "read_flow_catalog")
    val collection = NakshaCollection("read_flow_features", catalog.id).apply {
      databaseId = storage.id.text
      members = MemberList(Member("name"), Int64Member("count"))
      indices = IndexList()
      shift = 12
    }
    val created = ArrayList<Tuple>()
    lateinit var catalogTuple: Tuple
    lateinit var collectionTuple: Tuple
    storage.newSession(Naksha.useAdminOptions(), false).use { writer ->
      try {
        catalogTuple = create(writer, Write().createCatalog(catalog))
        collectionTuple = create(writer, Write().createCollection(collection))
        assertRead(writer, collection, emptyList(), "empty HEAD")
        for (id in listOf("1", "256", "read-flow-alpha", "read-flow-beta")) {
          val feature = NakshaFeature(id).apply {
            properties.setRaw("name", "name-$id")
            properties.setRaw("count", 42L)
            properties.xyz.tags = TagList("read-flow", "id:$id")
            setRaw("payload", "payload-$id")
          }
          val tuple = create(writer, Write().createFeature(collection, feature))
          assertEquals(Id(id).number, tuple.tupleNumber.featureNumber)
          created.add(tuple)
        }
        assertReads(writer, collection, created, "transaction-local")
        // Fixture seam only: intentionally bypass the legacy transaction-log UPSERT and commit/cache lifecycle.
        // Closing the writer rolls back/closes its connection; no uncommitted fixture work remains.
        writer.useConnNonNull(false) { conn ->
          assertFalse(conn.autoCommit)
          val metadata = assertNotNull(writer.getPgCollection(Id(catalog.id).intValue, Id.COLLECTIONS_COL_ID.intValue))
          insertHistoricalMetadata(storage, conn, storage.database.pgAdminCatalogs, catalogTuple)
          insertHistoricalMetadata(storage, conn, metadata, collectionTuple)
          conn.commit()
        }
      } finally {
        writer.rollback()
      }
    }

    Naksha.cache.clear(storage)
    // Construct directly: Naksha.useStorage would reuse global storage and its metadata cache.
    // true/false attaches to and validates the existing bootstrap without upgrading/reinstalling it.
    val cold = PgStorage(config, true, false)
    assertTrue(cold.initialized)
    assertNotSame(storage.database, cold.database)
    assertNull(cold.database.getPgCatalog(Id(catalog.id).intValue), "User catalog must not already be cached")
    assertNull(cold.database.getPgCollection(Id(catalog.id).intValue, Id(collection.id).intValue),
      "User collection must not already be cached")
    cold.newSession(Naksha.useAdminOptions(), true).use { reader ->
      assertTrue(reader.readOnly)
      assertReads(reader, collection, created, "cold committed")
      assertEquals(catalogTuple.tupleNumber,
        assertNotNull(reader.getPgCatalog(Id(catalog.id).intValue)).tuple.tupleNumber,
        "Cold catalog metadata must resolve HEAD, not the historical row")
      assertEquals(collectionTuple.tupleNumber,
        assertNotNull(reader.getPgCollection(Id(catalog.id).intValue, Id(collection.id).intValue)).tuple.tupleNumber,
        "Cold collection metadata must resolve HEAD, not the historical row")
      assertNull(reader.getTransaction(), "Public reads must not start a write transaction")
    }
  }

  private fun insertHistoricalMetadata(storage: PgStorage, conn: PgConnection, metadata: PgCollection, head: Tuple) {
    val version = head.version - 4L
    storage.database.collection_maintain_partitions(conn, metadata.catalogId.catalogName(), metadata.name, version)
    val columns = metadata.columns.joinToString { it.ident }
    val values = metadata.columns.joinToString {
      when (it.name) {
        StandardMembers.VERSION -> version.toString()
        StandardMembers.NEXT_VERSION -> head.version.toString()
        else -> it.ident
      }
    }
    val fn = Id.featureNumberToOrdinal(head.tupleNumber.featureNumber)
    conn.jdbc.createStatement().use { statement ->
      assertEquals(1, statement.executeUpdate(
        "INSERT INTO ${metadata.qualifiedName} ($columns) SELECT $values FROM ${metadata.qualifiedName} " +
          "WHERE fn = $fn AND nv = ${Version.HEAD.number}"), "Must copy exactly one HEAD metadata row into history")
    }
  }

  private fun create(session: PgSession, write: Write): Tuple {
    val response = session.executeWrite(WriteRequest().add(write))
    assertFalse(response is ErrorResponse, "Fixture CREATE failed: $response")
    val success = assertIs<SuccessResponse>(response)
    return assertNotNull(assertNotNull(success.featureTupleList.single()).tuple)
  }

  private fun assertReads(session: PgSession, collection: NakshaCollection, created: List<Tuple>, stage: String) {
    val ordered = created.sortedBy { it.tupleNumber.featureNumber }
    assertRead(session, collection, ordered, "$stage all HEAD")
    assertRead(session, collection, listOf(created[0]), "$stage numeric ID 1", Equals(StandardMembers.Id, "1"))
    assertRead(session, collection, listOf(created[1]), "$stage numeric ID 256", Equals(StandardMembers.Id, "256"))
    assertRead(session, collection, listOf(created[2]), "$stage hashed ID",
      Equals(StandardMembers.Id, "read-flow-alpha"))
    assertRead(session, collection, ordered, "$stage mixed IDs",
      IsAnyOf(StandardMembers.Id, "1", "256", "read-flow-alpha", "read-flow-beta"))
    assertRead(session, collection, listOf(created[0], created[1]), "$stage logical feature-number range",
      And(Gte(StandardMembers.FeatureNumber, 1L), Lte(StandardMembers.FeatureNumber, 256L)))
    assertRead(session, collection, listOf(created[0], created[1]), "$stage numeric ID range",
      And(Gte(StandardMembers.Id, "1"), Lte(StandardMembers.Id, "256")))
    assertRead(session, collection, ordered.reversed(), "$stage descending logical feature-number order",
      descending = true)
    assertRead(session, collection, listOf(created[1]), "$stage numeric GUID", guid = created[1])
    assertRead(session, collection, listOf(created[3]), "$stage hashed GUID", guid = created[3])
    assertRead(session, collection, emptyList(), "$stage nonmatching ID",
      Equals(StandardMembers.Id, "read-flow-missing"))
  }

  private fun assertRead(
    session: PgSession,
    collection: NakshaCollection,
    expected: List<Tuple>,
    stage: String,
    query: Op? = null,
    descending: Boolean = false,
    guid: Tuple? = null
  ) {
    val request = ReadFeatures().apply {
      catalogId = collection.catalogId
      collectionId = collection.id
      queryMembers = query
      orderBy = OrderBy(StandardMembers.FeatureNumber,
        if (descending) SortOrder.DESCENDING else SortOrder.ASCENDING)
      if (guid != null) guids.add(Guid(guid.id.text, guid.tupleNumber))
    }
    val response = session.executeRead(request)
    assertFalse(response is ErrorResponse, "$stage failed: $response")
    val references = assertIs<SuccessResponse>(response, stage).featureTupleList
    assertEquals(expected.size, references.size, stage)
    for (i in expected.indices) {
      val reference = assertNotNull(references[i], stage)
      assertEquals(expected[i].tupleNumber, reference.tupleNumber, "$stage logical tuple-number")
      // FeatureTuple's constructor may hit the CREATE cache; force the public database materialization path.
      reference.tuple = null
    }
    session.loadTuples(references, 0, references.size)
    for (i in expected.indices) {
      val original = expected[i]
      val reference = assertNotNull(references[i], stage)
      val tuple = assertNotNull(reference.tuple, "$stage materialized tuple")
      assertNotSame(original, tuple, "$stage must load a new tuple from PostgreSQL")
      assertEquals(original.tupleNumber, tuple.tupleNumber, stage)
      assertEquals(original.id, tuple.id, stage)
      assertEquals(Action.CREATE, tuple.action, stage)
      assertEquals(Version.HEAD.number, tuple.nextVersion, stage)
      assertContentEquals(original.featureBytes, tuple.featureBytes, stage)
      val id = original.id.text
      assertEquals("name-$id", tuple.getString(Member("name")), stage)
      assertEquals(42L, Int64Member("count").get(tuple), stage)
      val feature = assertNotNull(reference.feature, "$stage full feature decode")
      assertEquals(id, feature.id, stage)
      assertEquals(id, reference.id, stage)
      assertEquals("name-$id", feature.properties.getRaw("name"), stage)
      assertEquals(42L, (feature.properties.getRaw("count") as Number).toLong(), stage)
      assertEquals("payload-$id", feature.getRaw("payload"), stage)
      assertContentEquals(listOf("read-flow", "id:$id"), feature.properties.xyz.tags, stage)
    }
  }
}
