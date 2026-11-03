package naksha.psql

import naksha.base.Id
import naksha.base.NakshaException
import naksha.base.Version
import naksha.model.Naksha
import naksha.model.objects.Member
import naksha.model.objects.MemberType
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.mockito.Mockito
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PgDatabasePartmanTest {
  private val database = PgDatabaseImpl(Mockito.mock(PgStorage::class.java), Id(1L))
  private val payload = PgColumn(3, Member("payload", MemberType.STRING), "STORAGE MAIN COLLATE \"C\"")
  private val id = PgColumn(4, Member("id", MemberType.STRING), "STORAGE PLAIN COLLATE \"C\"")
  private val columns = arrayOf(PgColumn.FnColumn, PgColumn.VersionColumn, PgColumn.NextVersionColumn, payload, id)
  private val logicalCatalog = "Logical \"Catalog"
  private val logicalCollection = "Logical.Collection"
  private val head = Version.HEAD.number
  private val registry = "\"naksha~admin\".\"naksha_partman\""

  @Test
  fun createsHistoryOnAndOffWithSevenAndOnePartitions() = withDatabase { conn, schema ->
    for (history in listOf(true, false)) {
      for (partitions in listOf(7, 1)) {
        val table = "root_${history}_$partitions"
        create(conn, schema, table, options(history, partitions))
        val root = relation(schema, table)
        assertEquals((if (history) 5 else 1).toString(), scalar(conn,
          "SELECT count(*) FROM pg_partition_tree(${literal(root)}::regclass) WHERE level = 1"))
        assertLeafCount(conn, root, (if (history) 5 else 1) * partitions)
        assertEquals(if (history) "1" else "0", scalar(conn,
          "SELECT count(*) FROM $registry WHERE schema_name = ${literal(schema)} AND table_name = ${literal(table)}"))
        assertRootContract(conn, schema, table)
        assertStorageAndInheritance(conn, root)

        val before = snapshot(conn, schema)
        create(conn, schema, table, options(!history, if (partitions == 7) 1 else 7),
          arrayOf(PgColumn.FnColumn, PgColumn.VersionColumn, PgColumn.NextVersionColumn))
        assertEquals(before, snapshot(conn, schema), "Same logical owner must leave existing objects unchanged")
        expectFailure(conn) {
          create(conn, schema, table, options(history, partitions), owner = "Different owner")
        }
        assertEquals(before, snapshot(conn, schema))
        if (!history) {
          expectFailure(conn) { insert(conn, schema, table, 17, 0, 0) }
          assertEquals("0", scalar(conn, "SELECT count(*) FROM $root"))
        }
      }
    }
  }

  @Test
  fun routesBigintBoundariesAndEnforcesHeadUniqueness() = withDatabase { conn, schema ->
    for (partitions in listOf(7, 1)) {
      val table = "routing_$partitions"
      create(conn, schema, table, options(true, partitions))
      database.collection_maintain_partitions(conn, schema, table, 0L)
      assertLeafCount(conn, relation(schema, table), 9 * partitions)
      assertRouting(conn, schema, table, partitions, 12, 0L)
      expectFailure(conn) { insert(conn, schema, table, Long.MIN_VALUE, 4L, head) }
      assertEquals("1", scalar(conn,
        "SELECT count(*) FROM ${relation(schema, table)} WHERE fn = ${Long.MIN_VALUE} AND nv = $head"))
      // The mandatory unique key also prevents two versions of the same finite state.
      expectFailure(conn) { insert(conn, schema, table, Long.MIN_VALUE, 4L, 0L) }
      for (nv in listOf(-1L, 16384L, head + 1)) {
        expectFailure(conn) { insert(conn, schema, table, 23L, 0L, nv) }
      }
      expectFailure(conn) {
        conn.execute("INSERT INTO ${relation(schema, table)} (fn, version, nv) VALUES (NULL, 0, $head)").close()
      }
      assertStorageAndInheritance(conn, relation(schema, table))
    }
  }

  @Test
  fun maintainsImportedRangesRepairsFutureLeavesAndInheritsRootIndexes() = withDatabase { conn, schema ->
    val table = "repair"
    val root = relation(schema, table)
    create(conn, schema, table, options(true, 7))
    database.collection_maintain_partitions(conn, schema, table, 0L)
    val index = PgIndex("payload_lookup", arrayOf(payload to PgOrder.ASC),
      includes = arrayOf(PgColumn.FnColumn, PgColumn.NextVersionColumn, PgColumn.VersionColumn))
    database.collection_create_index(conn, schema, table, index)
    assertStorageAndInheritance(conn, root)
    val missing = relation(schema, table + "\$3\$6")
    conn.execute("DROP TABLE $missing").close()
    assertNull(scalar(conn, "SELECT to_regclass(${literal(missing)})"))

    val imported = 40960000000000L
    database.collection_maintain_partitions(conn, schema, table, imported)
    assertTrue(scalar(conn, "SELECT to_regclass(${literal(missing)})") != null)
    assertLeafCount(conn, root, 13 * 7)
    assertRouting(conn, schema, table, 7, 12, imported)
    assertStorageAndInheritance(conn, root)
    val before = snapshot(conn, schema)
    database.collection_register_partitions(conn, schema, table, 12, 7, 2048, 50, 100, null)
    database.collection_maintain_partitions(conn, schema, table, imported)
    assertEquals(before, snapshot(conn, schema))

    val indexRoot = relation(schema, index.id.indexName(table))
    val indexOids = scalar(conn, """
      WITH RECURSIVE tree AS (
        SELECT ${literal(indexRoot)}::regclass::oid AS oid
        UNION ALL SELECT h.inhrelid FROM pg_inherits h JOIN tree t ON t.oid = h.inhparent
      ) SELECT string_agg(oid::text, ',') FROM tree
    """)!!
    database.collection_drop_index(conn, schema, table, index)
    assertEquals("0", scalar(conn, "SELECT count(*) FROM pg_class WHERE oid IN ($indexOids)"))
    database.collection_maintain_partitions(conn, schema, table, imported + (4L shl 12))
    assertEquals("0", scalar(conn,
      "SELECT count(*) FROM pg_index i JOIN pg_partition_tree(${literal(root)}::regclass) t ON t.relid = i.indrelid WHERE i.indnkeyatts = 1 AND pg_get_indexdef(i.indexrelid, 1, true) LIKE 'payload%'"))
    assertLeafCount(conn, root, 17 * 7)
    assertStorageAndInheritance(conn, root)
  }

  @Test
  fun rejectsInvalidCreationAndRollsBackPartialMaintenance() = withDatabase { conn, schema ->
    for (storage in listOf(PgStorageClass.Ephemeral, PgStorageClass.Brittle, PgStorageClass.Temporary)) {
      // Do not recover with a savepoint here: validation must fail before any DDL or SQL error.
      val before = snapshot(conn, schema)
      assertFailsWith<NakshaException> {
        create(conn, schema, "invalid_storage", options(true, 7).copy(storageClass = storage))
      }
      assertEquals(before, snapshot(conn, schema))
    }
    val before = snapshot(conn, schema)
    assertFailsWith<NakshaException> {
      create(conn, schema, "duplicate_columns", options(true, 7), columns + PgColumn(4, payload.member))
    }
    assertEquals(before, snapshot(conn, schema))

    val table = "partial"
    create(conn, schema, table, options(false, 1))
    database.collection_register_partitions(conn, schema, table, 12, 1, 2048, 50, 100, null)
    conn.execute("CREATE TABLE ${relation(schema, table + "\$2")} (unrelated text)").close()
    val registered = snapshot(conn, schema)
    // The conflict is encountered after ranges 0 and 1 can have been created.
    // Recovery belongs to the caller, not to the maintainer.
    expectFailure(conn) { database.collection_maintain_partitions(conn, schema, table, 0L) }
    assertEquals(registered, snapshot(conn, schema))
    assertLeafCount(conn, relation(schema, table), 1)
    conn.execute("DROP TABLE ${relation(schema, table + "\$2")}").close()
    database.collection_maintain_partitions(conn, schema, table, 0L)
    assertLeafCount(conn, relation(schema, table), 5)
    conn.execute("DROP TABLE ${relation(schema, table)} CASCADE").close()
    expectFailure(conn) { database.collection_maintain_partitions(conn, schema, table, 0L) }
    assertNull(scalar(conn, "SELECT to_regclass(${literal(relation(schema, table))})"))
  }

  @Test
  fun clipsShiftFortyOneAtHeadWithTwoHundredFiftySixPartitions() = withDatabase { conn, schema ->
    val table = "clipped"
    // HEAD-only creation avoids unrelated clock-selected finite ranges in this boundary case.
    create(conn, schema, table, options(false, 256).copy(shift = 41))
    assertLeafCount(conn, relation(schema, table), 256)
    database.collection_register_partitions(conn, schema, table, 41, 256, 2048, 50, 100, null)
    database.collection_maintain_partitions(conn, schema, table, head - 1)
    assertEquals("2", scalar(conn,
      "SELECT count(*) FROM pg_partition_tree(${literal(relation(schema, table))}::regclass) WHERE level = 1"))
    assertLeafCount(conn, relation(schema, table), 512)
    assertEquals("FOR VALUES FROM ('9005000231485440') TO ('9007199254740991')", scalar(conn,
      "SELECT pg_get_expr(relpartbound, oid) FROM pg_class WHERE oid = ${literal(relation(schema, table + "\$4095"))}::regclass"))
    assertRouting(conn, schema, table, 256, 41, head - 1)
    database.collection_maintain_partitions(conn, schema, table, head - 1)
    assertLeafCount(conn, relation(schema, table), 512)
    assertStorageAndInheritance(conn, relation(schema, table))
  }

  @Test
  fun initialCoverageUsesAheadAllocatorWithoutAdvancingIt() = withDatabase { conn, schema ->
    val watermark = 9000000000000003L
    conn.execute("CREATE SEQUENCE \"naksha~admin\".naksha_version_seq AS bigint START $watermark INCREMENT BY 4").close()
    create(conn, schema, "watermark", options(true, 1))
    assertTrue(scalar(conn, "SELECT to_regclass(${literal(relation(schema, "watermark\$${watermark shr 12}"))})") != null)
    assertEquals(watermark.toString(), scalar(conn, "SELECT last_value FROM \"naksha~admin\".naksha_version_seq"))
    assertEquals("false", scalar(conn, "SELECT is_called::text FROM \"naksha~admin\".naksha_version_seq"))
  }

  @Test
  fun sweepsAllRegisteredRootsWithoutAllocatingVersions() = withDatabase { conn, schema ->
    assertEquals(0, database.maintain_partitions(conn))
    create(conn, schema, "z_root", options(true, 1))
    create(conn, schema, "a_root", options(true, 3))
    create(conn, schema, "head_only", options(false, 1))
    val watermark = 9000000000000003L
    conn.execute("CREATE SEQUENCE \"naksha~admin\".naksha_version_seq AS bigint START $watermark INCREMENT BY 4").close()
    assertEquals(2, database.maintain_partitions(conn))
    for (table in listOf("a_root", "z_root")) {
      assertTrue(scalar(conn, "SELECT to_regclass(${literal(relation(schema, table + "\$${watermark shr 12}"))})") != null)
    }
    assertLeafCount(conn, relation(schema, "head_only"), 1)
    val before = snapshot(conn, schema)
    assertEquals(2, database.maintain_partitions(conn))
    assertEquals(before, snapshot(conn, schema))
    assertEquals("false", scalar(conn, "SELECT is_called::text FROM \"naksha~admin\".naksha_version_seq"))
  }

  @Test
  fun failedSweepRollsBackEarlierRootsInDeterministicOrder() = withDatabase { conn, schema ->
    create(conn, schema, "a_good", options(false, 1))
    create(conn, schema, "z_bad", options(false, 1))
    for (table in listOf("z_bad", "a_good")) {
      database.collection_register_partitions(conn, schema, table, 12, 1, 2048, 50, 100, null)
    }
    conn.execute("CREATE TABLE ${relation(schema, "z_bad\$2")} (unrelated text)").close()
    val before = snapshot(conn, schema)
    val savepoint = conn.jdbc.setSavepoint()
    try {
      assertFailsWith<NakshaException> { PgPartitions.maintainAll(conn, 0) }
      assertTrue(scalar(conn, "SELECT to_regclass(${literal(relation(schema, "a_good\$0"))})") != null,
        "C-ordered sweep must reach a_good before failing at z_bad; caller owns rollback")
    } finally {
      conn.jdbc.rollback(savepoint)
      conn.jdbc.releaseSavepoint(savepoint)
    }
    assertEquals(before, snapshot(conn, schema))
    conn.execute("DROP TABLE ${relation(schema, "z_bad\$2")}").close()
    assertEquals(2, PgPartitions.maintainAll(conn, 0))
    assertLeafCount(conn, relation(schema, "a_good"), 5)
    assertLeafCount(conn, relation(schema, "z_bad"), 5)
  }

  private fun withDatabase(test: (PgConnection, String) -> Unit) {
    val url = System.getenv("NAKSHA_TEST_PSQL_DB_URL")
    assumeTrue(!url.isNullOrBlank(), "Set NAKSHA_TEST_PSQL_DB_URL to a dedicated clean PostgreSQL test database")
    val instance = PgInstance.of(PgInstanceConfig.fromUri(url!!))
    instance.openConnection(Naksha.useAdminOptions(), false, PgStorage.SKIP_INIT).use { conn ->
      val schema = "Partman Test " + UUID.randomUUID().toString().replace("-", "")
      try {
        conn.autoCommit = false
        conn.execute("SET LOCAL lock_timeout = '5s'; SET LOCAL statement_timeout = '120s'").close()
        assertNull(scalar(conn, "SELECT to_regclass(${literal(registry)})"), "Use a database without a persisted partition registry")
        conn.execute("CREATE SCHEMA IF NOT EXISTS \"naksha~admin\"").close()
        PgPartitions.install(conn)
        assertEquals("0", scalar(conn, """
          SELECT count(*) FROM pg_proc p JOIN pg_namespace n ON n.oid = p.pronamespace
          WHERE n.nspname = 'naksha~admin' AND starts_with(p.proname, 'naksha_partman_')
        """), "Tests must not rely on installed naksha_partman_* server functions")
        assertTrue(database.catalog_get_or_create_schema(conn, schema, logicalCatalog) > 0)
        test(conn, schema)
        assertFalse(conn.isClosed())
        assertFalse(conn.autoCommit)
        conn.rollback()
        assertEquals("0", scalar(conn, "SELECT count(*) FROM pg_namespace WHERE nspname = ${literal(schema)}"),
          "Collection operations must never commit the caller's transaction")
        assertNull(scalar(conn, "SELECT to_regclass(${literal(registry)})"))
      } finally {
        conn.rollback()
      }
    }
  }

  private fun options(history: Boolean, partitions: Int) = PgCollectionCreateOptions(
    storeHistory = history, partitions = partitions, shift = 12, retain = 10,
    backward = -1, forward = 3, storageClass = PgStorageClass.Consistent,
    tablespace = null, toastTupleTarget = 2048
  )

  private fun create(
    conn: PgConnection, schema: String, table: String, options: PgCollectionCreateOptions,
    definitions: Array<PgColumn> = columns, owner: String = logicalCollection
  ) {
    database.collection_create_table_if_not_exists(
      conn = conn, schema = schema, table = table, columns = definitions, indices = emptyArray(),
      options = options, logicalCatalog = logicalCatalog, logicalCollection = owner
    )
  }

  private fun assertRootContract(conn: PgConnection, schema: String, table: String) {
    val root = literal(relation(schema, table)) + "::regclass"
    assertEquals(logicalCatalog, scalar(conn,
      "SELECT obj_description(oid, 'pg_namespace') FROM pg_namespace WHERE nspname = ${literal(schema)}"))
    assertEquals(PgUtil.quoteIdent(logicalCatalog) + "." + PgUtil.quoteIdent(logicalCollection),
      scalar(conn, "SELECT obj_description($root, 'pg_class')"))
    assertEquals("RANGE (nv)", scalar(conn, "SELECT pg_get_partkeydef($root)"))
    assertEquals("p", scalar(conn, "SELECT relpersistence FROM pg_class WHERE oid = $root"))
    assertEquals("5", scalar(conn,
      "SELECT count(*) FROM pg_attribute WHERE attrelid = $root AND attnum > 0 AND NOT attisdropped"))
    for (column in columns) {
      assertEquals(column.name, scalar(conn,
        "SELECT col_description(attrelid, attnum) FROM pg_attribute WHERE attrelid = $root AND attname = ${literal(column.columnName)}"))
    }
    assertEquals("3", scalar(conn, """
      SELECT count(*) FROM pg_attribute WHERE attrelid = $root AND attname IN ('fn', 'nv', 'version')
        AND atttypid = 'bigint'::regtype AND attnotnull
    """))
    assertEquals("1", scalar(conn, """
      SELECT count(*) FROM pg_index i JOIN pg_constraint c ON c.conindid = i.indexrelid
      WHERE i.indrelid = $root AND c.contype = 'p' AND i.indisvalid AND i.indisready
        AND i.indnkeyatts = 3 AND pg_get_indexdef(i.indexrelid, 1, true) LIKE 'nv%'
        AND pg_get_indexdef(i.indexrelid, 2, true) LIKE 'fn%'
        AND pg_get_indexdef(i.indexrelid, 3, true) LIKE 'version%'
    """))
    assertTrue(scalar(conn, "SELECT to_regclass(${literal(relation(schema, table + "\$id"))})") != null)
    assertTrue(scalar(conn, "SELECT to_regclass(${literal(relation(schema, table + "\$version"))})") != null)
    assertEquals("1", scalar(conn, """
      SELECT count(*) FROM pg_index i WHERE i.indrelid = $root AND i.indisunique AND i.indisvalid
        AND i.indisready AND i.indnkeyatts = 2 AND i.indnatts = 3
        AND pg_get_indexdef(i.indexrelid, 1, true) LIKE 'fn%'
        AND pg_get_indexdef(i.indexrelid, 2, true) LIKE 'nv%'
        AND pg_get_indexdef(i.indexrelid, 3, true) LIKE 'version%'
    """))
  }

  private fun assertStorageAndInheritance(conn: PgConnection, root: String) {
    val tree = "pg_partition_tree(${literal(root)}::regclass)"
    assertEquals("0", scalar(conn, """
      SELECT count(*) FROM $tree t JOIN pg_class c ON c.oid = t.relid
      LEFT JOIN pg_attribute a ON a.attrelid = c.oid AND a.attname = ${literal(payload.columnName)} AND NOT a.attisdropped
      WHERE a.attnum IS NULL OR c.relpersistence <> 'p' OR a.atttypid <> 'text'::regtype OR a.attnotnull
        OR a.attstorage <> 'm' OR a.attcollation <> '"C"'::regcollation
    """))
    assertEquals("0", scalar(conn, """
      SELECT count(*) FROM $tree t JOIN pg_class c ON c.oid = t.relid
      JOIN pg_database d ON d.datname = current_database()
      WHERE t.isleaf AND (NOT coalesce(c.reloptions @> ARRAY['toast_tuple_target=2048',
        'fillfactor=' || CASE WHEN strpos(c.relname, '${'$'}head') > 0 THEN '50' ELSE '100' END], false)
        OR coalesce(nullif(c.reltablespace, 0), d.dattablespace) <>
          (SELECT coalesce(nullif(r.reltablespace, 0), d.dattablespace) FROM pg_class r WHERE r.oid = ${literal(root)}::regclass))
    """))
    assertEquals("0", scalar(conn, """
      SELECT count(*) FROM $tree t JOIN pg_index p ON p.indrelid = t.parentrelid
      WHERE NOT EXISTS (
        SELECT 1 FROM pg_inherits h JOIN pg_index c ON c.indexrelid = h.inhrelid
        WHERE h.inhparent = p.indexrelid AND c.indrelid = t.relid AND c.indisvalid AND c.indisready
      )
    """))
  }

  private fun assertRouting(conn: PgConnection, schema: String, table: String, partitions: Int, shift: Int, finite: Long) {
    for (slot in 0 until partitions) {
      val low = if (slot == 0) Long.MIN_VALUE else Id.partitionIndexMin(slot, partitions)
      val high = if (slot == partitions - 1) Long.MAX_VALUE else Id.partitionIndexMin(slot + 1, partitions) - 1
      for (fn in listOf(low, high)) {
        for (nv in listOf(finite, head)) {
          var child = table + if (nv == head) "\$head" else "$" + (nv shr shift)
          if (partitions > 1) child += "\$$slot"
          val expected = scalar(conn, "SELECT ${literal(relation(schema, child))}::regclass::oid::text")
          assertEquals(expected, insert(conn, schema, table, fn, 3L, nv),
            "fn=$fn nv=$nv must route to slot $slot, including NULL-payload tombstones")
        }
      }
    }
  }

  private fun insert(conn: PgConnection, schema: String, table: String, fn: Long, version: Long, nv: Long): String? {
    conn.execute("""
      INSERT INTO ${relation(schema, table)} (fn, version, nv, ${payload.ident})
      VALUES ($fn, $version, $nv, NULL) RETURNING tableoid::oid::text AS leaf
    """).fetch().use { cursor -> return cursor.get<String>("leaf") }
  }

  private fun assertLeafCount(conn: PgConnection, root: String, count: Int) {
    assertEquals(count.toString(), scalar(conn,
      "SELECT count(*) FROM pg_partition_tree(${literal(root)}::regclass) WHERE isleaf"))
  }

  private fun expectFailure(conn: PgConnection, action: () -> Unit) {
    val savepoint = conn.jdbc.setSavepoint()
    try {
      assertFailsWith<NakshaException> { action() }
    } finally {
      conn.jdbc.rollback(savepoint)
      conn.jdbc.releaseSavepoint(savepoint)
    }
  }

  private fun snapshot(conn: PgConnection, schema: String): String? = scalar(conn, """
    SELECT coalesce(string_agg(ROW(c.oid, c.relname, c.relkind, c.reloptions,
      obj_description(c.oid, 'pg_class'), pg_get_expr(c.relpartbound, c.oid))::text, '|' ORDER BY c.oid), '')
    FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace WHERE n.nspname = ${literal(schema)}
  """)

  private fun scalar(conn: PgConnection, sql: String): String? {
    conn.jdbc.createStatement().use { statement ->
      statement.executeQuery(sql).use { result ->
        assertTrue(result.next(), "Expected one result row")
        val value = result.getString(1)
        assertFalse(result.next(), "Expected only one result row")
        return value
      }
    }
  }

  private fun relation(schema: String, table: String) = PgUtil.quoteIdent(schema) + "." + PgUtil.quoteIdent(table)
  private fun literal(value: String) = PgUtil.quoteLiteral(value)
}
