package naksha.psql

import naksha.base.NakshaError
import naksha.base.NakshaException
import naksha.base.Id
import naksha.base.fn.Fx2
import naksha.base.illegalState
import naksha.model.NakshaVersion
import naksha.model.SessionOptions
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.mockito.Mockito
import java.sql.Connection
import java.sql.DriverManager
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Independent storage instances and JDBC backends exercise bootstrap without Naksha.useStorage's process-global lock. */
@EnabledIfEnvironmentVariable(named = "NAKSHA_TEST_BOOTSTRAP_LOCK_DB_URL", matches = ".+")
class PgBootstrapConcurrencyTest {
  private val clients = 4
  private val storageId = "bootstrap_concurrency_probe"

  /** Stronger connection defaults must not keep the pre-lock snapshot across a competing installer's commit. */
  private class Client(config: PgConfig) : PgStorage() {
    init {
      initializeStorage(config, true, true)
    }

    override fun newConnection(options: SessionOptions, readOnly: Boolean, init: Fx2<PgConnection, String>?): PgConnection {
      // The external lock deliberately queues all clients before a full cold installation. Allow that setup to finish; PostgreSQL's deadlock detector and the bounded futures still enforce progress.
      val conn = super.newConnection(options.copy(lockTimeout = 60_000, stmtTimeout = 120_000, socketTimeout = 180_000), readOnly, init)
      conn.execute("SET SESSION CHARACTERISTICS AS TRANSACTION ISOLATION LEVEL REPEATABLE READ").close()
      conn.execute("SET TRANSACTION ISOLATION LEVEL REPEATABLE READ").close()
      conn.execute("SELECT current_setting('default_transaction_isolation') AS defaults, current_setting('transaction_isolation') AS initial").fetch().use { cursor ->
        assertEquals("repeatable read", cursor.get<String>("defaults"))
        assertEquals("repeatable read", cursor.get<String>("initial"))
      }
      return conn
    }
  }

