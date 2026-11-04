package naksha.psql

import java.sql.DriverManager
import naksha.base.Action
import naksha.base.Id
import naksha.base.NakshaError
import naksha.base.NakshaException
import naksha.base.Platform
import naksha.base.TupleNumber
import naksha.base.Version
import naksha.model.Naksha
import naksha.model.NewTuple
import naksha.model.TagList
import naksha.model.Tuple
import naksha.model.objects.IndexList
import naksha.model.objects.Member
import naksha.model.objects.MemberList
import naksha.model.objects.MemberType
import naksha.model.objects.NakshaCatalog
import naksha.model.objects.NakshaCollection
import naksha.model.objects.NakshaFeature
import naksha.model.objects.StoreMode
import org.junit.jupiter.api.Assertions.assertAll
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.junit.jupiter.api.function.Executable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@EnabledIfEnvironmentVariable(named = "NAKSHA_TEST_UPDATE_DB_URL", matches = ".+")
class PgDatabaseUpdateUpsertTest {
  @Test
  fun updateAndUpsertThroughTheRoot() {
    val url = System.getenv("NAKSHA_TEST_UPDATE_DB_URL")
    assertEquals(url, System.getenv("NAKSHA_TEST_PSQL_DB_URL"),
      "Update tests and storage bootstrap must use the same fresh dedicated database")
    DriverManager.getConnection(url).use { jdbc ->
      jdbc.createStatement().use { statement ->
        statement.executeQuery("SELECT 1 FROM pg_namespace WHERE nspname = 'naksha~admin'").use { rows ->
          assertFalse(rows.next(), "Requires a fresh disposable database; existing schemas are never cleared")
        }
      }
    }
    val config = PgConfig("database_update_test").apply {
      className = "naksha.psql.PgStorage"
      master = PgInstanceConfig.fromUri(url)
    }
    val storage = PgStorage(config, true, false)
    assertTrue(storage.initialized)
    val database = storage.database
    val catalog = PgCatalog(Tuple.encodeObject(
      NakshaCatalog(storage.id, "update_test"), database.pgAdminCatalogs.naksha, 1L
    ).tuple)
    storage.newConnection(Naksha.useAdminOptions(), false, PgStorage.SKIP_INIT).use { conn ->
      conn.autoCommit = false
      try {
        conn.execute("SET LOCAL lock_timeout = '5s'; SET LOCAL statement_timeout = '120s'").close()
        assertTrue(database.catalog_get_or_create_schema(conn, catalog.name, catalog.id.text) > 0)
        val history = collection(storage, conn, catalog, true)
        val current = collection(storage, conn, catalog, false)
        val version = PgPartitions.currentVersion(conn) + 4L
        assertAll("rows_update/rows_upsert live contract",
          Executable { isolated(conn) { updateOrdinary(database, conn, history, version) } },
          Executable { isolated(conn) { updateOrdinary(database, conn, current, version) } },
          Executable { isolated(conn) { updateMissingAndDeleted(database, conn, history, version) } },
          Executable { isolated(conn) { updateAtomic(database, conn, history, version) } },
          Executable { isolated(conn) { upsertBranches(database, conn, history, version) } },
          Executable { isolated(conn) { upsertBranches(database, conn, current, version) } }
        )
        assertFalse(conn.isClosed())
        assertFalse(conn.autoCommit)
        conn.rollback()
        assertEquals("0", scalar(conn,
          "SELECT count(*) FROM pg_namespace WHERE nspname = ${PgUtil.quoteLiteral(catalog.name)}"),
          "Update/upsert and fixture operations must not commit the caller's transaction")
      } finally {
        conn.rollback()
      }
    }
  }

  private fun collection(storage: PgStorage, conn: PgConnection, catalog: PgCatalog, history: Boolean): PgCollection {
    val database = storage.database
    val definition = NakshaCollection(if (history) "features_history" else "features_current", catalog.id.text).apply {
      databaseId = storage.id.text
      members = MemberList(Member("name"), Member("tags", MemberType.TAG_LIST))
      indices = IndexList()
      shift = 12
      setRaw("storeHistory", if (history) StoreMode.ON.text else StoreMode.OFF.text)
    }
    PgCollection.resolveLayout(definition)
    val descriptor = PgCollection(catalog.id, Tuple.encodeRaw(
      definition, database.id.number, catalog.id.intValue, Id.COLLECTIONS_COL_ID.intValue,
      database.pgAdminCollections.naksha.validatedMembers, 1L, requestedAction = Action.CREATE
    ).tuple)
    assertEquals(history, descriptor.storeHistory)
    database.collection_create_table_if_not_exists(
      conn, catalog.name, descriptor.name, descriptor.columns, descriptor.indices,
      descriptor.creationOptions(storage.config, database.maxTupleSize), catalog.id.text, descriptor.id.text
    )
    return descriptor
  }

