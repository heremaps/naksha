package naksha.psql

import java.sql.Connection
import java.sql.DriverManager
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import naksha.base.Id
import naksha.base.NakshaException
import naksha.model.Naksha
import naksha.model.objects.NakshaCatalog
import naksha.model.objects.NakshaCollection
import naksha.model.request.SuccessResponse
import naksha.model.request.Write
import naksha.model.request.WriteRequest
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Requires an explicitly isolated, preloaded pg_cron cluster. Creates uniquely named databases and retains them for inspection; never resets or drops pre-existing databases. @since 3.0 */
@EnabledIfEnvironmentVariable(named = "NAKSHA_TEST_CRON_CONTROL_URL", matches = ".+")
class PgCronIntegrationTest {
  @Test
  fun installsRunsReconcilesAndCleansOnlyItsOwnDatabaseJobs() {
    assertTrue(System.getenv("NAKSHA_TEST_PSQL_DB_URL").isNullOrEmpty(), "The cron test must not use a global storage URL override")
    val controlUrl = System.getenv("NAKSHA_TEST_CRON_CONTROL_URL")
    val base = PgInstanceConfig.fromUri(controlUrl)
    val suffix = UUID.randomUUID().toString().replace("-", "").take(12)
    val firstName = "naksha_cron_a_$suffix"
    val secondName = "naksha_cron_b_$suffix"
    val plainName = "naksha_cron_plain_$suffix"
    DriverManager.getConnection(controlUrl).use { control ->
      for (name in listOf(firstName, secondName, plainName)) sql(control, "CREATE DATABASE ${PgUtil.quoteIdent(name)} TEMPLATE template0")
      val first = storage(base, firstName, "cron_first")
      val second = storage(base, secondName, "cron_second")
      val firstOid = databaseOid(control, firstName)
      val secondOid = databaseOid(control, secondName)
      val firstJob = job(control, PgCron.PREFIX + firstOid)
      val secondJob = job(control, PgCron.PREFIX + secondOid)
      assertEquals(2, scalar(control, "SELECT count(*) FROM cron.job WHERE jobid IN ($firstJob, $secondJob)"))
      assertEquals(1, scalar(control, "SELECT count(*) FROM cron.job WHERE jobname = '${PgCron.CLEANUP}'"))
      assertEquals(0, scalar(control, "SELECT count(*) FROM cron.job WHERE database = ${PgUtil.quoteLiteral(plainName)}"))
      assertEquals(PgCron.SCHEDULE, text(control, "SELECT schedule FROM cron.job WHERE jobid = $firstJob"))
      assertFalse(text(control, "SELECT command FROM cron.job WHERE jobid = $firstJob").contains(base.password))

      // Repeated and independent concurrent reconciliation preserves IDs and does not create duplicates.
      val pool = Executors.newFixedThreadPool(3)
      try {
        val tasks = ArrayList<java.util.concurrent.Future<Unit>>()
        for (i in 0 until 3) tasks.add(pool.submit(Callable { PgCron.ensure(first.cluster.master, first.id, "disable") }))
        for (task in tasks) task.get(60, TimeUnit.SECONDS)
      } finally {
        pool.shutdownNow()
        assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS))
      }
      assertEquals(firstJob, job(control, PgCron.PREFIX + firstOid))

