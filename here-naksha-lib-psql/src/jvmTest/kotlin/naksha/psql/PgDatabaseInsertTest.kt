package naksha.psql

import java.sql.DriverManager
import naksha.base.Action
import naksha.base.Id
import naksha.base.NakshaError
import naksha.base.NakshaException
import naksha.base.Platform
import naksha.base.Version
import naksha.base.toOrdinalFeatureNumber
import naksha.jbon.HeapBook
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
import naksha.model.objects.StandardMembers
import naksha.model.objects.StoreMode
import org.junit.jupiter.api.Assertions.assertAll
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.junit.jupiter.api.function.Executable
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@EnabledIfEnvironmentVariable(named = "NAKSHA_TEST_INSERT_DB_URL", matches = ".+")
class PgDatabaseInsertTest {
  @Test
  fun insertsValidatesRecreatesAndLeavesRollbackToCaller() {
    val url = System.getenv("NAKSHA_TEST_INSERT_DB_URL")
    assertEquals(url, System.getenv("NAKSHA_TEST_PSQL_DB_URL"),
      "Insert tests and storage bootstrap must use the same fresh dedicated database")
    DriverManager.getConnection(url).use { jdbc ->
      jdbc.createStatement().use { statement ->
        statement.executeQuery("SELECT 1 FROM pg_namespace WHERE nspname = 'naksha~admin'").use { rows ->
          assertFalse(rows.next(), "Requires a fresh disposable database; existing schemas are never cleared")
        }
      }
    }
    val config = PgConfig("database_insert_test").apply {
      className = "naksha.psql.PgStorage"
      master = PgInstanceConfig.fromUri(url)
    }
    val storage = PgStorage(config, true, false)
    assertTrue(storage.initialized)
    val database = storage.database
    val catalog = PgCatalog(Tuple.encodeObject(
      NakshaCatalog(storage.id, "insert_test"), database.pgAdminCatalogs.naksha, 1L
    ).tuple)
    storage.newConnection(Naksha.useAdminOptions(), false, PgStorage.SKIP_INIT).use { conn ->
      conn.autoCommit = false
      try {
        conn.execute("SET LOCAL lock_timeout = '5s'; SET LOCAL statement_timeout = '120s'").close()
        assertTrue(database.catalog_get_or_create_schema(conn, catalog.name, catalog.id.text) > 0)
        val history = collection(storage, conn, catalog, true)
        val current = collection(storage, conn, catalog, false)
        val version = PgPartitions.currentVersion(conn) + 4L
        database.collection_maintain_partitions(conn, catalog.name, history.name, version)
        assertAll("rows_insert live contract",
          Executable { isolated(conn) { ordinaryAndDuplicate(database, conn, history, version) } },
          Executable { isolated(conn) { rejectsWholeBatch(database, conn, history, version) } },
          Executable { isolated(conn) { recreates(database, conn, history, version) } },
          Executable { isolated(conn) { recreates(database, conn, current, version) } },
          Executable { isolated(conn) { callerRollback(database, conn, history, version) } }
        )
        assertFalse(conn.isClosed())
        assertFalse(conn.autoCommit)
        conn.rollback()
        assertEquals("0", scalar(conn,
          "SELECT count(*) FROM pg_namespace WHERE nspname = ${PgUtil.quoteLiteral(catalog.name)}"),
          "Insert and fixture operations must not commit the caller's transaction")
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
    // Collection metadata belongs to its own catalog, not the admin catalog providing its member schema.
    val descriptor = PgCollection(catalog.id, Tuple.encodeRaw(
      definition, database.id.number, catalog.id.intValue, Id.COLLECTIONS_COL_ID.intValue,
      database.pgAdminCollections.naksha.validatedMembers, 1L, requestedAction = Action.CREATE
    ).tuple)
    assertEquals(history, descriptor.storeHistory)
    assertEquals(database.id.number, descriptor.tupleNumber.databaseNumber)
    assertEquals(catalog.id.intValue, descriptor.tupleNumber.catalogNumber)
    assertEquals(storage.id.text, descriptor.naksha.databaseId)
    assertEquals(catalog.id.text, descriptor.naksha.catalogId)
    database.collection_create_table_if_not_exists(
      conn, catalog.name, descriptor.name, descriptor.columns, descriptor.indices,
      descriptor.creationOptions(storage.config, database.maxTupleSize), catalog.id.text, descriptor.id.text
    )
    return descriptor
  }

  private fun encode(collection: PgCollection, id: String, version: Long, name: String? = "new payload",
    tags: TagList? = TagList("new", "complete"), action: Action = Action.CREATE,
    databaseNumber: Long = collection.tupleNumber.databaseNumber,
    catalogNumber: Int = collection.catalogId.intValue,
    collectionNumber: Int = collection.id.intValue): NewTuple {
    val feature = NakshaFeature(id).apply {
      properties.setRaw("name", name)
      properties.setRaw("tags", tags)
      setRaw("lifecycle", if (action == Action.DELETE) "old" else "new")
      if (action == Action.DELETE) setRaw("oldOnly", "must not survive recreation")
    }
    return Tuple.encodeRaw(feature, databaseNumber, catalogNumber, collectionNumber,
      collection.naksha.validatedMembers, version, requestedAction = action)
  }

  private fun insert(database: PgDatabase, conn: PgConnection, collection: PgCollection, inputs: List<NewTuple>): List<Tuple> =
    database.rows_insert(conn, collection.catalogId, collection.id, inputs, collection.storeHistory, collection.columns)

  private fun ordinaryAndDuplicate(database: PgDatabase, conn: PgConnection, collection: PgCollection, version: Long) {
    assertTrue(insert(database, conn, collection, emptyList()).isEmpty())
    val inputs = listOf(encode(collection, "hashed-feature", version), encode(collection, "42", version))
    val before = snapshots(inputs)
    val result = insert(database, conn, collection, inputs)
    assertEquals(inputs.size, result.size)
    for (i in inputs.indices) {
      val expected = inputs[i].tuple
      assertSame(expected, result[i], "Returned tuple reference and order must match input")
      assertTuple(expected, read(conn, collection, expected.id).single())
      val ordinal = expected.tupleNumber.featureNumber.toOrdinalFeatureNumber()
      assertEquals(ordinal.toString(), scalar(conn,
        "SELECT fn::text FROM ${collection.qualifiedName} WHERE nv = ${Version.HEAD.number} AND fn = $ordinal"))
      val idColumn = assertNotNull(collection.column(StandardMembers.Id)).ident
      assertEquals(if (expected.tupleNumber.featureNumber < 0) expected.id.text else null, scalar(conn,
        "SELECT $idColumn FROM ${collection.qualifiedName} WHERE fn = $ordinal"))
    }
    assertUnchanged(inputs, before)
    val duplicate = encode(collection, "hashed-feature", version + 4L, "must not replace")
    val duplicateBefore = snapshots(listOf(duplicate))
    val error = assertFailsWith<NakshaException> { insert(database, conn, collection, listOf(duplicate)) }.error
    assertConflict(database, collection, duplicate, inputs[0].tuple.version, error)
    assertTuple(inputs[0].tuple, read(conn, collection, inputs[0].tuple.id).single())
    assertUnchanged(listOf(duplicate), duplicateBefore)
  }

  private fun rejectsWholeBatch(database: PgDatabase, conn: PgConnection, collection: PgCollection, version: Long) {
    val valid = encode(collection, "first-valid", version)
    val invalid = encode(collection, "invalid", version)
    val nonHeadBook = HeapBook.copyOf(invalid.tuple.membersBook).apply {
      put(StandardMembers.NextVersion.name, version + 8L)
    }
    val cases = listOf(
      NewTuple(invalid.tuple, invalid.tuple.tupleNumber),
      encode(collection, "wrong-database", version, databaseNumber = database.id.number + 1L),
      encode(collection, "wrong-catalog", version, catalogNumber = Id("another_catalog").intValue),
      encode(collection, "wrong-collection", version, collectionNumber = Id("another_collection").intValue),
      encode(collection, "delete-action", version, action = Action.DELETE),
      NewTuple(Tuple(invalid.tuple.featureBytes.copyOf(), nonHeadBook))
    )
    for (bad in cases) {
      val inputs = listOf(valid, bad)
      val before = snapshots(inputs)
      val error = assertFailsWith<NakshaException> { insert(database, conn, collection, inputs) }.error
      assertEquals(NakshaError.ILLEGAL_ARGUMENT, error.code)
      assertEquals("0", scalar(conn, "SELECT count(*) FROM ${collection.qualifiedName}"),
        "The entire batch must be validated before inserting its first valid tuple")
      assertUnchanged(inputs, before)
    }
  }

  private fun recreates(database: PgDatabase, conn: PgConnection, collection: PgCollection, version: Long) {
    val tombstone = encode(collection, "recreated", version, "old payload", TagList("old", "retained"), Action.DELETE).tuple
    val beforeTombstone = snapshots(listOf(NewTuple(tombstone)))
    val rows = PgRows().withCollection(collection)
    rows.size = 1
    rows.setRow(0, tombstone)
    conn.prepare("INSERT INTO ${collection.qualifiedName} (${rows.aliases()}) " +
      "SELECT ${rows.decodedColumns()} FROM UNNEST(${rows.placeholders()}) AS t(${rows.aliases()})",
      rows.typeNames()).use { plan -> plan.execute(rows.values()).close() }
    assertTuple(tombstone, read(conn, collection, tombstone.id).single())
    // Missing extracted and unextracted old properties must not be merged into the new lifecycle.
    val incoming = encode(collection, tombstone.id.text, version + 4L, name = null, tags = null)
    val before = snapshots(listOf(incoming))
    assertSame(incoming.tuple, insert(database, conn, collection, listOf(incoming)).single())
    val states = read(conn, collection, tombstone.id)
    assertEquals(if (collection.storeHistory) 2 else 1, states.size)
    val head = states.single { it.nextVersion == Version.HEAD.number }
    assertTuple(incoming.tuple, head)
    assertNull(head.getString(Member("name")))
    assertNull(head.membersBook["tags"])
    assertEquals("new", head.decodeFeature(null).getRaw("lifecycle"))
    assertNull(head.decodeFeature(null).getRaw("oldOnly"))
    if (collection.storeHistory) {
      val archived = states.single { it.nextVersion != Version.HEAD.number }
      assertTuple(tombstone, archived, incoming.tuple.version)
      assertEquals("old payload", archived.getString(Member("name")))
      assertEquals("old", archived.decodeFeature(null).getRaw("lifecycle"))
      assertEquals("must not survive recreation", archived.decodeFeature(null).getRaw("oldOnly"))
    }
    assertUnchanged(listOf(incoming), before)
    assertUnchanged(listOf(NewTuple(tombstone)), beforeTombstone)
  }

  private fun callerRollback(database: PgDatabase, conn: PgConnection, collection: PgCollection, version: Long) {
    val live = encode(collection, "existing", version)
    insert(database, conn, collection, listOf(live))
    val inputs = listOf(encode(collection, "earlier", version + 4L), encode(collection, "existing", version + 4L))
    val before = snapshots(inputs)
    val savepoint = conn.jdbc.setSavepoint()
    try {
      val error = assertFailsWith<NakshaException> { insert(database, conn, collection, inputs) }.error
      assertConflict(database, collection, inputs[1], live.tuple.version, error)
      assertTuple(inputs[0].tuple, read(conn, collection, inputs[0].tuple.id).single(),
        message = "The earlier insert remains pending until the caller rolls back")
      assertFalse(conn.isClosed())
      assertFalse(conn.autoCommit)
    } finally {
      conn.jdbc.rollback(savepoint)
      conn.jdbc.releaseSavepoint(savepoint)
    }
    assertTrue(read(conn, collection, inputs[0].tuple.id).isEmpty())
    assertTuple(live.tuple, read(conn, collection, live.tuple.id).single())
    assertUnchanged(inputs, before)
  }

  private fun assertConflict(database: PgDatabase, collection: PgCollection, incoming: NewTuple,
    actualVersion: Long, error: NakshaError) {
    assertEquals(NakshaError.FEATURE_EXISTS, error.code)
    assertEquals(database.id.text, error.database)
    assertEquals(collection.catalogId.text, error.catalog)
    assertEquals(collection.id.text, error.collection)
    assertEquals(incoming.tuple.id.text, error.id)
    assertEquals(actualVersion, error.actualVersion)
  }

  private fun read(conn: PgConnection, collection: PgCollection, id: Id): List<Tuple> {
    val rows = PgRows().withCollection(collection)
    conn.execute("SELECT * FROM ${collection.qualifiedName} WHERE fn = ${id.number.toOrdinalFeatureNumber()}")
      .fetch().use { cursor -> rows.readAll(cursor) }
    return List(rows.size) { assertNotNull(rows.getTuple(it)) }
  }

  private fun assertTuple(expected: Tuple, actual: Tuple, nextVersion: Long = Version.HEAD.number, message: String? = null) {
    assertEquals(expected.tupleNumber, actual.tupleNumber, message)
    assertEquals(expected.id, actual.id)
    assertEquals(nextVersion, actual.nextVersion)
    assertContentEquals(expected.featureBytes, actual.featureBytes)
    val book = expected.membersBook
    for (i in 0 until book.namesLength()) {
      val name = assertNotNull(book.getNameAt(i))
      if (name == StandardMembers.NextVersion.name || name == StandardMembers.Id.name) continue
      assertEquals(Platform.toJSON(book[name]), Platform.toJSON(actual.membersBook[name]), name)
    }
    // Numeric ids are deliberately represented by SQL NULL in the id column.
    val expectedFeature = expected.decodeFeature(null)
    val actualFeature = actual.decodeFeature(null)
    assertEquals(expectedFeature.properties.getRaw("name"), actualFeature.properties.getRaw("name"))
    assertEquals(expectedFeature.getRaw("lifecycle"), actualFeature.getRaw("lifecycle"))
    assertEquals(expectedFeature.getRaw("oldOnly"), actualFeature.getRaw("oldOnly"))
  }

  private fun snapshots(inputs: List<NewTuple>): List<Triple<ByteArray, String, String>> = inputs.map {
    Triple(it.tuple.featureBytes.copyOf(), Platform.toJSON(it.tuple.decodeFeature(null)), memberSnapshot(it))
  }

  private fun memberSnapshot(input: NewTuple): String {
    val book = input.tuple.membersBook
    val values = ArrayList<String?>()
    values.add(input.previousTupleNumber?.toString())
    for (i in 0 until book.namesLength()) {
      values.add(book.getNameAt(i))
      values.add(Platform.toJSON(book[i]))
    }
    return values.toString()
  }

  private fun assertUnchanged(inputs: List<NewTuple>, before: List<Triple<ByteArray, String, String>>) {
    for (i in inputs.indices) {
      assertContentEquals(before[i].first, inputs[i].tuple.featureBytes)
      assertEquals(before[i].second, Platform.toJSON(inputs[i].tuple.decodeFeature(null)))
      assertEquals(before[i].third, memberSnapshot(inputs[i]))
    }
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
