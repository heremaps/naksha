package naksha.psql

import naksha.base.Version
import naksha.model.Naksha
import naksha.model.objects.Member
import naksha.model.objects.MemberType
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.postgresql.util.PSQLException
import java.sql.DriverManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@EnabledIfEnvironmentVariable(named = "NAKSHA_TEST_PLV8_DB_URL", matches = ".+")
class PgPlv8MaintenanceTest {
  @Test
  fun maintainsThroughRealPlv8InCallerTransaction() {
    val url = System.getenv("NAKSHA_TEST_PLV8_DB_URL")
    assertEquals(url, System.getenv("NAKSHA_TEST_PSQL_DB_URL"),
      "Both database URL variables must point to the same fresh dedicated database")
    DriverManager.getConnection(url).use { jdbc ->
      jdbc.createStatement().use { statement ->
        statement.executeQuery("SELECT 1 FROM pg_namespace WHERE nspname = 'naksha~admin'").use { rows ->
          assertFalse(rows.next(), "Requires a fresh database; this test never deletes existing schemas")
        }
      }
    }

    val config = PgConfig("plv8_maintenance_probe").apply {
      className = "naksha.psql.PgStorage"
      master = PgInstanceConfig.fromUri(url)
    }
    val storage = PgStorage(config, true, false)
    assertTrue(storage.initialized)
    val database = storage.database
    val schema = "PLV8 Maintenance"
    val table = "history_root"
    val logicalCatalog = "Original \"Catalog"
    val logicalCollection = "Original.Collection"
    val root = PgUtil.quoteIdent(schema) + "." + PgUtil.quoteIdent(table)
    val payload = PgColumn(3, Member("payload", MemberType.STRING), "STORAGE MAIN COLLATE \"C\"")
    val sequence = "\"naksha~admin\"." + PgUtil.quoteIdent(NAKSHA_VERSION_SEQ)
    val registry = "\"naksha~admin\".naksha_partman"

    // Independent JVM connection proves bootstrap and module installation were committed.
    storage.newConnection(Naksha.useAdminOptions(), false, PgStorage.SKIP_INIT).use { conn ->
      conn.autoCommit = false
      try {
        conn.execute("SET LOCAL lock_timeout = '5s'; SET LOCAL statement_timeout = '120s'").close()
        val comment = assertNotNull(scalar(conn, """
          SELECT obj_description(oid, 'pg_namespace') FROM pg_namespace WHERE nspname = 'naksha~admin'
        """))
        assertFalse(comment.contains("password", ignoreCase = true))
        val password = config.master.password
        if (password.isNotEmpty()) assertFalse(comment.contains(password))
        val attached = PgLocalConfig.readFromSchemaComment(conn)
        assertEquals(config.id, attached.id)
        assertEquals("", attached.master.password)
        assertEquals(scalar(conn, "SELECT current_database()"), attached.master.db)
        assertEquals(scalar(conn, "SELECT current_user::text"), attached.master.user)
        assertFalse(attached.create)
        assertFalse(attached.upgrade)
        // Never open a JVM connection with the ambient, credential-free configuration.
        assertEquals("1", scalar(conn, """
          SELECT count(*) FROM "naksha~admin".es_modules
          WHERE name = 'naksha_psql' AND source LIKE '%naksha_maintain%'
        """))
        assertTrue(database.catalog_get_or_create_schema(conn, schema, logicalCatalog) > 0)
        database.collection_create_table_if_not_exists(
          conn = conn, schema = schema, table = table,
          columns = arrayOf(PgColumn.FnColumn, PgColumn.VersionColumn, PgColumn.NextVersionColumn, payload),
          indices = emptyArray(),
          options = PgCollectionCreateOptions(true, 3, 12, 10, -1, 3,
            PgStorageClass.Consistent, null, 2048),
          logicalCatalog = logicalCatalog, logicalCollection = logicalCollection
        )
        assertEquals(PgUtil.quoteIdent(logicalCatalog) + "." + PgUtil.quoteIdent(logicalCollection),
          scalar(conn, "SELECT obj_description(${literal(root)}::regclass, 'pg_class')"))
        conn.execute("""
          INSERT INTO $root (fn, version, nv, ${payload.ident})
          VALUES (${Long.MIN_VALUE}, 3, ${Version.HEAD.number}, NULL),
            (${Long.MAX_VALUE}, 7, ${Version.HEAD.number}, 'preserved')
        """).close()
        conn.commit()
        println("PLV8 verification: committed JVM bootstrap, credential-free config, modules, and history fixture")

        // A direct JDBC backend must initialize PLV8 through the installed wrapper, before the alias fixture does.
        DriverManager.getConnection(url).use { cold ->
          cold.autoCommit = false
          try {
            assertFalse(scalar(conn, "SELECT pg_backend_pid()") == scalar(cold, "SELECT pg_backend_pid()"))
            cold.createStatement().use { statement ->
              statement.executeQuery("""
                SELECT p.provolatile, p.proparallel, p.prosecdef, p.proconfig,
                  NOT EXISTS (
                    SELECT 1 FROM aclexplode(coalesce(p.proacl, acldefault('f', p.proowner))) acl
                    WHERE acl.grantee = 0 AND acl.privilege_type = 'EXECUTE'
                  ) AS public_cannot_execute
                FROM pg_proc p WHERE p.oid = '"naksha~admin".naksha_maintain()'::regprocedure
              """).use { rows ->
                assertTrue(rows.next(), "Bootstrap must install the maintenance wrapper")
                assertEquals("v", rows.getString("provolatile"))
                assertEquals("u", rows.getString("proparallel"))
                assertFalse(rows.getBoolean("prosecdef"), "Maintenance must run as the caller")
                assertEquals(listOf("search_path=\"naksha~admin\", pg_catalog"),
                  (rows.getArray("proconfig").array as Array<*>).toList())
                assertTrue(rows.getBoolean("public_cannot_execute"), "PUBLIC must not have EXECUTE privilege")
                assertFalse(rows.next())
              }
            }
            val coldSettings = sessionSettings(cold)
            val coldExpected = assertNotNull(scalar(cold, "SELECT count(*) FROM $registry")).toInt()
            assertTrue(coldExpected > 0)
            assertEquals(coldExpected.toString(), scalar(cold, "SELECT \"naksha~admin\".naksha_maintain()"),
              "The first installed-wrapper call must initialize a cold PLV8 runtime without session settings")
            assertEquals(coldSettings, sessionSettings(cold))
            val coldAfter = snapshot(cold)
            assertEquals(coldExpected.toString(), scalar(cold, "SELECT \"naksha~admin\".naksha_maintain()"))
            assertEquals(coldAfter, snapshot(cold), "Repeated installed-wrapper calls must be idempotent")
            assertEquals(coldSettings, sessionSettings(cold))
            println("PLV8 verification: installed wrapper metadata, trusted search_path, PUBLIC ACL, cold initialization and idempotency")
          } finally {
            cold.rollback()
          }
        }

        conn.execute("SET LOCAL lock_timeout = '5s'; SET LOCAL statement_timeout = '120s'").close()
        conn.execute("SET LOCAL search_path TO pg_catalog, public").close()
        val settings = sessionSettings(conn)
        conn.execute("""
          INSERT INTO "naksha~admin".es_modules (name, source, autoload) VALUES
          ('plv8_alias_probe_a', ${literal("""
            function first() { return 'first'; }
            function second() { return 'second'; }
            export { first as x, second as y };
          """.trimIndent())}, false),
          ('plv8_alias_probe_b', ${literal("""
            import { x as second, y as first } from 'plv8_alias_probe_a';
            export { second as left, first as right };
          """.trimIndent())}, false),
          ('plv8_alias_probe_c', ${literal("""
            import { x as first, y as second } from 'plv8_alias_probe_a';
            export { first as left, second as right };
          """.trimIndent())}, false)
        """).close()
        conn.execute("""
          CREATE FUNCTION pg_temp.loader_alias_isolation() RETURNS boolean
          LANGUAGE plv8 VOLATILE PARALLEL UNSAFE
          SET search_path = 'naksha~admin', pg_catalog, public, topology AS ${'$'}${'$'}
            if (typeof require !== "function") plv8.find_function('"naksha~admin".es_modules_init')();
            const a = require('plv8_alias_probe_a');
            const originalFirst = a.first, originalSecond = a.second;
            const b = require('plv8_alias_probe_b');
            const c = require('plv8_alias_probe_c');
            return b.second() === 'first' && b.first() === 'second'
              && c.first() === 'first' && c.second() === 'second'
              && b.second === originalFirst && b.first === originalSecond
              && c.first === originalFirst && c.second === originalSecond
              && require('plv8_alias_probe_a') === a
              && a.first === originalFirst && a.second === originalSecond
              && a.first() === 'first' && a.second() === 'second';
          ${'$'}${'$'}
        """).close()
        assertEquals("true", scalar(conn, "SELECT pg_temp.loader_alias_isolation()::text"),
          "Crossed and later normal import aliases must not mutate cached module exports")
        assertEquals(settings, sessionSettings(conn))
        println("PLV8 verification: crossed import aliases preserve later imports and cached original exports")
        conn.execute("""
          CREATE FUNCTION pg_temp.bigint_transport() RETURNS text
          LANGUAGE plv8 VOLATILE PARALLEL UNSAFE AS ${'$'}${'$'}
            const row = plv8.execute("SELECT '-9223372036854775808'::bigint AS lo, '9223372036854775807'::bigint AS hi")[0];
            return String(row.lo) + ',' + String(row.hi);
          ${'$'}${'$'}
        """).close()
        assertEquals("${Long.MIN_VALUE},${Long.MAX_VALUE}", scalar(conn, "SELECT pg_temp.bigint_transport()"),
          "PLV8 bigint transport must preserve both signed 64-bit boundaries exactly")
        conn.execute("SELECT '${Long.MIN_VALUE}'::bigint AS lo, '${Long.MAX_VALUE}'::bigint AS hi").fetch().use { cursor ->
          assertEquals(Long.MIN_VALUE, cursor.get<Long>("lo"))
          assertEquals(Long.MAX_VALUE, cursor.get<Long>("hi"))
        }
        println("PLV8 verification: exact signed bigint transport through PLV8 and JVM cursors")

        val anchor = PgPartitions.currentVersion(conn) + 16L * 4096L
        conn.execute("SELECT setval(${literal(sequence)}::regclass, $anchor, true)").close()
        val sequenceBefore = scalar(conn, "SELECT ROW(last_value, is_called)::text FROM $sequence")
        val future = PgUtil.quoteIdent(schema) + "." + PgUtil.quoteIdent(table + "$" + (anchor shr 12))
        assertNull(scalar(conn, "SELECT to_regclass(${literal(future)})"))
        val before = snapshot(conn)
        val rowsBefore = scalar(conn, "SELECT jsonb_agg(to_jsonb(r) ORDER BY fn)::text FROM $root r")
        val expected = scalar(conn, "SELECT count(*) FROM $registry")!!.toInt()
        assertTrue(expected > 0)
        val savepoint = conn.jdbc.setSavepoint()
        try {
          assertEquals(expected.toString(), scalar(conn, "SELECT \"naksha~admin\".naksha_maintain()"))
          assertEquals(settings, sessionSettings(conn), "Attached PLV8 storage must not change caller session settings")
          assertNotNull(scalar(conn, "SELECT to_regclass(${literal(future)})"))
          assertEquals("3", scalar(conn, """
            SELECT count(*) FROM pg_partition_tree(${literal(future)}::regclass) WHERE isleaf
          """))
          val after = snapshot(conn)
          assertFalse(before == after, "SQL maintenance must create previously absent ranges")
          assertEquals(expected, database.maintain_partitions(conn))
          assertEquals(after, snapshot(conn), "JVM sweep after PLV8 must be idempotent")
          assertEquals(expected.toString(), scalar(conn, "SELECT \"naksha~admin\".naksha_maintain()"))
          assertEquals(after, snapshot(conn), "Repeated PLV8 invocation must attach afresh without DDL changes")
          assertEquals(sequenceBefore, scalar(conn, "SELECT ROW(last_value, is_called)::text FROM $sequence"))
          assertEquals(rowsBefore, scalar(conn, "SELECT jsonb_agg(to_jsonb(r) ORDER BY fn)::text FROM $root r"))
        } finally {
          conn.jdbc.rollback(savepoint)
          conn.jdbc.releaseSavepoint(savepoint)
        }
        assertNull(scalar(conn, "SELECT to_regclass(${literal(future)})"), "Caller rollback must remove PLV8-created ranges")
        assertEquals(before, snapshot(conn))
        assertEquals(rowsBefore, scalar(conn, "SELECT jsonb_agg(to_jsonb(r) ORDER BY fn)::text FROM $root r"))
        assertEquals(settings, sessionSettings(conn))
        assertFalse(conn.isClosed())
        assertFalse(conn.autoCommit)
        println("PLV8 verification: registry count, new ranges, JVM/SQL idempotency, unchanged allocator/settings/data, caller rollback")

        val fixturePoint = conn.jdbc.setSavepoint()
        try {
          val failureTable = "z_failure"
          database.collection_create_table_if_not_exists(
            conn = conn, schema = schema, table = failureTable,
            columns = arrayOf(PgColumn.FnColumn, PgColumn.VersionColumn, PgColumn.NextVersionColumn, payload),
            indices = emptyArray(),
            options = PgCollectionCreateOptions(false, 3, 12, 10, -1, 3,
              PgStorageClass.Consistent, null, 2048),
            logicalCatalog = logicalCatalog, logicalCollection = "Failure.Collection"
          )
          database.collection_register_partitions(conn, schema, failureTable, 12, 3, 2048, 50, 100, null)
          val conflict = PgUtil.quoteIdent(schema) + "." +
            PgUtil.quoteIdent(failureTable + "$" + ((anchor shr 12) + 2))
          conn.execute("CREATE TABLE $conflict (unrelated text)").close()
          assertEquals("history_root,z_failure", scalar(conn, """
            SELECT string_agg(table_name, ',' ORDER BY table_name COLLATE "C") FROM $registry
            WHERE schema_name = ${literal(schema)}
          """))
          val failedBefore = snapshot(conn)
          val failurePoint = conn.jdbc.setSavepoint()
          try {
            val failure = assertFailsWith<PSQLException> { scalar(conn, "SELECT \"naksha~admin\".naksha_maintain()") }
            assertTrue(failure.message.orEmpty().contains(failureTable), failure.message)
          } finally {
            conn.jdbc.rollback(failurePoint)
            conn.jdbc.releaseSavepoint(failurePoint)
          }
          assertEquals(failedBefore, snapshot(conn), "Failed SQL sweep must leave no partial provisioning after caller rollback")
          assertNull(scalar(conn, "SELECT to_regclass(${literal(future)})"))
          assertEquals(rowsBefore, scalar(conn, "SELECT jsonb_agg(to_jsonb(r) ORDER BY fn)::text FROM $root r"))
          assertEquals(sequenceBefore, scalar(conn, "SELECT ROW(last_value, is_called)::text FROM $sequence"))
          assertEquals(settings, sessionSettings(conn))

          conn.execute("DROP TABLE $conflict").close()
          assertEquals((expected + 1).toString(), scalar(conn, "SELECT \"naksha~admin\".naksha_maintain()"))
          assertNotNull(scalar(conn, "SELECT to_regclass(${literal(future)})"))
          assertEquals("3", scalar(conn, """
            SELECT count(*) FROM pg_partition_tree(${literal(conflict)}::regclass) WHERE isleaf
          """))
          assertEquals(rowsBefore, scalar(conn, "SELECT jsonb_agg(to_jsonb(r) ORDER BY fn)::text FROM $root r"))
          assertEquals(sequenceBefore, scalar(conn, "SELECT ROW(last_value, is_called)::text FROM $sequence"))
          assertEquals(settings, sessionSettings(conn))
        } finally {
          conn.jdbc.rollback(fixturePoint)
          conn.jdbc.releaseSavepoint(fixturePoint)
        }
        assertEquals(before, snapshot(conn))
        assertNull(scalar(conn, "SELECT to_regclass(${literal(future)})"))
        println("PLV8 verification: partial sweep failure propagates, caller rollback restores snapshot, retry succeeds")

        for (invalid in arrayOf<String?>(null, "{}", comment.replace("\"storageId\":\"${config.id}\"", "\"storageId\":\"other_storage\""))) {
          assertFalse(invalid == comment, "Identity mismatch fixture must change the comment")
          val failurePoint = conn.jdbc.setSavepoint()
          try {
            conn.execute("COMMENT ON SCHEMA \"naksha~admin\" IS " + (invalid?.let { literal(it) } ?: "NULL")).close()
            val failure = assertFailsWith<PSQLException> { scalar(conn, "SELECT \"naksha~admin\".naksha_maintain()") }
            assertTrue(failure.message.orEmpty().contains("local config", ignoreCase = true), failure.message)
          } finally {
            conn.jdbc.rollback(failurePoint)
            conn.jdbc.releaseSavepoint(failurePoint)
          }
          assertEquals(comment, scalar(conn, """
            SELECT obj_description(oid, 'pg_namespace') FROM pg_namespace WHERE nspname = 'naksha~admin'
          """))
          assertEquals(before, snapshot(conn))
          assertEquals(settings, sessionSettings(conn))
        }
        println("PLV8 verification: missing, malformed, and mismatched schema comments propagate clear SQL errors")
      } finally {
        conn.rollback()
      }
    }
  }

