package naksha.psql

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
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PgMaintenanceConcurrencyTest {
  @Test
  fun serializesJvmAndPlv8SweepsUntilCallerCommitOrRollback() {
    val url = System.getenv("NAKSHA_TEST_CONCURRENCY_DB_URL")
    val psqlUrl = System.getenv("NAKSHA_TEST_PSQL_DB_URL")
    assumeTrue(!url.isNullOrBlank() && !psqlUrl.isNullOrBlank(),
      "Set both database URL variables to the same fresh isolated disposable database")
    assertEquals(url, psqlUrl, "Concurrency and bootstrap must use the same isolated database")
    DriverManager.getConnection(url).use { jdbc ->
      jdbc.createStatement().use { statement ->
        statement.executeQuery("SELECT 1 FROM pg_namespace WHERE nspname = 'naksha~admin'").use { rows ->
          assertFalse(rows.next(), "Requires a fresh database; this test never deletes existing schemas")
        }
      }
    }

    val config = PgConfig("maintenance_concurrency_probe").apply {
      className = "naksha.psql.PgStorage"
      master = PgInstanceConfig.fromUri(url!!)
    }
    val storage = PgStorage(config, true, false)
    assertTrue(storage.initialized)
    val database = storage.database
    val schema = "Maintenance Concurrency"
    val table = "history_root"
    val root = relation(schema, table)
    val registry = "\"naksha~admin\".naksha_partman"
    val sequence = "\"naksha~admin\"." + PgUtil.quoteIdent(NAKSHA_VERSION_SEQ)
    val executor = Executors.newSingleThreadExecutor()
    try {
      storage.newConnection(Naksha.useAdminOptions(), false, PgStorage.SKIP_INIT).use { setup ->
        setup.autoCommit = false
        try {
          timeouts(setup)
          assertTrue(database.catalog_get_or_create_schema(setup, schema, schema) > 0)
          database.collection_create_table_if_not_exists(
            conn = setup, schema = schema, table = table,
            columns = arrayOf(PgColumn.FnColumn, PgColumn.VersionColumn, PgColumn.NextVersionColumn),
            indices = emptyArray(),
            options = PgCollectionCreateOptions(true, 1, 12, 10, -1, 3,
              PgStorageClass.Consistent, null, 2048),
            logicalCatalog = schema, logicalCollection = table
          )
          setup.execute("""
            INSERT INTO $root (fn, version, nv) VALUES
              (${Long.MIN_VALUE}, 3, ${Version.HEAD.number}),
              (${Long.MAX_VALUE}, 7, ${Version.HEAD.number})
          """).close()
          setup.commit()
          val expected = assertNotNull(scalar(setup, "SELECT count(*) FROM $registry")).toInt()
          assertTrue(expected > 0)
          assertEquals("1", scalar(setup, """
            SELECT count(*) FROM $registry
            WHERE schema_name = ${literal(schema)} AND table_name = ${literal(table)}
              AND partitions = 1 AND shift = 12
          """))
          val fixture = scalar(setup, "SELECT jsonb_agg(to_jsonb(r) ORDER BY fn)::text FROM $root r")
          val nativeKeys = scalar(setup, """
            SELECT string_agg(ROW(indexrelid, pg_get_indexdef(indexrelid))::text, '|' ORDER BY indexrelid)
            FROM pg_index WHERE indrelid = ${literal(root)}::regclass
          """)
          val observerPid = assertNotNull(scalar(setup, "SELECT pg_backend_pid()")).toInt()

          for (commitFirst in listOf(true, false)) {
            setup.autoCommit = false
            timeouts(setup)
            val anchor = PgPartitions.currentVersion(setup) + 16L * 4096L
            // setval is deliberately nontransactional; only a disposable database is safe here.
            setup.execute("SELECT setval(${literal(sequence)}::regclass, $anchor, true)").close()
            val allocator = scalar(setup, "SELECT ROW(last_value, is_called)::text FROM $sequence")
            val beforeCount = assertNotNull(scalar(setup, """
              SELECT count(*) FROM pg_partition_tree(${literal(root)}::regclass) WHERE level = 1
            """)).toInt()
            for (slot in 0..3) {
              assertNull(scalar(setup, "SELECT to_regclass(${literal(relation(schema, table + "$" + ((anchor shr 12) + slot)))})"))
            }
            setup.commit()
            // Autocommit gives each statistics poll a fresh snapshot, without touching the worker connection.
            setup.autoCommit = true
            val workerPid = CompletableFuture<Int>()
            storage.newConnection(Naksha.useAdminOptions(), false, PgStorage.SKIP_INIT).use { first ->
              first.autoCommit = false
              try {
                timeouts(first)
                val firstPid = assertNotNull(scalar(first, "SELECT pg_backend_pid()")).toInt()
                assertFalse(firstPid == observerPid)
                assertEquals(expected, database.maintain_partitions(first))
                val firstRanges = ArrayList<String>()
                for (slot in 0..3) {
                  val range = relation(schema, table + "$" + ((anchor shr 12) + slot))
                  firstRanges.add(assertNotNull(scalar(first, "SELECT ${literal(range)}::regclass::oid::text")))
                  assertNull(scalar(setup, "SELECT to_regclass(${literal(range)})"),
                    "First sweep must not commit its newly provisioned ranges")
                }

                val second = executor.submit(Callable {
                  try {
                    storage.newConnection(Naksha.useAdminOptions(), false, PgStorage.SKIP_INIT).use { worker ->
                      worker.autoCommit = false
                      try {
                        timeouts(worker)
                        workerPid.complete(assertNotNull(scalar(worker, "SELECT pg_backend_pid()")).toInt())
                        val count = assertNotNull(scalar(worker, "SELECT \"naksha~admin\".naksha_maintain()")).toInt()
                        worker.commit()
                        count
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
                  val secondPid = workerPid.get(30, TimeUnit.SECONDS)
                  assertFalse(firstPid == secondPid, "Sweeps must use independent PostgreSQL backends")
                  assertFalse(observerPid == secondPid)
                  val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
                  var blocked = false
                  while (System.nanoTime() < deadline) {
                    blocked = scalar(setup, """
                      SELECT EXISTS (
                        SELECT 1 FROM pg_stat_activity a
                        JOIN pg_locks waiting ON waiting.pid = a.pid
                        JOIN pg_locks held ON held.locktype = waiting.locktype
                          AND held.database = waiting.database AND held.classid = waiting.classid
                          AND held.objid = waiting.objid AND held.objsubid = waiting.objsubid
                        WHERE a.pid = $secondPid AND a.state = 'active' AND a.wait_event_type = 'Lock'
                          AND waiting.locktype = 'advisory' AND NOT waiting.granted
                          AND held.pid = $firstPid AND held.granted
                          AND held.mode = 'ExclusiveLock' AND waiting.mode = 'ExclusiveLock'
                          AND held.objsubid = 1
                          AND held.classid::bigint = ((hashtextextended('naksha:partman:sweep', 0) >> 32) & 4294967295)
                          AND held.objid::bigint = (hashtextextended('naksha:partman:sweep', 0) & 4294967295)
                      )::text
                    """) == "true"
                    if (blocked || second.isDone) break
                    Thread.sleep(10)
                  }
                  if (second.isDone) second.get(30, TimeUnit.SECONDS)
                  assertTrue(blocked, "PLV8 must wait on the global sweep advisory key held by the completed JVM call")
                  assertFalse(second.isDone, "The sweep lock must survive method return until transaction completion")
                  if (commitFirst) first.commit() else first.rollback()
                  assertEquals(expected, second.get(30, TimeUnit.SECONDS),
                    "The waiting SQL sweep must finish and commit after the first caller releases its transaction")

                  assertEquals((beforeCount + 4).toString(), scalar(setup, """
                    SELECT count(*) FROM pg_partition_tree(${literal(root)}::regclass) WHERE level = 1
                  """), "Exactly four ahead ranges must survive, even when the first caller rolls back")
                  for (slot in 0..3) {
                    val range = relation(schema, table + "$" + ((anchor shr 12) + slot))
                    val oid = assertNotNull(scalar(setup, "SELECT ${literal(range)}::regclass::oid::text"))
                    assertEquals(commitFirst, oid == firstRanges[slot],
                      "Commit must retain the first range; rollback must force the waiting sweep to provision it")
                    val start = ((anchor shr 12) + slot) shl 12
                    assertEquals("1", scalar(setup, """
                      SELECT count(*) FROM pg_inherits h JOIN pg_class c ON c.oid = h.inhrelid
                      WHERE h.inhparent = ${literal(root)}::regclass AND c.oid = $oid::oid
                        AND c.relkind = 'r' AND c.relispartition
                        AND pg_get_expr(c.relpartbound, c.oid) = ${literal("FOR VALUES FROM ('$start') TO ('${start + 4096}')")}
                    """))
                  }
                  assertEquals(nativeKeys, scalar(setup, """
                    SELECT string_agg(ROW(indexrelid, pg_get_indexdef(indexrelid))::text, '|' ORDER BY indexrelid)
                    FROM pg_index WHERE indrelid = ${literal(root)}::regclass
                  """), "Maintenance must preserve native root keys and indexes")
                  assertEquals("0", scalar(setup, """
                    SELECT count(*) FROM pg_partition_tree(${literal(root)}::regclass) t
                    WHERE NOT EXISTS (
                      SELECT 1 FROM pg_index i JOIN pg_constraint c ON c.conindid = i.indexrelid
                      WHERE i.indrelid = t.relid AND c.contype = 'p' AND i.indisvalid AND i.indisready
                        AND i.indnkeyatts = 3 AND pg_get_indexdef(i.indexrelid, 1, true) = 'nv'
                        AND pg_get_indexdef(i.indexrelid, 2, true) = 'fn'
                        AND pg_get_indexdef(i.indexrelid, 3, true) = 'version'
                    ) OR NOT EXISTS (
                      SELECT 1 FROM pg_index i WHERE i.indrelid = t.relid
                        AND i.indisunique AND i.indisvalid AND i.indisready
                        AND i.indnkeyatts = 2 AND i.indnatts = 3
                        AND pg_get_indexdef(i.indexrelid, 1, true) = 'fn'
                        AND pg_get_indexdef(i.indexrelid, 2, true) = 'nv'
                        AND pg_get_indexdef(i.indexrelid, 3, true) = 'version'
                    )
                  """), "Every retained and newly created leaf must have the native primary and unique keys")
                  assertEquals(fixture, scalar(setup, "SELECT jsonb_agg(to_jsonb(r) ORDER BY fn)::text FROM $root r"))
                  assertEquals(allocator, scalar(setup, "SELECT ROW(last_value, is_called)::text FROM $sequence"))
                } finally {
                  first.rollback()
                  second.cancel(true)
                }
              } finally {
                first.rollback()
              }
            }
          }
        } finally {
          if (!setup.autoCommit) setup.rollback()
        }
      }
    } finally {
      executor.shutdownNow()
      assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS), "Maintenance worker must terminate")
    }
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