  private fun encode(collection: PgCollection, id: String, version: Long, name: String? = "payload",
    tags: TagList? = TagList("a", "b"), action: Action = Action.CREATE,
    databaseNumber: Long = collection.tupleNumber.databaseNumber,
    catalogNumber: Int = collection.catalogId.intValue,
    collectionNumber: Int = collection.id.intValue): NewTuple {
    val feature = NakshaFeature(id).apply {
      properties.setRaw("name", name)
      properties.setRaw("tags", tags)
    }
    return Tuple.encodeRaw(feature, databaseNumber, catalogNumber, collectionNumber,
      collection.naksha.validatedMembers, version, requestedAction = action)
  }

  private fun update(database: PgDatabase, conn: PgConnection, collection: PgCollection, inputs: List<NewTuple>): List<NewTuple> =
    database.rows_update(conn, collection.catalogId, collection.id, inputs, collection.storeHistory, collection.columns)

  private fun upsert(database: PgDatabase, conn: PgConnection, collection: PgCollection, inputs: List<NewTuple>): List<NewTuple> =
    database.rows_upsert(conn, collection.catalogId, collection.id, inputs, collection.storeHistory, collection.columns)

  private fun seed(database: PgDatabase, conn: PgConnection, collection: PgCollection, inputs: List<NewTuple>) {
    database.rows_insert(conn, collection.catalogId, collection.id, inputs, collection.storeHistory, collection.columns)
  }

  private fun updateOrdinary(database: PgDatabase, conn: PgConnection, collection: PgCollection, version: Long) {
    val created = encode(collection, "upd", version)
    seed(database, conn, collection, listOf(created))
    val incoming = encode(collection, "upd", version + 4L, name = "updated", tags = TagList("c"), action = Action.UPDATE)
    val result = update(database, conn, collection, listOf(incoming))
    assertEquals(1, result.size)
    val final = result.single()
    assertSame(incoming.tuple, final.tuple, "UPDATE returns the supplied new state")
    assertEquals(Action.UPDATE, final.tuple.tupleNumber.action)
    assertEquals(created.tuple.tupleNumber.version, final.previousTupleNumber?.version, "predecessor is the replaced live HEAD")
    val states = read(conn, collection, incoming.tuple.id)
    val head = states.single { it.nextVersion == Version.HEAD.number }
    assertEquals(incoming.tuple.tupleNumber, head.tupleNumber)
    assertEquals("updated", head.getString(Member("name")))
    if (collection.storeHistory) {
      val archived = states.single { it.nextVersion != Version.HEAD.number }
      assertEquals(created.tuple.tupleNumber, archived.tupleNumber)
      assertEquals(incoming.tuple.tupleNumber.version, archived.nextVersion, "archived HEAD ends at the new version")
      assertEquals("payload", archived.getString(Member("name")))
    } else {
      assertEquals(1, states.size, "history OFF replaces the HEAD in place")
    }
  }

  private fun updateMissingAndDeleted(database: PgDatabase, conn: PgConnection, collection: PgCollection, version: Long) {
    val missing = encode(collection, "absent", version, action = Action.UPDATE)
    val missingError = assertFailsWith<NakshaException> { update(database, conn, collection, listOf(missing)) }.error
    assertEquals(NakshaError.FEATURE_NOT_FOUND, missingError.code)
    assertEquals(missing.tuple.id.text, missingError.id)

    val tombstone = encode(collection, "gone", version, name = "old", tags = TagList("old"), action = Action.DELETE).tuple
    val rows = PgRows().withCollection(collection)
    rows.size = 1
    rows.setRow(0, tombstone)
    conn.prepare("INSERT INTO ${collection.qualifiedName} (${rows.aliases()}) " +
      "SELECT ${rows.decodedColumns()} FROM UNNEST(${rows.placeholders()}) AS t(${rows.aliases()})",
      rows.typeNames()).use { plan -> plan.execute(rows.values()).close() }
    val deleted = encode(collection, "gone", version + 4L, name = "nope", action = Action.UPDATE)
    val deletedError = assertFailsWith<NakshaException> { update(database, conn, collection, listOf(deleted)) }.error
    assertEquals(NakshaError.FEATURE_NOT_FOUND, deletedError.code, "a DELETE HEAD has no live state to update")
  }