  private fun sessionSettings(conn: PgConnection): String? = sessionSettings(conn.jdbc)

  private fun sessionSettings(conn: java.sql.Connection): String? = scalar(conn, """
    SELECT string_agg(name || '=' || setting, '|' ORDER BY name) FROM pg_settings
    WHERE name IN ('search_path', 'application_name', 'default_transaction_read_only',
      'transaction_read_only', 'lock_timeout', 'statement_timeout', 'TimeZone')
  """)

  private fun snapshot(conn: PgConnection): String? = snapshot(conn.jdbc)

  private fun snapshot(conn: java.sql.Connection): String? = scalar(conn, """
    SELECT coalesce(string_agg(ROW(c.oid, n.nspname, c.relname, c.relkind, c.reloptions,
      obj_description(c.oid, 'pg_class'), pg_get_expr(c.relpartbound, c.oid))::text, '|' ORDER BY c.oid), '')
    FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
    WHERE n.nspname IN ('naksha~admin', 'PLV8 Maintenance')
  """)

  private fun scalar(conn: PgConnection, sql: String): String? = scalar(conn.jdbc, sql)

  private fun scalar(conn: java.sql.Connection, sql: String): String? {
    conn.createStatement().use { statement ->
      statement.executeQuery(sql).use { rows ->
        assertTrue(rows.next(), "Expected one result row")
        val value = rows.getString(1)
        assertFalse(rows.next(), "Expected only one result row")
        return value
      }
    }
  }

  private fun literal(value: String) = PgUtil.quoteLiteral(value)
}
