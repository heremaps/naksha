import java.sql.Connection
import java.sql.SQLException
import java.sql.Statement
import java.util.Properties
import org.gradle.api.GradleException
import org.gradle.api.logging.Logging
import org.gradle.api.provider.Property
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.postgresql.Driver

/** Provisions one database for an aggregate test run and retains it for inspection. @since 3.0 */
abstract class TestRunDatabase : BuildService<TestRunDatabase.Parameters> {
  interface Parameters : BuildServiceParameters {
    val controlUrl: Property<String>
  }

  private var runDatabaseName: String? = null

  /** Create the same run database for all JVM test tasks; never reuse an existing name. @since 3.0 */
  @Synchronized
  fun testUrl(): String {
    val databaseName = runDatabaseName ?: createDatabase()
    val base = parameters.controlUrl.get()
    val queryAt = base.indexOf('?')
    val dbAt = base.lastIndexOf('/', queryAt)
    return base.substring(0, dbAt + 1) + databaseName + base.substring(queryAt)
  }

  private fun createDatabase(): String = withControlConnection(
    "allocate the disposable test database; check the test connection, CREATEDB and access to public.naksha_test_counter",
  ) { connection, statement ->
    repeat(32) {
      val number = nextRunNumber(connection, statement)
      val name = "naksha_test_run_" + number.toString().padStart(3, '0')
      try {
        statement.execute("CREATE DATABASE \"$name\" TEMPLATE template0")
        runDatabaseName = name
        Logging.getLogger(TestRunDatabase::class.java).lifecycle("Using disposable PostgreSQL test database $name (retained for inspection)")
        return@withControlConnection name
      } catch (e: SQLException) {
        // Another control database on this server can have its own counter, or a stale run may still own this name.
        if (e.sqlState != "42P04") throw e
      }
    }
    throw GradleException("Could not find a free PostgreSQL test database name after 32 attempts; check public.naksha_test_counter and existing test databases.")
  }

  private fun nextRunNumber(connection: Connection, statement: Statement): Long {
    connection.autoCommit = false
    // Serialize the initial sequence creation within this control database, including concurrent first builds.
    statement.execute("SELECT pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtextextended('naksha:gradle:test-run-counter', 0))")
    val missing = statement.executeQuery("SELECT pg_catalog.to_regclass('public.naksha_test_counter') IS NULL").use { rows ->
      rows.next()
      rows.getBoolean(1)
    }
    if (missing) statement.execute("CREATE SEQUENCE public.naksha_test_counter AS bigint START WITH 1 MINVALUE 1 NO CYCLE")
    val number = statement.executeQuery("SELECT pg_catalog.nextval('public.naksha_test_counter')").use { rows ->
      rows.next()
      rows.getLong(1)
    }
    if (number < 1) throw GradleException("public.naksha_test_counter must yield positive run numbers.")
    connection.commit()
    connection.autoCommit = true // CREATE DATABASE cannot run inside a transaction.
    return number
  }

  private fun <T> withControlConnection(action: String, execute: (Connection, Statement) -> T): T {
    val properties = Properties().apply {
      setProperty("connectTimeout", "10")
      setProperty("socketTimeout", "45")
    }
    return try {
      val connection = Driver().connect(parameters.controlUrl.get(), properties)
        ?: throw GradleException("cleanAndTest requires a PostgreSQL JDBC URL")
      connection.use { conn ->
        conn.createStatement().use { statement ->
          statement.queryTimeout = 30
          execute(conn, statement)
        }
      }
    } catch (e: SQLException) {
      // The JDBC error text can include connection details. Report only the bounded SQL status.
      throw GradleException("Could not $action (PostgreSQL SQLSTATE ${e.sqlState ?: "unavailable"}).")
    }
  }
}