      // An unrelated job occupying our name must not be adopted. Admin installation remains committed, and the same storage object can retry after the scheduling conflict is removed.
      val retryName = "naksha_cron_retry_$suffix"
      sql(control, "CREATE DATABASE ${PgUtil.quoteIdent(retryName)} TEMPLATE template0")
      val reserved = PgCron.PREFIX + databaseOid(control, retryName)
      val conflict = text(control, "SELECT cron.schedule_in_database(${PgUtil.quoteLiteral(reserved)}, '0 0 1 1 *', 'SELECT 1', ${PgUtil.quoteLiteral(retryName)})").toLong()
      val retry = RetryStorage()
      val retryConfig = configuration(base, retryName, "cron_retry")
      val setupFailure = assertFailsWith<NakshaException> { retry.open(retryConfig) }
      assertTrue(setupFailure.message.orEmpty().contains("initialization committed"))
      assertFalse(retry.initialized)
      assertEquals("SELECT 1", text(control, "SELECT command FROM cron.job WHERE jobid = $conflict"))
      DriverManager.getConnection(targetUrl(base, retryName)).use { installed ->
        assertEquals(1, scalar(installed, "SELECT count(*) FROM pg_namespace WHERE nspname = 'naksha~admin'"))
      }
      sql(control, "SELECT cron.unschedule($conflict)")
      retry.open(retryConfig)
      assertTrue(retry.initialized)
      retry.removeMaintenanceJob()

      val catalog = NakshaCatalog(first.id, "cron_catalog")
      write(first, WriteRequest().add(Write().createCatalog(catalog)))
      val collection = NakshaCollection("events", catalog.id, partitions = 4)
      write(first, WriteRequest().add(Write().createCollection(collection)))
      val instance = first.cluster.master
      val targetUrl = targetUrl(base, firstName)
      var missing: String
      DriverManager.getConnection(targetUrl).use { target ->
        val root = Id(collection.id).tableName().name
        val schema = Id(catalog.id).schemaName().name
        missing = text(target, "SELECT c.relname FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace " +
          "JOIN pg_inherits i ON i.inhrelid = c.oid JOIN pg_class p ON p.oid = i.inhparent " +
          "WHERE n.nspname = ${PgUtil.quoteLiteral(schema)} AND p.relname = ${PgUtil.quoteLiteral(root)} " +
          "AND c.relname <> ${PgUtil.quoteLiteral(root + "\$head")} ORDER BY c.relname LIMIT 1")
        sql(target, "DROP TABLE ${PgUtil.quoteIdent(schema)}.${PgUtil.quoteIdent(missing)}")
        val function = "SELECT oid::text FROM pg_proc WHERE oid = '\"naksha~admin\".naksha_maintain()'::regprocedure"
        val functionOid = text(target, function)
        sql(control, "SELECT cron.alter_job($firstJob, schedule := '1 second')")
        await("A real pg_cron worker must restore missing finite coverage") {
          scalar(target, "SELECT count(*) FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace WHERE n.nspname = ${PgUtil.quoteLiteral(schema)} AND c.relname = ${PgUtil.quoteLiteral(missing)}") == 1
        }
        await("Successful cron execution must be observable") {
          scalar(control, "SELECT count(*) FROM cron.job_run_details WHERE jobid = $firstJob AND status = 'succeeded'") > 0
        }
        sql(control, "SELECT cron.alter_job($firstJob, schedule := ${PgUtil.quoteLiteral(PgCron.SCHEDULE)})")
        val topology = text(target, "SELECT string_agg(oid::text, ',' ORDER BY oid) FROM pg_class WHERE relnamespace = ${PgUtil.quoteLiteral(schema)}::regnamespace")
        val command = text(control, "SELECT command FROM cron.job WHERE jobid = $firstJob")
        sql(target, command)
        assertEquals(topology, text(target, "SELECT string_agg(oid::text, ',' ORDER BY oid) FROM pg_class WHERE relnamespace = ${PgUtil.quoteLiteral(schema)}::regnamespace"), "No-op sweep must not recreate physical relations")
        assertEquals(functionOid, text(target, function), "Sweeps must not reinstall their runtime")

        // A manual SQL caller holding the sweep guard makes the cron command skip, without waiting.
        DriverManager.getConnection(targetUrl).use { blocker ->
          blocker.autoCommit = false
          sql(blocker, "SELECT pg_advisory_xact_lock(1852531051, 1835100526)")
          sql(target, command)
          assertEquals("0", text(target, "SELECT \"naksha~admin\".naksha_maintain()"), "The manual wrapper must share the same nonblocking sweep guard")
          blocker.rollback()
        }
        assertFailsWith<NakshaException> { PgCron.ensure(instance, Id("not_this_storage"), "disable") }
        assertFailsWith<NakshaException> { PgCron.ensure(instance, first.id, "prefer") }
      }

