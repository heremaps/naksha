package naksha.psql

import naksha.model.Naksha
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.postgresql.util.PSQLException
import java.sql.Connection
import java.sql.DriverManager
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@EnabledIfEnvironmentVariable(named = "NAKSHA_TEST_PERMISSIONS_DB_URL", matches = ".+")
class PgMaintenancePermissionsTest {
  private val admin = "\"naksha~admin\""
  private val wrapper = "$admin.naksha_maintain()"
  private val registry = "$admin.naksha_partman"
  private val modules = "$admin.es_modules"
  private val sequence = "$admin.${PgUtil.quoteIdent(NAKSHA_VERSION_SEQ)}"

  @Test
  fun enforcesInvokerPermissionsOnColdBackends() {
    val url = System.getenv("NAKSHA_TEST_PERMISSIONS_DB_URL")
    assertEquals(url, System.getenv("NAKSHA_TEST_PSQL_DB_URL"),
      "Both database URL variables must point to the same fresh dedicated database")
    DriverManager.getConnection(url).use { setup ->
      assertEquals("0", scalar(setup, "SELECT count(*) FROM pg_namespace WHERE nspname = 'naksha~admin'"),
        "Requires a fresh database; this test never deletes existing schemas")
      val config = PgConfig("maintenance_permissions_probe").apply {
        className = "naksha.psql.PgStorage"
        master = PgInstanceConfig.fromUri(url)
      }
      val storage = PgStorage(config, true, false)
      assertTrue(storage.initialized)
      val installer = assertNotNull(scalar(setup, "SELECT current_user::text"))
      val role = "naksha_maint_" + UUID.randomUUID().toString().replace("-", "")
      val quotedRole = PgUtil.quoteIdent(role)
      val schema = "Permissions Maintenance"
      val quotedSchema = PgUtil.quoteIdent(schema)
      val table = "role_history"
      val root = "$quotedSchema.${PgUtil.quoteIdent(table)}"
      var roleCreated = false
      try {
        execute(setup, "CREATE ROLE $quotedRole NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOREPLICATION NOBYPASSRLS")
        roleCreated = true
        execute(setup, "GRANT USAGE ON SCHEMA $admin, public TO $quotedRole")
        assertEquals("false", scalar(setup, "SELECT prosecdef::text FROM pg_proc WHERE oid = ${literal(wrapper)}::regprocedure"))
        assertEquals("false", scalar(setup, "SELECT has_function_privilege(${literal(role)}, ${literal(wrapper)}, 'EXECUTE')::text"))
        expectDenied(url, role, "function naksha_maintain")

        execute(setup, "GRANT EXECUTE ON FUNCTION $wrapper TO $quotedRole")
        expectDenied(url, role, "table es_modules")
        execute(setup, "GRANT SELECT ON $modules TO $quotedRole")
        expectDenied(url, role, "sequence $NAKSHA_VERSION_SEQ")
        execute(setup, "GRANT SELECT ON SEQUENCE $sequence TO $quotedRole")
        expectDenied(url, role, "table naksha_partman")
        execute(setup, "GRANT SELECT ON $registry TO $quotedRole")

        val managed = ArrayList<String>()
        setup.createStatement().use { statement ->
          statement.executeQuery("""
            SELECT t.relid::regclass::text FROM $registry r
            CROSS JOIN LATERAL pg_partition_tree(r.root_oid::regclass) t
            ORDER BY r.schema_name COLLATE "C", r.table_name COLLATE "C", t.level, t.relid
          """).use { rows ->
            while (rows.next()) managed.add(rows.getString(1))
          }
        }
        assertTrue(managed.isNotEmpty(), "Bootstrap must register admin history roots")
        expectDenied(url, role, "table")

        // DML permits the recursive lock, but cannot authorize CREATE TABLE ... PARTITION OF.
        execute(setup, "GRANT USAGE, CREATE ON SCHEMA $admin TO $quotedRole")
        for (relation in managed) execute(setup, "GRANT UPDATE ON TABLE $relation TO $quotedRole")
        val anchor = storage.newConnection(Naksha.useAdminOptions(), false, PgStorage.SKIP_INIT).use { conn ->
          PgPartitions.currentVersion(conn) + 16L * 4096L
        }
        execute(setup, "SELECT setval(${literal(sequence)}::regclass, $anchor, true)")
        val missingLeaf = assertNotNull(scalar(setup, """
          SELECT t.relid::regclass::text FROM $registry r
          CROSS JOIN LATERAL pg_partition_tree(r.root_oid::regclass) t
          WHERE t.isleaf ORDER BY r.schema_name COLLATE "C", r.table_name COLLATE "C", t.relid LIMIT 1
        """))
        execute(setup, "DROP TABLE $missingLeaf")
        assertTrue(managed.remove(missingLeaf))
        expectDenied(url, role, "must be owner of table")
        for (relation in managed) {
          execute(setup, "REVOKE UPDATE ON TABLE $relation FROM $quotedRole")
          execute(setup, "ALTER TABLE $relation OWNER TO $quotedRole")
        }

        // Only managed tables change owner, not the admin schema, wrapper, metadata or allocator.
        execute(setup, "CREATE SCHEMA $quotedSchema AUTHORIZATION $quotedRole")
        execute(setup, "GRANT INSERT ON $registry TO $quotedRole")
        storage.newConnection(Naksha.useAdminOptions(), false, PgStorage.SKIP_INIT).use { conn ->
          try {
            conn.execute("SET LOCAL ROLE $quotedRole").close()
            assertEquals(role, scalar(conn.jdbc, "SELECT current_user::text"))
            storage.database.collection_create_table_if_not_exists(
              conn = conn, schema = schema, table = table,
              columns = arrayOf(PgColumn.FnColumn, PgColumn.VersionColumn, PgColumn.NextVersionColumn),
              indices = emptyArray(),
              options = PgCollectionCreateOptions(true, 3, 12, 10, -1, 3,
                PgStorageClass.Consistent, null, 2048),
              logicalCatalog = schema, logicalCollection = table
            )
            conn.commit()
          } finally {
            conn.rollback()
          }
        }
        execute(setup, "REVOKE INSERT ON $registry FROM $quotedRole")
        execute(setup, "SELECT setval(${literal(sequence)}::regclass, ${anchor + 16L * 4096L}, true)")
        val future = "$quotedSchema.${PgUtil.quoteIdent(table + "$" + ((anchor shr 12) + 16))}"
        assertNull(scalar(setup, "SELECT to_regclass(${literal(future)})"))
        val expected = assertNotNull(scalar(setup, "SELECT count(*) FROM $registry"))
        assertTrue(expected.toInt() > 1, "The global sweep must include admin roots and the role-created root")
        val allocator = scalar(setup, "SELECT ROW(last_value, is_called)::text FROM $sequence")
        assertEquals("0", scalar(setup, """
          SELECT count(*) FROM pg_class WHERE oid IN (
            ${literal(modules)}::regclass, ${literal(registry)}::regclass, ${literal(sequence)}::regclass
          ) AND relowner = ${literal(role)}::regrole
        """))
        assertEquals(installer, scalar(setup, "SELECT pg_get_userbyid(nspowner) FROM pg_namespace WHERE nspname = 'naksha~admin'"))
        assertEquals(installer, scalar(setup, "SELECT pg_get_userbyid(proowner) FROM pg_proc WHERE oid = ${literal(wrapper)}::regprocedure"))

        DriverManager.getConnection(url).use { cold ->
          cold.autoCommit = false
          try {
            enterRole(cold, role)
            assertFalse(scalar(setup, "SELECT pg_backend_pid()") == scalar(cold, "SELECT pg_backend_pid()"))
            assertEquals("0", scalar(cold, "SELECT count(*) FROM pg_auth_members WHERE member = current_user::regrole"),
              "The worker must not inherit the installer, postgres or any other role")
            assertEquals("false", scalar(cold, """
              SELECT (rolsuper OR rolcreaterole OR rolcreatedb OR rolreplication OR rolbypassrls OR rolcanlogin)::text
              FROM pg_roles WHERE rolname = current_user
            """))
            assertEquals("false", scalar(cold, "SELECT has_table_privilege(current_user, ${literal(registry)}, 'INSERT,UPDATE,DELETE,TRUNCATE')::text"))
            assertEquals("false", scalar(cold, "SELECT has_sequence_privilege(current_user, ${literal(sequence)}, 'USAGE,UPDATE')::text"))
            val settings = sessionSettings(cold)
            val before = snapshot(cold)
            assertEquals(expected, scalar(cold, "SELECT $wrapper"), "A cold invoker runtime must maintain every managed root")
            assertEquals(role, scalar(cold, "SELECT current_user::text"), "Maintenance must not escalate to the installer")
            assertEquals(settings, sessionSettings(cold))
            assertNotNull(scalar(cold, "SELECT to_regclass(${literal(future)})"))
            assertEquals("3", scalar(cold, "SELECT count(*) FROM pg_partition_tree(${literal(future)}::regclass) WHERE isleaf"))
            assertEquals("0", scalar(cold, """
              SELECT count(*) FROM $registry r
              CROSS JOIN LATERAL pg_partition_tree(r.root_oid::regclass) t
              JOIN pg_class c ON c.oid = t.relid WHERE c.relowner <> current_user::regrole
            """), "All managed roots, intermediate partitions and leaves must belong to the worker")
            assertEquals(role, scalar(cold, "SELECT pg_get_userbyid(relowner) FROM pg_class WHERE oid = ${literal(root)}::regclass"))
            val after = snapshot(cold)
            assertFalse(before == after, "The positive sweep must actually create partitions")
            assertEquals(expected, scalar(cold, "SELECT $wrapper"))
            assertEquals(after, snapshot(cold), "Repeated maintenance must be idempotent")
            assertEquals(allocator, scalar(cold, "SELECT ROW(last_value, is_called)::text FROM $sequence"))
            assertEquals(settings, sessionSettings(cold))
            cold.rollback()
            assertNull(scalar(cold, "SELECT to_regclass(${literal(future)})"), "Caller rollback must remove the newly created partitions")
            assertEquals(before, snapshot(cold))
            assertEquals(installer, scalar(cold, "SELECT current_user::text"), "Rollback must also restore SET LOCAL ROLE")
          } finally {
            cold.rollback()
          }
        }
        println("Permission verification: cold non-superuser sweep succeeds with managed-table ownership, schema CREATE, wrapper EXECUTE and metadata/sequence SELECT only")
      } finally {
        if (roleCreated) {
          // Remove this unique cluster role even after an assertion or SQL failure; never drop the supplied database.
          execute(setup, "REASSIGN OWNED BY $quotedRole TO ${PgUtil.quoteIdent(installer)}")
          execute(setup, "DROP OWNED BY $quotedRole")
          execute(setup, "DROP ROLE $quotedRole")
          assertEquals("0", scalar(setup, "SELECT count(*) FROM pg_roles WHERE rolname = ${literal(role)}"))
        }
      }
    }
  }

