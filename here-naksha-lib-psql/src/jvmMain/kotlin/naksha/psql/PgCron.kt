package naksha.psql

import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException
import java.util.Properties
import naksha.base.Id
import naksha.base.NakshaError
import naksha.base.NakshaException
import naksha.base.illegalState
import naksha.psql.PgUtil.PgUtilCompanion.quoteLiteral

/** Installs cluster-local pg_cron metadata after database bootstrap commits. Never changes server configuration, restarts PostgreSQL, stores credentials, or commits caller transactions. @since 3.0 */
internal object PgCron {
  internal const val SCHEDULE = "0 */8 * * *"
  internal const val PREFIX = "naksha-maintain-db-"
  internal const val CLEANUP = "naksha-maintenance-orphans"
  private const val MARKER = "-- naksha.psql.maintenance.v1"
  private const val CONTROL = "naksha~cron"

  private class Target(val name: String, val oid: Long, val schema: Long, val version: Long, val cronDatabase: String)

  /** Ensure exactly one current-role job for this verified database identity plus one shared cleanup job. Initialization failures after database commit are safe to retry. @since 3.0 */
  fun ensure(instance: PgInstance, storageId: Id, sslMode: String = "verify-full") {
    try {
      val target = target(instance, storageId, sslMode)
      connect(instance, target.cronDatabase, sslMode).use { conn ->
        conn.autoCommit = false
        try {
          execute(conn, "SET LOCAL lock_timeout = '10s'; SET LOCAL statement_timeout = '60s'")
          execute(conn, "SELECT pg_catalog.pg_advisory_xact_lock(1852531051, 1668444014)")
          execute(conn, "CREATE EXTENSION IF NOT EXISTS pg_cron")
          ownControlSchema(conn)
          reconcile(conn, CLEANUP, target.cronDatabase, cleanupCommand())
          reconcile(conn, PREFIX + target.oid, target.name, maintenanceCommand(target, storageId))
          conn.commit()
        } catch (failure: Throwable) {
          conn.rollback()
          throw failure
        }
      }
    } catch (failure: Throwable) {
      // JDBC failures can contain endpoints and server-auth diagnostics. Report a bounded, credential-free setup error, not connection configuration or SQL text.
      val detail = if (failure is NakshaException) failure.error.msg else "SQLSTATE ${(failure as? SQLException)?.sqlState ?: "unavailable"}"
      throw NakshaException(NakshaError.INITIALIZATION_FAILED,
        "Naksha database initialization committed, but pg_cron maintenance setup failed ($detail). Configure preloaded pg_cron, its designated database, a shared installer/maintenance role and server-side job authentication, then retry initialization; or explicitly set maintenanceJobs=false for external scheduling.")
    }
  }

  /** Remove only a verified target's managed job; shared cleanup and unrelated jobs remain installed. @since 3.0 */
  fun remove(instance: PgInstance, storageId: Id, sslMode: String = "verify-full") {
    val target = target(instance, storageId, sslMode)
    connect(instance, target.cronDatabase, sslMode).use { conn ->
      conn.autoCommit = false
      try {
        execute(conn, "SET LOCAL lock_timeout = '10s'; SET LOCAL statement_timeout = '60s'")
        execute(conn, "SELECT pg_catalog.pg_advisory_xact_lock(1852531051, 1668444014)")
        verifyControlSchema(conn)
        conn.prepareStatement("SELECT jobid, database, command FROM cron.job WHERE jobname = ? AND username = current_user").use { query ->
          query.setString(1, PREFIX + target.oid)
          query.executeQuery().use { rows ->
            if (rows.next()) {
              if (rows.getString("database") != target.name || !rows.getString("command").startsWith(MARKER)) throw illegalState("Maintenance job identity does not match the target database")
              unschedule(conn, rows.getLong("jobid"))
            }
          }
        }
        conn.commit()
      } catch (failure: Throwable) {
        conn.rollback()
        throw failure
      }
    }
  }