      // Unrelated jobs must survive cleanup, even when their database disappears.
      val unrelated = text(control, "SELECT cron.schedule_in_database('unrelated_$suffix', '0 0 1 1 *', 'SELECT 1', ${PgUtil.quoteLiteral(plainName)})").toLong()
      sql(control, "DROP DATABASE ${PgUtil.quoteIdent(plainName)}")
      sql(control, "DROP DATABASE ${PgUtil.quoteIdent(secondName)} WITH (FORCE)")
      sql(control, "CREATE DATABASE ${PgUtil.quoteIdent(secondName)} TEMPLATE template0")
      assertTrue(databaseOid(control, secondName) != secondOid)
      val cleanupJob = job(control, PgCron.CLEANUP)
      sql(control, "SELECT cron.alter_job($cleanupJob, schedule := '1 second')")
      await("A real cleanup worker must remove the stale OID job after DROP/recreate") {
        scalar(control, "SELECT count(*) FROM cron.job WHERE jobid = $secondJob") == 0
      }
      sql(control, "SELECT cron.alter_job($cleanupJob, schedule := ${PgUtil.quoteLiteral(PgCron.SCHEDULE)})")
      assertEquals(1, scalar(control, "SELECT count(*) FROM cron.job WHERE jobid = $unrelated"))
      assertEquals(1, scalar(control, "SELECT count(*) FROM cron.job WHERE jobid = $firstJob"))
      first.removeMaintenanceJob()
      first.removeMaintenanceJob()
      assertEquals(0, scalar(control, "SELECT count(*) FROM cron.job WHERE jobid = $firstJob"))
      assertEquals(1, scalar(control, "SELECT count(*) FROM cron.job WHERE jobname = '${PgCron.CLEANUP}'"))
      sql(control, "SELECT cron.unschedule($unrelated)")
    }
  }

  private class RetryStorage : PgStorage() {
    fun open(config: PgConfig) { initializeStorage(config, true, true) }
  }

  private fun storage(base: PgInstanceConfig, name: String, id: String): PgStorage = PgStorage(configuration(base, name, id), true, true)

  private fun configuration(base: PgInstanceConfig, name: String, id: String): PgConfig = PgConfig(id).apply {
    className = "naksha.psql.PgStorage"
    master = PgInstanceConfig.fromUri(targetUrl(base, name))
    maintenanceJobs = true
    maintenanceSslMode = "disable"
  }

  private fun targetUrl(base: PgInstanceConfig, name: String): String = base.copy<PgInstanceConfig>().withDb(name).toString().replace("ssl=true", "ssl=false")

  private fun write(storage: PgStorage, request: WriteRequest) {
    storage.newWriteSession(Naksha.useAdminOptions()).use { session ->
      assertIs<SuccessResponse>(session.execute(request))
      session.commit()
    }
  }

  private fun databaseOid(conn: Connection, name: String): Long = text(conn, "SELECT oid::text FROM pg_database WHERE datname = ${PgUtil.quoteLiteral(name)}").toLong()
  private fun job(conn: Connection, name: String): Long = text(conn, "SELECT jobid::text FROM cron.job WHERE jobname = ${PgUtil.quoteLiteral(name)}").toLong()
  private fun scalar(conn: Connection, sql: String): Int = text(conn, sql).toInt()
  private fun text(conn: Connection, sql: String): String = conn.createStatement().use { query ->
    query.executeQuery(sql).use { rows -> assertTrue(rows.next()); assertNotNull(rows.getString(1)) }
  }
  private fun sql(conn: Connection, sql: String) { conn.createStatement().use { it.execute(sql) } }
  private fun await(message: String, ready: () -> Boolean) {
    val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(90)
    while (System.nanoTime() < deadline) {
      if (ready()) return
      Thread.sleep(250)
    }
    assertTrue(ready(), message)
  }
}