  private fun expectDenied(url: String, role: String, message: String) {
    DriverManager.getConnection(url).use { cold ->
      cold.autoCommit = false
      try {
        enterRole(cold, role)
        val settings = sessionSettings(cold)
        val savepoint = cold.setSavepoint()
        val failure = assertFailsWith<PSQLException> { scalar(cold, "SELECT $wrapper") }
        cold.rollback(savepoint)
        cold.releaseSavepoint(savepoint)
        assertEquals("42501", failure.sqlState, failure.message)
        assertTrue(failure.message.orEmpty().contains(message), failure.message)
        assertEquals(role, scalar(cold, "SELECT current_user::text"))
        assertEquals(settings, sessionSettings(cold))
        println("Permission verification: SQLSTATE ${failure.sqlState}: ${failure.serverErrorMessage?.message}")
      } finally {
        cold.rollback()
      }
    }
  }

  private fun enterRole(conn: Connection, role: String) {
    execute(conn, "SET LOCAL ROLE ${PgUtil.quoteIdent(role)}")
    execute(conn, "SET LOCAL search_path TO pg_catalog, public")
    execute(conn, "SET LOCAL lock_timeout = '5s'")
    execute(conn, "SET LOCAL statement_timeout = '120s'")
    execute(conn, "SET LOCAL application_name = 'maintenance_permissions_caller'")
    assertEquals(role, scalar(conn, "SELECT current_user::text"))
  }

  private fun sessionSettings(conn: Connection): String? = scalar(conn, """
    SELECT string_agg(name || '=' || setting, '|' ORDER BY name) FROM pg_settings
    WHERE name IN ('role', 'search_path', 'application_name', 'default_transaction_read_only',
      'transaction_read_only', 'lock_timeout', 'statement_timeout', 'TimeZone')
  """)

  private fun snapshot(conn: Connection): String? = scalar(conn, """
    SELECT coalesce(string_agg(ROW(c.oid, c.relowner, c.relname, c.relkind,
      pg_get_expr(c.relpartbound, c.oid))::text, '|' ORDER BY c.oid), '')
    FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
    WHERE n.nspname IN ('naksha~admin', 'Permissions Maintenance')
  """)

  private fun execute(conn: Connection, sql: String) {
    conn.createStatement().use { it.execute(sql) }
  }

  private fun scalar(conn: Connection, sql: String): String? {
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