  @Test
  fun coordinatesColdStartUpgradeWarmStartAndChangedIdentity() {
    val url = assertNotNull(System.getenv("NAKSHA_TEST_BOOTSTRAP_LOCK_DB_URL"))
    assertEquals(url, System.getenv("NAKSHA_TEST_PSQL_DB_URL"), "Requires matching URLs to a fresh, isolated database")
    DriverManager.getConnection(url).use { control ->
      control.autoCommit = false
      assertEquals(0L, scalar(control, "SELECT count(*) FROM pg_namespace WHERE nspname = 'naksha~admin'"),
        "This regression never clears existing databases")
      control.commit()
      val executor = Executors.newFixedThreadPool(clients)
      try {
        // All clients first observe the absent admin schema, then wait on one database-scoped installer lock.
        acquire(control)
        val installing = startClients(executor, url)
        awaitWaiters(control, installing)
        control.commit()
        val installed = awaitClients(installing).first()
        assertInstalled(control)

        // A current installation must open even while some unrelated connection owns the installer lock.
        acquire(control)
        val before = scalar(control, "SELECT xmin::text::bigint FROM pg_proc WHERE oid = '" +
          "\"naksha~admin\".es_modules_init()'::regprocedure")
        awaitClients(startClients(executor, url), timeoutSeconds = 15)
        assertEquals(before, scalar(control, "SELECT xmin::text::bigint FROM pg_proc WHERE oid = '" +
          "\"naksha~admin\".es_modules_init()'::regprocedure"))
        assertEquals(0L, waiters(control))
        control.commit()

        // Count actual module-update transactions so successful serial execution alone cannot hide redundant installs.
        execute(control, "CREATE TABLE public.bootstrap_install_audit (transaction_id bigint NOT NULL)")
        execute(control, """
          CREATE FUNCTION public.bootstrap_record_install() RETURNS trigger LANGUAGE plpgsql AS ${'$'}${'$'}
          BEGIN INSERT INTO public.bootstrap_install_audit VALUES (txid_current()); RETURN NULL; END;
          ${'$'}${'$'}
        """.trimIndent())
        execute(control, "CREATE TRIGGER bootstrap_install_audit AFTER UPDATE ON \"naksha~admin\".es_modules " +
          "FOR EACH STATEMENT EXECUTE FUNCTION public.bootstrap_record_install()")
        markOldVersion(control)
        control.commit()
        acquire(control)
        val upgrading = startClients(executor, url)
        awaitWaiters(control, upgrading)
        control.commit()
        awaitClients(upgrading)
        assertInstalled(control)
        assertTrue(scalar(control, "SELECT count(*) FROM public.bootstrap_install_audit") > 0)
        assertEquals(1L, scalar(control, "SELECT count(DISTINCT transaction_id) FROM public.bootstrap_install_audit"),
          "Waiters must re-read the installed version and skip redundant DDL")

        // The real installer can fail after modifying functions, modules, roots and the schema comment. None of that transaction may escape rollback, and its advisory lock must be released.
        val auditRows = scalar(control, "SELECT count(*) FROM public.bootstrap_install_audit")
        val beforeFailure = scalar(control, "SELECT xmin::text::bigint FROM pg_proc WHERE oid = '" +
          "\"naksha~admin\".es_modules_init()'::regprocedure")
        markOldVersion(control)
        control.commit()
        val failedConnection = installed.newConnection(SessionOptions(), false)
        val failedConfig = configuration(url)
        val failedStorage = Mockito.mock(PgStorage::class.java) { call ->
          when (call.method.name) {
            "getConfig" -> failedConfig
            "newConnection" -> failedConnection
            else -> Mockito.RETURNS_DEFAULTS.answer(call)
          }
        }
        val failedDatabase = object : PgDatabaseImpl(failedStorage, Id(storageId)) {
          override fun upsert_naksha_admin(conn: PgConnection, id: Id, psql_version: NakshaVersion,
            schema_oid: Int?, installed_version: NakshaVersion?): Int {
            super.upsert_naksha_admin(conn, id, psql_version, schema_oid, installed_version)
            conn.execute("CREATE TABLE public.bootstrap_rolled_back (value integer)").close()
            throw illegalState("simulated failure after real installation DDL")
          }
        }
        val installFailure = assertFailsWith<NakshaException> { failedDatabase.init(doUpgrade = true) }
        assertTrue(installFailure.message.orEmpty().contains("simulated failure"))
        assertEquals(NakshaVersion.of("3.0.0-beta.53").toLong(), scalar(control, "SELECT \"naksha~admin\".naksha_version()"))
        assertEquals(beforeFailure, scalar(control, "SELECT xmin::text::bigint FROM pg_proc WHERE oid = '" +
          "\"naksha~admin\".es_modules_init()'::regprocedure"))
        assertEquals(0L, scalar(control, "SELECT count(*) FROM pg_class WHERE oid = to_regclass('public.bootstrap_rolled_back')"))
        assertEquals(auditRows, scalar(control, "SELECT count(*) FROM public.bootstrap_install_audit"))
        assertTrue(scalar(control, "SELECT pg_try_advisory_xact_lock($PG_ADMIN_INSTALL_LOCK_CLASS, $PG_ADMIN_INSTALL_LOCK_KEY)::int") == 1L)
        control.commit()
        awaitClients(startClients(executor, url))
        assertInstalled(control)
        assertEquals(2L, scalar(control, "SELECT count(DISTINCT transaction_id) FROM public.bootstrap_install_audit"))

        // Each waiter must also re-check identity, not merely the version, after obtaining the lock.
        val beforeIdentityAudit = scalar(control, "SELECT count(*) FROM public.bootstrap_install_audit")
        markOldVersion(control)
        control.commit()
        acquire(control)
        val rejected = startClients(executor, url)
        awaitWaiters(control, rejected)
        execute(control, "CREATE OR REPLACE FUNCTION \"naksha~admin\".naksha_storage_id() RETURNS text " +
          "LANGUAGE sql IMMUTABLE PARALLEL SAFE AS ${'$'}${'$'} SELECT 'different_storage'::text ${'$'}${'$'}")
        control.commit()
        for (future in rejected) {
          val failure = assertFailsWith<ExecutionException> { future.get(120, TimeUnit.SECONDS) }
          assertEquals(NakshaError.STORAGE_ID_MISMATCH, assertIs<NakshaException>(failure.cause).error.code)
        }
        assertEquals(beforeIdentityAudit, scalar(control, "SELECT count(*) FROM public.bootstrap_install_audit"))
        control.createStatement().use { statement ->
          statement.executeQuery("SELECT pg_try_advisory_xact_lock($PG_ADMIN_INSTALL_LOCK_CLASS, $PG_ADMIN_INSTALL_LOCK_KEY)").use { rows ->
            assertTrue(rows.next())
            assertTrue(rows.getBoolean(1), "Failed initializers must release transaction-scoped installer locks")
          }
        }
        control.commit()
      } finally {
        control.rollback()
        executor.shutdownNow()
        assertTrue(executor.awaitTermination(120, TimeUnit.SECONDS))
      }
    }
  }