  private fun target(instance: PgInstance, storageId: Id, sslMode: String): Target {
    connect(instance, instance.database, sslMode).use { conn ->
      conn.createStatement().use { query ->
        query.executeQuery("SELECT d.datname, d.oid::bigint AS db_oid, n.oid::bigint AS schema_oid, " +
          "pg_catalog.current_setting('cron.database_name', true) AS cron_db, " +
          "pg_catalog.current_setting('shared_preload_libraries') AS preload, " +
          "\"naksha~admin\".naksha_storage_id() AS storage_id, \"naksha~admin\".naksha_storage_number()::text AS storage_number, " +
          "\"naksha~admin\".naksha_version() AS admin_version " +
          "FROM pg_catalog.pg_database d CROSS JOIN pg_catalog.pg_namespace n " +
          "WHERE d.datname = current_database() AND n.nspname = 'naksha~admin'").use { rows ->
          if (!rows.next() || rows.getString("storage_id") != storageId.text || rows.getString("storage_number") != storageId.number.toString()) throw illegalState("Maintenance target is not the initialized Naksha storage")
          val cronDatabase = rows.getString("cron_db")
          if (cronDatabase.isNullOrEmpty() || !rows.getString("preload").split(',').any { it.trim().trim('"') == "pg_cron" }) throw illegalState("pg_cron must be configured in shared_preload_libraries before database installation; no server changes were made")
          return Target(rows.getString("datname"), rows.getLong("db_oid"), rows.getLong("schema_oid"), rows.getLong("admin_version"), cronDatabase)
        }
      }
    }
  }

  private fun connect(instance: PgInstance, database: String, sslMode: String): Connection {
    if (sslMode != "verify-full" && sslMode != "disable") throw illegalState("maintenanceSslMode must be verify-full or an explicit disable for local test connections")
    val properties = Properties()
    properties.setProperty("user", instance.user)
    properties.setProperty("password", instance.password)
    properties.setProperty("connectTimeout", "10")
    properties.setProperty("socketTimeout", "70")
    properties.setProperty("ApplicationName", "naksha-maintenance-install")
    properties.setProperty("sslmode", sslMode)
    // Keep connection secrets out of the URL, cron metadata and errors. The same endpoint/role and explicit TLS policy are used for both databases.
    val host = if (instance.host.contains(':')) "[${instance.host}]" else instance.host
    val name = java.net.URLEncoder.encode(database, Charsets.UTF_8.name()).replace("+", "%20")
    return DriverManager.getConnection("jdbc:postgresql://$host:${instance.port}/$name", properties)
  }

  private fun ownControlSchema(conn: Connection) {
    conn.prepareStatement("SELECT 1 FROM pg_catalog.pg_namespace WHERE nspname = ?").use { query ->
      query.setString(1, CONTROL)
      query.executeQuery().use { rows ->
        if (!rows.next()) {
          execute(conn, "CREATE SCHEMA \"naksha~cron\"; REVOKE ALL ON SCHEMA \"naksha~cron\" FROM PUBLIC; COMMENT ON SCHEMA \"naksha~cron\" IS 'naksha.psql.cron.v1'")
        }
      }
    }
    verifyControlSchema(conn)
  }

  private fun verifyControlSchema(conn: Connection) {
    conn.createStatement().use { query ->
      query.executeQuery("SELECT n.nspowner = (SELECT oid FROM pg_catalog.pg_roles WHERE rolname = current_user) " +
        "AND pg_catalog.obj_description(n.oid, 'pg_namespace') = 'naksha.psql.cron.v1' AS owned " +
        "FROM pg_catalog.pg_namespace n WHERE n.nspname = 'naksha~cron'").use { rows ->
        if (!rows.next() || !rows.getBoolean("owned")) throw illegalState("The cron control schema must belong to the shared Naksha installer/maintenance role")
      }
    }
  }