  private fun updateAtomic(database: PgDatabase, conn: PgConnection, collection: PgCollection, version: Long) {
    val created = encode(collection, "atomic", version)
    seed(database, conn, collection, listOf(created))
    val wrong = encode(collection, "atomic", version + 4L, name = "conflict", action = Action.UPDATE)
    val wrongPrev = TupleNumber.copy(wrong.tuple.tupleNumber, version = version + 8L)
    val conflict = assertFailsWith<NakshaException> {
      update(database, conn, collection, listOf(NewTuple(wrong.tuple, wrongPrev)))
    }.error
    assertEquals(NakshaError.CONFLICT, conflict.code)
    assertEquals(wrongPrev.version, conflict.expectedVersion)
    assertEquals(created.tuple.tupleNumber.version, conflict.actualVersion)
    assertEquals(created.tuple.tupleNumber, read(conn, collection, wrong.tuple.id).single().tupleNumber,
      "a failed atomic update leaves the HEAD unchanged")
    val rightPrev = TupleNumber.copy(wrong.tuple.tupleNumber, version = created.tuple.tupleNumber.version)
    val ok = update(database, conn, collection, listOf(NewTuple(wrong.tuple, rightPrev))).single()
    assertEquals(created.tuple.tupleNumber.version, ok.previousTupleNumber?.version)
    val head = read(conn, collection, wrong.tuple.id).single { it.nextVersion == Version.HEAD.number }
    assertEquals(wrong.tuple.tupleNumber, head.tupleNumber)
  }

  private fun upsertBranches(database: PgDatabase, conn: PgConnection, collection: PgCollection, version: Long) {
    // Insert into an absent HEAD.
    val inserted = upsert(database, conn, collection, listOf(encode(collection, "new", version))).single()
    assertNull(inserted.previousTupleNumber, "an inserted HEAD has no predecessor")
    assertEquals(Action.CREATE, inserted.tuple.tupleNumber.action)

    // Update a live HEAD; the CREATE-encoded input is stored and returned with the UPDATE action.
    val live = read(conn, collection, inserted.tuple.id).single()
    val updated = upsert(database, conn, collection, listOf(encode(collection, "new", version + 4L, name = "u"))).single()
    assertEquals(live.tupleNumber.version, updated.previousTupleNumber?.version, "a live HEAD sets the predecessor")
    assertEquals(Action.UPDATE, updated.tuple.tupleNumber.action, "the UPDATE branch flips the action bit")
    val states = read(conn, collection, updated.tuple.id)
    val head = states.single { it.nextVersion == Version.HEAD.number }
    assertEquals(updated.tuple.tupleNumber, head.tupleNumber)
    if (collection.storeHistory) {
      val archived = states.single { it.nextVersion != Version.HEAD.number }
      assertEquals(live.tupleNumber.version, archived.tupleNumber.version)
    }

    // Re-create over a DELETE HEAD (a fresh feature holding only a tombstone).
    val tombstone = encode(collection, "recreate", version + 8L, name = "tomb", tags = TagList("t"), action = Action.DELETE).tuple
    val rows = PgRows().withCollection(collection)
    rows.size = 1
    rows.setRow(0, tombstone)
    conn.prepare("INSERT INTO ${collection.qualifiedName} (${rows.aliases()}) " +
      "SELECT ${rows.decodedColumns()} FROM UNNEST(${rows.placeholders()}) AS t(${rows.aliases()})",
      rows.typeNames()).use { plan -> plan.execute(rows.values()).close() }
    val recreated = upsert(database, conn, collection, listOf(encode(collection, "recreate", version + 12L, name = "re"))).single()
    assertNull(recreated.previousTupleNumber, "re-creation over a DELETE HEAD is an INSERT")
    assertEquals(Action.CREATE, recreated.tuple.tupleNumber.action)

    // Atomic upsert on a live HEAD rejects a mismatching predecessor.
    val conflictInput = encode(collection, "new", version + 16L, name = "x")
    val conflictPrev = TupleNumber.copy(conflictInput.tuple.tupleNumber, version = version + 16L)
    val conflict = assertFailsWith<NakshaException> {
      upsert(database, conn, collection, listOf(NewTuple(conflictInput.tuple, conflictPrev)))
    }.error
    assertEquals(NakshaError.CONFLICT, conflict.code)
  }

  private fun read(conn: PgConnection, collection: PgCollection, id: Id): List<Tuple> {
    val rows = PgRows().withCollection(collection)
    conn.execute("SELECT * FROM ${collection.qualifiedName} WHERE fn = ${Id.featureNumberToOrdinal(id.number)}")
      .fetch().use { cursor -> rows.readAll(cursor) }
    return List(rows.size) { assertNotNull(rows.getTuple(it)) }
  }

  private fun isolated(conn: PgConnection, test: () -> Unit) {
    val savepoint = conn.jdbc.setSavepoint()
    try {
      test()
    } finally {
      conn.jdbc.rollback(savepoint)
      conn.jdbc.releaseSavepoint(savepoint)
    }
  }

  private fun scalar(conn: PgConnection, sql: String): String? {
    conn.jdbc.createStatement().use { statement ->
      statement.executeQuery(sql).use { rows ->
        assertTrue(rows.next())
        val value = rows.getString(1)
        assertFalse(rows.next())
        return value
      }
    }
  }
}
