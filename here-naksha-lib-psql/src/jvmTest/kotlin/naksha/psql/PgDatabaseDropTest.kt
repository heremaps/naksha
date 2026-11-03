package naksha.psql

import naksha.base.NakshaException
import naksha.base.Version
import naksha.model.Naksha
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.sql.DriverManager
import java.util.concurrent.Callable
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PgDatabaseDropTest {
  private val registry = "\"naksha~admin\".naksha_partman"

  @Test
  fun dropsRootsAndSchemasTransactionallyAndSerializesWithMaintenance() {
    val url = System.getenv("NAKSHA_TEST_DROP_DB_URL")
    val psqlUrl = System.getenv("NAKSHA_TEST_PSQL_DB_URL")
    assumeTrue(!url.isNullOrBlank() && !psqlUrl.isNullOrBlank(),
      "Set both database URL variables to the same fresh isolated disposable database")
    assertEquals(url, psqlUrl, "Drop and bootstrap must use the same isolated database")
    DriverManager.getConnection(url).use { jdbc ->
      jdbc.createStatement().use { statement ->
        statement.executeQuery("SELECT 1 FROM pg_namespace WHERE nspname = 'naksha~admin'").use { rows ->
          assertFalse(rows.next(), "Requires a fresh database; this test never clears existing schemas")
        }
      }
    }
    val config = PgConfig("database_drop_probe").apply {
      className = "naksha.psql.PgStorage"
      master = PgInstanceConfig.fromUri(url!!)
    }
    val storage = PgStorage(config, true, false)
    assertTrue(storage.initialized)
    val database = storage.database
    val schema = "Drop \"Target"
    val other = "Drop Other"
    val concurrent = "Drop Concurrent"
    storage.newConnection(Naksha.useAdminOptions(), false, PgStorage.SKIP_INIT).use { conn ->
      conn.autoCommit = false
      try {
        timeouts(conn)
        for (name in listOf(schema, other, concurrent)) {
          assertTrue(database.catalog_get_or_create_schema(conn, name, name) > 0)
        }
        create(database, conn, schema, "history_root", true, 3)
        create(database, conn, schema, "head_only", false, 1)
        create(database, conn, schema, "remaining_a", true, 3)
        create(database, conn, schema, "remaining_b", true, 3)
        create(database, conn, other, "history_root", true, 3)
        create(database, conn, concurrent, "history_root", true, 3)
        conn.execute("CREATE TABLE ${relation(schema, "unrelated")} (value text); " +
          "INSERT INTO ${relation(schema, "unrelated")} VALUES ('fixture')").close()
        assertEquals("3", registrations(conn, schema))
        assertEquals("0", registrations(conn, schema, "head_only"))
        assertEquals("27", scalar(conn, "SELECT count(*) FROM pg_partition_tree(${literal(relation(schema, "history_root"))}::regclass) WHERE isleaf"))
        assertEquals("1", scalar(conn, "SELECT count(*) FROM pg_partition_tree(${literal(relation(schema, "head_only"))}::regclass) WHERE isleaf"))
        conn.commit()

        validate(database, conn, schema)
        val preserved = snapshot(conn, other)
        val before = snapshot(conn, schema)
        val registered = registrySnapshot(conn)
        val data = data(conn, schema, "history_root")
        val rootOids = objectOids(conn, schema, "history_root")
        val savepoint = conn.jdbc.setSavepoint()
        database.collection_drop_if_exists(conn, schema, "history_root")
        assertDropped(conn, schema, "history_root", rootOids)
        assertEquals(preserved, snapshot(conn, other))
        assertFalse(conn.isClosed())
        assertFalse(conn.autoCommit)
        conn.jdbc.rollback(savepoint)
        conn.jdbc.releaseSavepoint(savepoint)
        assertEquals(before, snapshot(conn, schema), "Savepoint rollback must restore original root, descendants and index OIDs")
        assertEquals(registered, registrySnapshot(conn), "Rollback must restore the exact registration and root OID")
        assertEquals(data, data(conn, schema, "history_root"))

        database.collection_drop_if_exists(conn, schema, "history_root")
        database.collection_drop_if_exists(conn, schema, "history_root")
        database.collection_drop_if_exists(conn, schema, "missing_root")
        database.collection_drop_if_exists(conn, "Drop Missing", "missing_root")
        sweep(conn)
        assertDropped(conn, schema, "history_root", rootOids)
        val headOids = objectOids(conn, schema, "head_only")
        database.collection_drop_if_exists(conn, schema, "head_only")
        assertDropped(conn, schema, "head_only", headOids)
        conn.commit()

        storage.newConnection(Naksha.useAdminOptions(), false, PgStorage.SKIP_INIT).use { observer ->
          observer.autoCommit = false
          try {
            timeouts(observer)
            sweep(observer)
            assertDropped(observer, schema, "history_root", rootOids)
            assertDropped(observer, schema, "head_only", headOids)
            assertEquals(preserved, snapshot(observer, other))
            observer.commit()
          } finally {
            observer.rollback()
          }
        }

        val schemaBefore = snapshot(conn, schema)
        val schemaOids = assertNotNull(scalar(conn, "SELECT string_agg(c.oid::text, ',') FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace WHERE n.nspname = ${literal(schema)}"))
        val schemaRegistry = registrySnapshot(conn)
        val schemaOid = scalar(conn, "SELECT oid::text FROM pg_namespace WHERE nspname = ${literal(schema)}")
        val remainingData = data(conn, schema, "remaining_a")
        assertEquals("2", registrations(conn, schema))
        val schemaSavepoint = conn.jdbc.setSavepoint()
        database.catalog_drop_schema(conn, schema)
        assertSchemaDropped(conn, schema, schemaOids)
        assertEquals(preserved, snapshot(conn, other))
        conn.jdbc.rollback(schemaSavepoint)
        conn.jdbc.releaseSavepoint(schemaSavepoint)
        assertEquals(schemaBefore, snapshot(conn, schema))
        assertEquals(schemaRegistry, registrySnapshot(conn))
        assertEquals(schemaOid, scalar(conn, "SELECT oid::text FROM pg_namespace WHERE nspname = ${literal(schema)}"))
        assertEquals(remainingData, data(conn, schema, "remaining_a"))
        assertEquals("fixture", scalar(conn, "SELECT value FROM ${relation(schema, "unrelated")}"))
        database.catalog_drop_schema(conn, schema)
        database.catalog_drop_schema(conn, schema)
        database.catalog_drop_schema(conn, "Drop Missing")
        conn.commit()
        storage.newConnection(Naksha.useAdminOptions(), false, PgStorage.SKIP_INIT).use { observer ->
          observer.autoCommit = false
          try {
            timeouts(observer)
            sweep(observer)
            assertSchemaDropped(observer, schema, schemaOids)
            assertEquals(preserved, snapshot(observer, other))
            assertEquals("1", registrations(observer, other, "history_root"))
            observer.commit()
          } finally {
            observer.rollback()
          }
        }

        // Release every fixture/read lock before the independent maintenance holder starts.
        conn.commit()
        conn.autoCommit = true
        concurrentDrop(storage, conn, concurrent)
        concurrentDrop(storage, conn, concurrent, schemaDrop = true)
        assertEquals(preserved, snapshot(conn, other))
        assertEquals("1", registrations(conn, other, "history_root"))

        conn.autoCommit = false
        timeouts(conn)
        val bareSchema = "Drop Without Registry"
        assertTrue(database.catalog_get_or_create_schema(conn, bareSchema, bareSchema) > 0)
        create(database, conn, bareSchema, "head_only", false, 1)
        create(database, conn, bareSchema, "schema_root", false, 1)
        conn.commit()
        val bareBefore = snapshot(conn, bareSchema)
        val registryBefore = registrySnapshot(conn)
        val registryOid = scalar(conn, "SELECT ${literal(registry)}::regclass::oid::text")
        val bareSavepoint = conn.jdbc.setSavepoint()
        try {
          // Temporarily remove only our disposable database's registry, never the installed wrapper.
          conn.execute("DROP TABLE $registry").close()
          database.collection_drop_if_exists(conn, bareSchema, "head_only")
          database.collection_drop_if_exists(conn, bareSchema, "head_only")
          assertNull(scalar(conn, "SELECT to_regclass(${literal(relation(bareSchema, "head_only"))})"))
          assertNotNull(scalar(conn, "SELECT to_regclass(${literal(relation(bareSchema, "schema_root"))})"))
          database.catalog_drop_schema(conn, bareSchema)
          database.catalog_drop_schema(conn, bareSchema)
          assertEquals("0", scalar(conn, "SELECT count(*) FROM pg_namespace WHERE nspname = ${literal(bareSchema)}"))
          assertNull(scalar(conn, "SELECT to_regclass(${literal(registry)})"), "Drops must not install a missing registry")
          assertEquals(preserved, snapshot(conn, other))
        } finally {
          conn.jdbc.rollback(bareSavepoint)
          conn.jdbc.releaseSavepoint(bareSavepoint)
        }
        assertEquals(registryOid, scalar(conn, "SELECT ${literal(registry)}::regclass::oid::text"))
        assertEquals(registryBefore, registrySnapshot(conn))
        assertEquals(bareBefore, snapshot(conn, bareSchema))
        database.catalog_drop_schema(conn, bareSchema)
        conn.commit()
        sweep(conn)
        conn.commit()
      } finally {
        if (!conn.autoCommit) conn.rollback()
      }
    }
  }

  private fun validate(database: PgDatabase, conn: PgConnection, schema: String) {
    // No recovery savepoint: a server-side error would poison the next SELECT.
    for (name in listOf("", "bad\u0000name", "s".repeat(64), "\u00e9".repeat(32))) {
      assertFailsWith<NakshaException> { database.catalog_drop_schema(conn, name) }
      assertEquals("1", scalar(conn, "SELECT 1"))
      assertFailsWith<NakshaException> { database.collection_drop_if_exists(conn, name, "history_root") }
      assertEquals("1", scalar(conn, "SELECT 1"))
    }
    for (name in listOf("", "bad\u0000name", "t".repeat(30), "\u00e9".repeat(15))) {
      assertFailsWith<NakshaException> { database.collection_drop_if_exists(conn, schema, name) }
      assertEquals("1", scalar(conn, "SELECT 1"))
    }
    conn.commit()
    conn.autoCommit = true
    assertFailsWith<NakshaException> { database.collection_drop_if_exists(conn, schema, "history_root") }
    assertFailsWith<NakshaException> { database.catalog_drop_schema(conn, schema) }
    assertEquals("1", scalar(conn, "SELECT 1"))
    assertNotNull(scalar(conn, "SELECT to_regclass(${literal(relation(schema, "history_root"))})"))
    conn.autoCommit = false
    timeouts(conn)
  }

  private fun concurrentDrop(storage: PgStorage, observer: PgConnection, schema: String, schemaDrop: Boolean = false) {
    val executor = Executors.newSingleThreadExecutor()
    try {
      storage.newConnection(Naksha.useAdminOptions(), false, PgStorage.SKIP_INIT).use { holder ->
        holder.autoCommit = false
        try {
          timeouts(holder)
          val heldPid = assertNotNull(scalar(holder, "SELECT pg_backend_pid()")).toInt()
          val observerPid = assertNotNull(scalar(observer, "SELECT pg_backend_pid()")).toInt()
          // Creation retains a shared catalog guard, not a sweep-wide lock.
          create(storage.database, holder, schema, "transaction_root", false, 1)
          val temporaryOids = objectOids(holder, schema, "transaction_root")
          if (!schemaDrop) storage.database.collection_maintain_partitions(holder, schema, "history_root", 0)
          val oids = if (schemaDrop) temporaryOids else objectOids(holder, schema, "history_root")
          val lockKey = if (schemaDrop) literal("naksha:schema:$schema") else
            "format('naksha:partman:%I.%I', ${literal(schema)}, 'history_root')"
          val workerPid = CompletableFuture<Int>()
          val drop = executor.submit(Callable {
            try {
              storage.newConnection(Naksha.useAdminOptions(), false, PgStorage.SKIP_INIT).use { worker ->
                worker.autoCommit = false
                try {
                  timeouts(worker)
                  workerPid.complete(assertNotNull(scalar(worker, "SELECT pg_backend_pid()")).toInt())
                  if (schemaDrop) storage.database.catalog_drop_schema(worker, schema)
                  else storage.database.collection_drop_if_exists(worker, schema, "history_root")
                  worker.commit()
                } finally {
                  worker.rollback()
                }
              }
            } catch (failure: Throwable) {
              workerPid.completeExceptionally(failure)
              throw failure
            }
          })
          try {
            val pid = workerPid.get(10, TimeUnit.SECONDS)
            assertTrue(pid != heldPid && pid != observerPid && heldPid != observerPid)
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
            var blocked = false
            while (System.nanoTime() < deadline) {
              blocked = scalar(observer, """
                SELECT EXISTS (
                  SELECT 1 FROM pg_stat_activity a
                  JOIN pg_locks waiting ON waiting.pid = a.pid
                  JOIN pg_locks held ON held.locktype = waiting.locktype
                    AND held.database = waiting.database AND held.classid = waiting.classid
                    AND held.objid = waiting.objid AND held.objsubid = waiting.objsubid
                  WHERE a.pid = $pid AND a.state = 'active' AND a.wait_event_type = 'Lock'
                    AND waiting.locktype = 'advisory' AND NOT waiting.granted
                    AND held.pid = $heldPid AND held.granted
                    AND held.mode = '${if (schemaDrop) "ShareLock" else "ExclusiveLock"}' AND waiting.mode = 'ExclusiveLock'
                    AND held.objsubid = 1
                    AND held.classid::bigint = ((hashtextextended($lockKey, 0) >> 32) & 4294967295)
                    AND held.objid::bigint = (hashtextextended($lockKey, 0) & 4294967295)
                )::text
              """) == "true"
              if (blocked || drop.isDone) break
              Thread.sleep(10)
            }
            if (drop.isDone) drop.get(10, TimeUnit.SECONDS)
            assertTrue(blocked, "Drop must wait on the exact ${if (schemaDrop) "catalog" else "collection"} advisory lock, not merely a relation lock")
            assertFalse(drop.isDone, "Scoped locks must remain held after method return")
            // A queued drop must not prevent cross-call maintenance of unlocked scopes.
            assertTrue(storage.database.maintain_partitions(holder) > 0)
            storage.database.collection_drop_if_exists(holder, schema, "transaction_root")
            assertDropped(holder, schema, "transaction_root", temporaryOids)
            assertFalse(drop.isDone, "Cross-call maintenance and drop must retain the transaction's scoped locks")
            holder.commit()
            drop.get(15, TimeUnit.SECONDS)
            assertDropped(observer, schema, "history_root", oids)
            assertDropped(observer, schema, "transaction_root", temporaryOids)
            if (schemaDrop) assertSchemaDropped(observer, schema, oids)
            observer.autoCommit = false
            try {
              timeouts(observer)
              sweep(observer)
              assertDropped(observer, schema, "history_root", oids)
              observer.commit()
            } finally {
              observer.rollback()
              observer.autoCommit = true
            }
          } finally {
            holder.rollback()
            drop.cancel(true)
          }
        } finally {
          holder.rollback()
        }
      }
    } finally {
      executor.shutdownNow()
      assertTrue(executor.awaitTermination(35, TimeUnit.SECONDS), "Drop worker must terminate")
    }
  }

  private fun create(database: PgDatabase, conn: PgConnection, schema: String, table: String, history: Boolean, partitions: Int) {
    database.collection_create_table_if_not_exists(
      conn = conn, schema = schema, table = table,
      columns = arrayOf(PgColumn.FnColumn, PgColumn.VersionColumn, PgColumn.NextVersionColumn),
      indices = emptyArray(),
      options = PgCollectionCreateOptions(history, partitions, 12, 10, -1, 3, PgStorageClass.Consistent, null, 2048),
      logicalCatalog = schema, logicalCollection = table
    )
    if (history) database.collection_maintain_partitions(conn, schema, table, 0)
    conn.execute("INSERT INTO ${relation(schema, table)} (fn, version, nv) VALUES " +
      "(${Long.MIN_VALUE}, 1, ${Version.HEAD.number}), (${Long.MAX_VALUE}, 2, ${Version.HEAD.number})").close()
    if (history) conn.execute("INSERT INTO ${relation(schema, table)} (fn, version, nv) VALUES (0, 0, 3)").close()
  }

  private fun assertDropped(conn: PgConnection, schema: String, table: String, oids: String) {
    assertNull(scalar(conn, "SELECT to_regclass(${literal(relation(schema, table))})"))
    assertEquals("0", registrations(conn, schema, table))
    assertEquals("0", scalar(conn, "SELECT count(*) FROM pg_class WHERE oid IN ($oids)"),
      "Root, every descendant and every attached index must be removed")
  }

  private fun assertSchemaDropped(conn: PgConnection, schema: String, oids: String) {
    assertEquals("0", scalar(conn, "SELECT count(*) FROM pg_namespace WHERE nspname = ${literal(schema)}"))
    assertEquals("0", registrations(conn, schema))
    assertEquals("0", scalar(conn, "SELECT count(*) FROM pg_class WHERE oid IN ($oids)"))
  }

  private fun objectOids(conn: PgConnection, schema: String, table: String): String = assertNotNull(scalar(conn, """
    WITH tree AS (SELECT relid FROM pg_partition_tree(${literal(relation(schema, table))}::regclass)),
    objects AS (SELECT relid::oid AS oid FROM tree UNION SELECT indexrelid FROM pg_index JOIN tree ON indrelid = relid)
    SELECT string_agg(oid::text, ',' ORDER BY oid) FROM objects
  """))

  private fun snapshot(conn: PgConnection, schema: String) = scalar(conn, """
    SELECT coalesce(jsonb_agg(jsonb_build_array(c.oid, c.relname, c.relkind, c.reloptions,
      pg_get_expr(c.relpartbound, c.oid)) ORDER BY c.oid)::text, '[]')
    FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace WHERE n.nspname = ${literal(schema)}
  """)

  private fun registrySnapshot(conn: PgConnection) = scalar(conn,
    "SELECT jsonb_agg(to_jsonb(r) ORDER BY schema_name, table_name)::text FROM $registry r")

  private fun registrations(conn: PgConnection, schema: String, table: String? = null) = scalar(conn,
    "SELECT count(*) FROM $registry WHERE schema_name = ${literal(schema)}" +
      if (table == null) "" else " AND table_name = ${literal(table)}")

  private fun data(conn: PgConnection, schema: String, table: String) = scalar(conn,
    "SELECT jsonb_agg(to_jsonb(r) ORDER BY fn, nv)::text FROM ${relation(schema, table)} r")

  private fun sweep(conn: PgConnection) {
    assertTrue(assertNotNull(scalar(conn, "SELECT \"naksha~admin\".naksha_maintain()")).toInt() > 0)
  }

  private fun timeouts(conn: PgConnection) {
    conn.execute("SET LOCAL statement_timeout = '30s'; SET LOCAL lock_timeout = '10s'").close()
  }

  private fun scalar(conn: PgConnection, sql: String): String? {
    conn.jdbc.createStatement().use { statement ->
      statement.executeQuery(sql).use { rows ->
        assertTrue(rows.next(), "Expected one result row")
        val value = rows.getString(1)
        assertFalse(rows.next(), "Expected only one result row")
        return value
      }
    }
  }

  private fun relation(schema: String, table: String) = PgUtil.quoteIdent(schema) + "." + PgUtil.quoteIdent(table)
  private fun literal(value: String) = PgUtil.quoteLiteral(value)
}