  private fun reconcile(conn: Connection, name: String, database: String, command: String) {
    var job: Long? = null
    conn.prepareStatement("SELECT jobid, schedule, command, database, active FROM cron.job WHERE jobname = ? AND username = current_user").use { query ->
      query.setString(1, name)
      query.executeQuery().use { rows ->
        if (rows.next()) {
          job = rows.getLong("jobid")
          if (!rows.getString("command").startsWith(MARKER)) throw illegalState("A non-Naksha job occupies the maintenance job name")
          if (rows.getString("schedule") == SCHEDULE && rows.getString("command") == command && rows.getString("database") == database && rows.getBoolean("active")) return
        }
      }
    }
    if (job != null) {
      conn.prepareStatement("SELECT cron.alter_job(?, schedule := ?, command := ?, database := ?, active := true)").use { query ->
        query.setLong(1, job!!); query.setString(2, SCHEDULE); query.setString(3, command); query.setString(4, database)
        query.execute()
      }
      return
    }
    conn.prepareStatement("SELECT cron.schedule_in_database(?, ?, ?, ?)").use { query ->
      query.setString(1, name); query.setString(2, SCHEDULE); query.setString(3, command); query.setString(4, database)
      query.execute()
    }
  }

  private fun unschedule(conn: Connection, job: Long) {
    conn.prepareStatement("SELECT cron.unschedule(?)").use { query -> query.setLong(1, job); query.execute() }
  }

  private fun execute(conn: Connection, sql: String) {
    conn.createStatement().use { it.execute(sql) }
  }

  private fun maintenanceCommand(target: Target, storageId: Id): String {
    val body = """
DECLARE checked integer;
BEGIN
  IF (SELECT oid::bigint FROM pg_catalog.pg_database WHERE datname = current_database()) IS DISTINCT FROM ${target.oid}
     OR pg_catalog.to_regnamespace('"naksha~admin"')::bigint IS DISTINCT FROM ${target.schema} THEN
    RAISE EXCEPTION 'Naksha maintenance target was replaced; reinstall its maintenance job';
  END IF;
  IF "naksha~admin".naksha_storage_id() IS DISTINCT FROM ${quoteLiteral(storageId.text)}
     OR "naksha~admin".naksha_storage_number() IS DISTINCT FROM ${storageId.number}::bigint
     OR "naksha~admin".naksha_version() IS DISTINCT FROM ${target.version}::bigint THEN
    RAISE EXCEPTION 'Naksha maintenance storage identity or runtime changed; reconcile its maintenance job';
  END IF;
  IF NOT pg_catalog.pg_try_advisory_xact_lock(1852531051, 1835100526) THEN
    RAISE LOG 'Naksha maintenance skipped: another sweep is running';
    RETURN;
  END IF;
  checked := "naksha~admin".naksha_maintain();
  RAISE LOG 'Naksha partition maintenance completed: % collections checked', checked;
END
""".trimIndent()
    return "$MARKER\nSET statement_timeout = '30min'; SET lock_timeout = '1s'; DO ${quoteLiteral(body)};"
  }

  /** Operates entirely on cron and cluster catalogs, never connects to target databases and never touches unrelated jobs. Names include the database OID so DROP/recreate cannot adopt an old job. @since 3.0 */
  internal fun cleanupCommand(): String = """
$MARKER
SET statement_timeout = '1min'; SET lock_timeout = '1s';
DO ${'$'}naksha${'$'}
DECLARE job record;
BEGIN
  IF NOT pg_catalog.pg_try_advisory_xact_lock(1852531051, 1668444014) THEN RETURN; END IF;
  FOR job IN SELECT j.jobid FROM cron.job j
    WHERE j.username = current_user AND j.jobname ~ '^naksha-maintain-db-[0-9]+${'$'}'
      AND pg_catalog.starts_with(j.command, ${quoteLiteral(MARKER)})
      AND NOT EXISTS (SELECT 1 FROM pg_catalog.pg_database d
        WHERE d.datname = j.database AND j.jobname = '$PREFIX' || d.oid::text)
  LOOP
    PERFORM cron.unschedule(job.jobid);
  END LOOP;
  DELETE FROM cron.job_run_details r WHERE r.username = current_user
    AND r.end_time < pg_catalog.clock_timestamp() - interval '30 days'
    AND pg_catalog.starts_with(r.command, ${quoteLiteral(MARKER)});
END
${'$'}naksha${'$'};
""".trimIndent()
}