  private fun startClients(executor: ExecutorService, url: String): List<Future<PgStorage>> {
    val futures = ArrayList<Future<PgStorage>>(clients)
    for (i in 0 until clients) {
      futures.add(executor.submit(Callable<PgStorage> {
        Client(configuration(url))
      }))
    }
    return futures
  }

  private fun configuration(url: String): PgConfig = PgConfig(storageId).withMaster(PgInstanceConfig.fromUri(url))

  private fun awaitClients(futures: List<Future<PgStorage>>, timeoutSeconds: Long = 120): List<PgStorage> {
    val storages = ArrayList<PgStorage>(futures.size)
    for (future in futures) {
      val storage = future.get(timeoutSeconds, TimeUnit.SECONDS)
      assertTrue(storage.initialized)
      assertTrue(storage.database.schemaOid > 0)
      assertTrue(storage.database.versionSequenceOid > 0)
      storages.add(storage)
    }
    return storages
  }

  private fun awaitWaiters(control: Connection, futures: List<Future<PgStorage>>) {
    val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30)
    while (System.nanoTime() < deadline) {
      if (waiters(control) == clients.toLong()) return
      for (future in futures) {
        if (future.isDone) {
          future.get(0, TimeUnit.SECONDS)
          assertFalse(future.isDone, "A client requiring installation must wait for the database lock")
        }
      }
      Thread.sleep(10)
    }
    assertEquals(clients.toLong(), waiters(control), "All independent initializers must reach the installer lock")
  }

  private fun waiters(conn: Connection): Long = scalar(conn, "SELECT count(*) FROM pg_locks WHERE locktype = 'advisory' " +
    "AND database = (SELECT oid FROM pg_database WHERE datname = current_database()) " +
    "AND classid = $PG_ADMIN_INSTALL_LOCK_CLASS::oid AND objid = $PG_ADMIN_INSTALL_LOCK_KEY::oid AND objsubid = 2 AND NOT granted")

  private fun acquire(conn: Connection) {
    execute(conn, "SELECT pg_advisory_xact_lock($PG_ADMIN_INSTALL_LOCK_CLASS, $PG_ADMIN_INSTALL_LOCK_KEY)")
  }

  private fun markOldVersion(conn: Connection) {
    val old = NakshaVersion.of("3.0.0-beta.53").toLong()
    execute(conn, "CREATE OR REPLACE FUNCTION \"naksha~admin\".naksha_version() RETURNS int8 " +
      "LANGUAGE sql IMMUTABLE PARALLEL SAFE AS ${'$'}${'$'} SELECT $old::int8 ${'$'}${'$'}")
  }

  private fun assertInstalled(conn: Connection) {
    assertEquals(adminVersion.toLong(), scalar(conn, "SELECT \"naksha~admin\".naksha_version()"))
    assertEquals(4L, scalar(conn, "SELECT count(*) FROM pg_class WHERE relnamespace = '\"naksha~admin\"'::regnamespace " +
      "AND relkind = 'p' AND relname IN ('naksha~collections', 'naksha~catalogs', 'naksha~transactions', 'naksha~books')"))
    conn.commit()
  }

  private fun execute(conn: Connection, sql: String) {
    conn.createStatement().use { it.execute(sql) }
  }

  private fun scalar(conn: Connection, sql: String): Long = conn.createStatement().use { statement ->
    statement.executeQuery(sql).use { rows ->
      assertTrue(rows.next())
      rows.getLong(1)
    }
  }
}
