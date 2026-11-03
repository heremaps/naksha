package naksha.psql

import naksha.base.NakshaException
import naksha.base.Version
import naksha.model.Naksha
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.sql.DriverManager
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PgMaintenanceConcurrencyTest {
  private val schema = "Maintenance Concurrency"
  private val otherSchema = "Other Maintenance Catalog"
  private val registry = "\"naksha~admin\".naksha_partman"
  private val sequence = "\"naksha~admin\"." + PgUtil.quoteIdent(NAKSHA_VERSION_SEQ)

  @Test
  fun skipsBusyRootsAndCatalogsWithoutSerializingIndependentWork() {
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
    val executor = java.util.concurrent.Executors.newSingleThreadExecutor()
    try {
      connection(storage).use { observer ->
        storage.database.catalog_get_or_create_schema(observer, schema, schema)
        storage.database.catalog_get_or_create_schema(observer, otherSchema, otherSchema)
        create(storage.database, observer, schema, "a")
        create(storage.database, observer, schema, "b")
        create(storage.database, observer, otherSchema, "c")
        observer.commit()
        observer.autoCommit = true
        val expected = assertNotNull(scalar(observer, "SELECT count(*) FROM $registry")).toInt()
        assertTrue(expected >= 3, "Committed bootstrap roots must be included in sweep counts")
        assertEquals("3", scalar(observer, """
          SELECT count(*) FROM $registry WHERE schema_name IN (${literal(schema)}, ${literal(otherSchema)})
            AND partitions = 1 AND shift = 12
        """))

        collectionGuardScenarios(storage, executor, observer, expected)
        physicalLockScenarios(storage, executor, observer, expected)
        catalogGuardScenarios(storage, executor, observer, expected)
        catalogUpgradeScenario(storage, executor)
        independentCreation(storage, executor, observer)
        independentCatalogCreation(storage, executor, observer)
      }
    } finally {
      executor.shutdownNow()
      assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS), "Concurrency worker must terminate")
    }
  }

  private fun collectionGuardScenarios(
    storage: PgStorage, executor: ExecutorService, observer: PgConnection, expected: Int
  ) {
    for (commitHolder in listOf(true, false)) {
      connection(storage).use { holder ->
        try {
          // A completed per-root call must retain both advisory and physical locks until transaction end.
          PgPartitions.maintain(holder, schema, "a", PgPartitions.currentVersion(holder))
          noGlobalLocks(holder)
          assertEquals("1", scalar(holder, """
            SELECT count(*) FROM pg_locks WHERE pid = pg_backend_pid()
              AND relation = ${literal(relation(schema, "a"))}::regclass
              AND mode = 'AccessExclusiveLock' AND granted
          """))
          val anchor = advanceAnchor(observer)
          coverage(observer, anchor, false, false, false)
          assertEquals(expected - 1, sweep(storage, executor, plv8 = true),
            "PLV8 must finish while A's holder remains open (commit=$commitHolder)")
          coverage(observer, anchor, false, true, true)
          assertFalse(holder.autoCommit)
          assertEquals("1", scalar(holder, "SELECT 1"))
          if (commitHolder) holder.commit() else holder.rollback()
          assertEquals(expected, sweep(storage, executor, plv8 = true), "Unlocked A must be retried")
          coverage(observer, anchor, true, true, true)
        } finally {
          holder.rollback()
        }
      }
    }
  }

  private fun physicalLockScenarios(
    storage: PgStorage, executor: ExecutorService, observer: PgConnection, expected: Int
  ) {
    for (insert in listOf(true, false)) {
      connection(storage).use { holder ->
        try {
          val root = relation(schema, "a")
          if (insert) {
            holder.execute("INSERT INTO $root (fn, version, nv) VALUES (123, 7, ${Version.HEAD.number})").close()
          } else {
            assertEquals("0", scalar(holder, "SELECT count(*) FROM $root"))
          }
          assertEquals("0", scalar(holder, """
            SELECT count(*) FROM pg_locks WHERE pid = pg_backend_pid() AND locktype = 'advisory'
          """), "Ordinary INSERT/SELECT must not use cooperating advisory locks")
          for (plv8 in listOf(false, true)) {
            val anchor = advanceAnchor(observer)
            coverage(observer, anchor, false, false, false)
            assertEquals(expected - 1, sweep(storage, executor, plv8, checkSkippedLock = true),
              "Physical NOWAIT must skip A and complete B/C (insert=$insert, plv8=$plv8)")
            coverage(observer, anchor, false, true, true)
          }
          holder.rollback()
          assertEquals(expected, sweep(storage, executor, plv8 = false))
          val anchor = currentAnchor(observer)
          coverage(observer, anchor, true, true, true)
        } finally {
          holder.rollback()
        }
      }
    }
  }

  private fun catalogGuardScenarios(
    storage: PgStorage, executor: ExecutorService, observer: PgConnection, expected: Int
  ) {
    for (plv8 in listOf(false, true)) {
      connection(storage).use { holder ->
        try {
          // An existing catalog is visible in the registry snapshot, unlike an uncommitted new schema.
          assertTrue(PgPartitions.lockCatalog(holder, schema))
          noGlobalLocks(holder)
          val anchor = advanceAnchor(observer)
          coverage(observer, anchor, false, false, false)
          val busyRoots = assertNotNull(scalar(observer,
            "SELECT count(*) FROM $registry WHERE schema_name = ${literal(schema)}")).toInt()
          assertEquals(2, busyRoots)
          assertEquals(expected - busyRoots, sweep(storage, executor, plv8),
            "Exclusive catalog guard must skip every root in S, not the other catalog (plv8=$plv8)")
          coverage(observer, anchor, false, false, true)
          holder.rollback()
          assertEquals(expected, sweep(storage, executor, plv8))
          coverage(observer, anchor, true, true, true)
        } finally {
          holder.rollback()
        }
      }
    }
  }

  private fun catalogUpgradeScenario(storage: PgStorage, executor: ExecutorService) {
    connection(storage).use { first ->
      connection(storage).use { second ->
        try {
          assertTrue(PgPartitions.lockCatalog(first, schema, shared = true))
          assertTrue(PgPartitions.lockCatalog(second, schema, shared = true))
          first.execute("CREATE TEMP TABLE catalog_upgrade_probe (value integer) ON COMMIT DROP").close()
          first.execute("INSERT INTO catalog_upgrade_probe VALUES (42)").close()
          for (conn in listOf(first, second)) {
            val rejected = executor.submit(Callable {
              val failure = assertFailsWith<NakshaException> { PgPartitions.lockCatalog(conn, schema) }
              assertTrue(failure.message.orEmpty().contains("retry", ignoreCase = true), failure.message)
              assertFalse(conn.isClosed())
              assertFalse(conn.autoCommit)
              assertEquals("1", scalar(conn, "SELECT 1"),
                "Contested upgrade must not abort the outer transaction")
              noGlobalLocks(conn)
            })
            rejected.get(1, TimeUnit.SECONDS)
          }
          assertEquals("42", scalar(first, "SELECT value FROM catalog_upgrade_probe"),
            "Rejected upgrade must preserve earlier caller writes without rollback")
          second.rollback()
          val upgraded = executor.submit(Callable { PgPartitions.lockCatalog(first, schema) })
          assertTrue(upgraded.get(1, TimeUnit.SECONDS),
            "The same transaction must upgrade once the other shared guard is released")
          assertEquals("1", scalar(first, """
            SELECT count(*) FROM pg_locks WHERE pid = pg_backend_pid() AND locktype = 'advisory'
              AND mode = 'ExclusiveLock' AND granted AND objsubid = 1
              AND classid::bigint = ((hashtextextended(${literal("naksha:schema:$schema")}, 0) >> 32) & 4294967295)
              AND objid::bigint = (hashtextextended(${literal("naksha:schema:$schema")}, 0) & 4294967295)
          """), "Uncontested upgrade must retain the exclusive catalog guard")
          assertEquals("42", scalar(first, "SELECT value FROM catalog_upgrade_probe"))
          noGlobalLocks(first)
          first.commit()
        } finally {
          second.rollback()
          first.rollback()
        }
      }
    }
  }

  private fun independentCreation(storage: PgStorage, executor: ExecutorService, observer: PgConnection) {
    val catalog = "Concurrent Collection Creation"
    val database = storage.database
    observer.autoCommit = false
    timeouts(observer)
    assertTrue(database.catalog_get_or_create_schema(observer, catalog, catalog) > 0)
    observer.commit()
    observer.autoCommit = true
    connection(storage).use { first ->
      try {
        assertTrue(database.catalog_get_or_create_schema(first, catalog, catalog) < 0)
        create(database, first, catalog, "a")
        first.execute("""
          INSERT INTO ${relation(catalog, "a")} (fn, version, nv)
          SELECT n, 7, ${Version.HEAD.number} FROM generate_series(1, 20000) n
        """).close()
        val firstOid = assertNotNull(scalar(first, "SELECT ${literal(relation(catalog, "a"))}::regclass::oid::text"))
        assertNull(scalar(observer, "SELECT to_regclass(${literal(relation(catalog, "a"))})"))
        val second = executor.submit(Callable {
          connection(storage).use { worker ->
            try {
              assertTrue(database.catalog_get_or_create_schema(worker, catalog, catalog) < 0,
                "Existing catalog lookup must retain only a shared guard")
              create(database, worker, catalog, "b")
              worker.execute("INSERT INTO ${relation(catalog, "b")} (fn, version) VALUES (1, 7)").close()
              assertFalse(worker.tryMaintenance {
                PgPartitions.lockCollection(worker, catalog, "a", tryOnly = true)
              }, "Same collection creation must not bypass the first creator's lock")
              worker.commit()
            } finally {
              worker.rollback()
            }
          }
        })
        second.get(5, TimeUnit.SECONDS)
        assertEquals("1", scalar(observer, "SELECT count(*) FROM ${relation(catalog, "b")}"),
          "B must commit while A's large insert and creation are still uncommitted")
        assertNull(scalar(observer, "SELECT to_regclass(${literal(relation(catalog, "a"))})"))
        first.commit()
        connection(storage).use { retry ->
          try {
            create(database, retry, catalog, "a")
            assertEquals(firstOid, scalar(retry, "SELECT ${literal(relation(catalog, "a"))}::regclass::oid::text"),
              "Same-collection retry must reuse the committed root")
            assertEquals("20000", scalar(retry, "SELECT count(*) FROM ${relation(catalog, "a")}"))
            assertEquals("1", scalar(retry, """
              SELECT count(*) FROM $registry WHERE schema_name = ${literal(catalog)} AND table_name = 'a'
            """))
            retry.commit()
          } finally {
            retry.rollback()
          }
        }
      } finally {
        first.rollback()
      }
    }
  }

  private fun independentCatalogCreation(storage: PgStorage, executor: ExecutorService, observer: PgConnection) {
    connection(storage).use { first ->
      try {
        assertTrue(storage.database.catalog_get_or_create_schema(first, "New Catalog A", "New Catalog A") > 0)
        noGlobalLocks(first)
        val second = executor.submit(Callable {
          connection(storage).use { worker ->
            try {
              assertTrue(storage.database.catalog_get_or_create_schema(worker, "New Catalog B", "New Catalog B") > 0)
              create(storage.database, worker, "New Catalog B", "b")
              worker.commit()
            } finally {
              worker.rollback()
            }
          }
        })
        second.get(5, TimeUnit.SECONDS)
        assertEquals("1", scalar(observer, "SELECT count(*) FROM pg_namespace WHERE nspname = 'New Catalog B'"))
        assertEquals("0", scalar(observer, "SELECT count(*) FROM pg_namespace WHERE nspname = 'New Catalog A'"),
          "Independent schema creation must finish before the first catalog commits")
        first.commit()
      } finally {
        first.rollback()
      }
    }
  }

  private fun sweep(
    storage: PgStorage, executor: ExecutorService, plv8: Boolean, checkSkippedLock: Boolean = false
  ): Int {
    val result = executor.submit(Callable {
      connection(storage).use { worker ->
        try {
          worker.execute("CREATE TEMP TABLE outer_transaction_probe (value integer) ON COMMIT DROP").close()
          worker.execute("INSERT INTO outer_transaction_probe VALUES (42)").close()
          val before = targetLocks(worker)
          val count = if (plv8) {
            assertNotNull(scalar(worker, "SELECT \"naksha~admin\".naksha_maintain()")).toInt()
          } else {
            storage.database.maintain_partitions(worker)
          }
          noGlobalLocks(worker)
          if (checkSkippedLock) {
            assertEquals("0", before)
            assertEquals(before, targetLocks(worker),
              "Failed physical NOWAIT attempt must release A's advisory lock; B may retain the shared catalog guard")
          }
          assertFalse(worker.isClosed())
          assertFalse(worker.autoCommit)
          assertEquals("42", scalar(worker, "SELECT value FROM outer_transaction_probe"),
            "A skipped root must not roll back or abort the outer caller transaction")
          worker.commit()
          count
        } finally {
          worker.rollback()
        }
      }
    })
    return result.get(5, TimeUnit.SECONDS)
  }

  private fun targetLocks(conn: PgConnection): String? = scalar(conn, """
    SELECT count(*) FROM pg_locks WHERE pid = pg_backend_pid() AND locktype = 'advisory'
      AND objsubid = 1 AND granted
      AND classid::bigint = ((hashtextextended(format('naksha:partman:%I.%I', ${literal(schema)}, 'a'), 0) >> 32) & 4294967295)
      AND objid::bigint = (hashtextextended(format('naksha:partman:%I.%I', ${literal(schema)}, 'a'), 0) & 4294967295)
  """)

  private fun noGlobalLocks(conn: PgConnection) {
    assertEquals("0", scalar(conn, """
      SELECT count(*) FROM pg_locks WHERE locktype = 'advisory' AND objsubid = 1
        AND database = (SELECT oid FROM pg_database WHERE datname = current_database())
        AND classid::bigint = ((hashtextextended('naksha:partman:sweep', 0) >> 32) & 4294967295)
        AND objid::bigint = (hashtextextended('naksha:partman:sweep', 0) & 4294967295)
    """), "No backend may hold or wait for the retired global sweep lock")
  }

  private fun advanceAnchor(observer: PgConnection): Long {
    observer.autoCommit = false
    try {
      timeouts(observer)
      val anchor = PgPartitions.currentVersion(observer) + 16L * 4096L
      // setval is nontransactional; this suite requires a fresh disposable database.
      observer.execute("SELECT setval(${literal(sequence)}::regclass, $anchor, true)").close()
      observer.commit()
      return anchor
    } finally {
      observer.rollback()
      observer.autoCommit = true
    }
  }

  private fun currentAnchor(observer: PgConnection): Long =
    assertNotNull(scalar(observer, "SELECT last_value FROM $sequence")).toLong()

  private fun coverage(conn: PgConnection, anchor: Long, a: Boolean, b: Boolean, c: Boolean) {
    noGlobalLocks(conn)
    for ((catalog, table, present) in listOf(Triple(schema, "a", a), Triple(schema, "b", b), Triple(otherSchema, "c", c))) {
      for (slot in 0..3) {
        val range = relation(catalog, table + "$" + ((anchor shr 12) + slot))
        val oid = scalar(conn, "SELECT to_regclass(${literal(range)})")
        if (present) assertNotNull(oid, "Expected committed coverage $range")
        else assertNull(oid, "Busy or not-yet-maintained root must have no new coverage $range")
      }
    }
  }

  private fun create(database: PgDatabase, conn: PgConnection, catalog: String, table: String) {
    database.collection_create_table_if_not_exists(
      conn = conn, schema = catalog, table = table,
      columns = arrayOf(PgColumn.FnColumn, PgColumn.VersionColumn, PgColumn.NextVersionColumn),
      indices = emptyArray(),
      options = PgCollectionCreateOptions(true, 1, 12, 10, -1, 3, PgStorageClass.Consistent, null, 2048),
      logicalCatalog = catalog, logicalCollection = table
    )
    noGlobalLocks(conn)
  }

  private fun connection(storage: PgStorage): PgConnection {
    val conn = storage.newConnection(Naksha.useAdminOptions(), false, PgStorage.SKIP_INIT)
    conn.autoCommit = false
    timeouts(conn)
    return conn
  }

  private fun timeouts(conn: PgConnection) {
    conn.execute("SET LOCAL statement_timeout = '3s'; SET LOCAL lock_timeout = '3s'").close()
  }

  private fun scalar(conn: PgConnection, sql: String): String? {
    conn.jdbc.createStatement().use { statement ->
      statement.executeQuery(sql).use { rows ->
        assertTrue(rows.next(), "Expected one result row: $sql")
        val value = rows.getString(1)
        assertFalse(rows.next(), "Expected only one result row: $sql")
        return value
      }
    }
  }

  private fun relation(schema: String, table: String) = PgUtil.quoteIdent(schema) + "." + PgUtil.quoteIdent(table)
  private fun literal(value: String) = PgUtil.quoteLiteral(value)
}
