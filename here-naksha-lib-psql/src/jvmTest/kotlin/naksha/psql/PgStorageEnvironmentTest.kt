package naksha.psql

import java.io.File
import java.net.URLClassLoader
import java.util.concurrent.TimeUnit
import naksha.base.NakshaError
import naksha.base.NakshaException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PgStorageEnvironmentTest {
  @Test
  fun disablesMaintenanceWithoutDatabaseUrlOverride() = probe("false", true, false)

  @Test
  fun enablesMaintenanceWithoutDatabaseUrlOverride() = probe("true", false, true)

  @Test
  fun preservesDefaultWhenMaintenanceOverrideIsAbsent() = probe(null, true, true)

  @Test
  fun preservesExplicitOptOutWhenMaintenanceOverrideIsAbsent() = probe(null, false, false)

  @Test
  fun appliesMaintenanceOverrideWithSharedDatabaseUrlOnly() = probe("false", true, false, sharedUrl = true)

  @Test
  fun appliesMaintenanceAndDatabaseUrlOverridesTogether() = probe("false", true, false, databaseUrl = true)

  @Test
  fun rejectsInvalidMaintenanceOverrideWithoutDatabaseUrl() {
    for (value in listOf("", "FALSE", "0")) probe(value, true, null)
  }

  private fun probe(jobs: String?, initial: Boolean, expected: Boolean?, sharedUrl: Boolean = false, databaseUrl: Boolean = false) {
    // Fork instead of mutating the process environment or depending on the campaign's database overrides.
    val classpath = linkedSetOf(System.getProperty("java.class.path"))
    var loader: ClassLoader? = javaClass.classLoader
    while (loader != null) {
      if (loader is URLClassLoader) {
        for (url in loader.urLs) classpath.add(File(url.toURI()).path)
      }
      loader = loader.parent
    }
    val builder = ProcessBuilder(
      File(System.getProperty("java.home"), "bin/java").path,
      "-cp", classpath.joinToString(File.pathSeparator),
      PgStorageEnvironmentProbe::class.java.name, initial.toString(), expected?.toString() ?: "invalid"
    ).redirectErrorStream(true)
    val env = builder.environment()
    env.remove("NAKSHA_TEST_PSQL_DB_URL")
    env.remove("NAKSHA_TEST_DB_URL")
    env.remove("NAKSHA_TEST_MAINTENANCE_JOBS")
    if (jobs != null) env["NAKSHA_TEST_MAINTENANCE_JOBS"] = jobs
    if (sharedUrl) env["NAKSHA_TEST_DB_URL"] = PgStorageEnvironmentProbe.OVERRIDE_URL
    if (databaseUrl) env["NAKSHA_TEST_PSQL_DB_URL"] = PgStorageEnvironmentProbe.OVERRIDE_URL
    val process = builder.start()
    try {
      assertTrue(process.waitFor(30, TimeUnit.SECONDS), "Configuration probe timed out")
      val output = process.inputStream.bufferedReader().use { it.readText() }
      assertEquals(0, process.exitValue(), output)
    } finally {
      process.destroyForcibly()
    }
  }
}

/** Isolated environment probe; never connects to a database. @since 3.0 */
object PgStorageEnvironmentProbe {
  const val OVERRIDE_URL = "jdbc:postgresql://localhost/override_probe?user=probe&password=unused"

  @JvmStatic
  fun main(args: Array<String>) {
    val config = PgConfig("environment_probe").withMasterUri("jdbc:postgresql://localhost/configured_probe?user=probe&password=unused")
    config.maintenanceJobs = args[0].toBooleanStrict()
    if (args[1] == "invalid") {
      val failure = assertFailsWith<NakshaException> { PgStorage.getPgInstanceConfigFromEnv(config) }
      assertEquals(NakshaError.ILLEGAL_ARGUMENT, failure.error.code)
      return
    }
    val instance = PgStorage.getPgInstanceConfigFromEnv(config)
    assertEquals(args[1].toBooleanStrict(), config.maintenanceJobs)
    if (System.getenv("NAKSHA_TEST_PSQL_DB_URL") != null) {
      assertEquals("override_probe", instance?.db)
      assertEquals("override_probe", config.master.db)
      return
    }
    assertNull(instance)
    assertEquals("configured_probe", config.master.db, "A maintenance-only override must preserve the configured endpoint")
  }
}
