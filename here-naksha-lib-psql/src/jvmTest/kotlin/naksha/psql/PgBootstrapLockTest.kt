package naksha.psql

import naksha.base.Id
import naksha.base.NakshaError
import naksha.base.NakshaException
import naksha.base.illegalState
import naksha.model.NakshaVersion
import org.mockito.Mockito
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Proves bootstrap's probe/lock/recheck ordering without process-global storage-cache serialization. */
class PgBootstrapLockTest {
  private val previousVersion = NakshaVersion.of("3.0.0-beta.53")

  private class State(
    val schemaOid: Int? = 1234,
    val version: NakshaVersion = adminVersion,
    val id: String = "bootstrap_lock_probe",
    val number: Long = Id(id).number
  )

  private class Fixture(private vararg val states: State) {
    val events = ArrayList<String>()
    var probes = 0
    var installs = 0
    var failInstallation = false
    private var state = states[0]
    private val config = PgConfig("bootstrap_lock_probe")
    val connection = Mockito.mock(PgConnection::class.java) { call ->
      when (call.method.name) {
        "execute" -> {
          val sql = call.getArgument<String>(0)
          events.add(sql)
          val fields: Map<String, Any?> = when {
            sql.trimStart().startsWith("WITH basics") -> {
              state = states[minOf(probes++, states.size - 1)]
              mapOf("bs" to 8192, "version" to "PostgreSQL 18.6 on test", "gzip_oid" to 200,
                "admin_oid" to state.schemaOid, "has_naksha_version" to true,
                "has_naksha_storage_id" to true, "has_naksha_storage_number" to true)
            }
            sql.contains("naksha_version()") -> mapOf("v" to state.version.toLong(), "id" to state.id, "n" to state.number)
            sql.contains("pg_advisory_xact_lock") -> {
              assertEquals(listOf(PG_ADMIN_INSTALL_LOCK_CLASS, PG_ADMIN_INSTALL_LOCK_KEY),
                call.getArgument<Array<Any?>>(1).toList())
              emptyMap()
            }
            else -> mapOf("oid" to 5678)
          }
          Mockito.mock(PgCursor::class.java) { cursorCall ->
            when (cursorCall.method.name) {
              "fetch" -> cursorCall.mock
              "get", "column" -> fields[cursorCall.getArgument<String>(0)]
              else -> Mockito.RETURNS_DEFAULTS.answer(cursorCall)
            }
          }
        }
        "commit", "rollback", "close" -> { events.add(call.method.name); null }
        "toUri" -> "jdbc:postgresql://localhost/bootstrap_lock_probe"
        else -> Mockito.RETURNS_DEFAULTS.answer(call)
      }
    }
    private val storage = Mockito.mock(PgStorage::class.java) { call ->
      when (call.method.name) {
        "getConfig" -> config
        "newConnection" -> connection
        else -> Mockito.RETURNS_DEFAULTS.answer(call)
      }
    }
    val database = object : PgDatabaseImpl(storage, Id(config.id)) {
      override fun upsert_naksha_admin(conn: PgConnection, id: Id, psql_version: NakshaVersion,
        schema_oid: Int?, installed_version: NakshaVersion?): Int {
        assertTrue(events.any { it.contains("pg_advisory_xact_lock") })
        assertEquals(2, probes, "DDL must follow the locked recheck")
        assertEquals(adminVersion, psql_version)
        installs++
        events.add("install")
        if (failInstallation) throw illegalState("simulated installation failure")
        return schema_oid ?: 1234
      }
    }

    fun assertLockFree() {
      assertEquals(1, probes)
      assertEquals(0, installs)
      assertFalse(events.any { it.contains("pg_advisory") })
      assertFalse(events.any { it.contains("ISOLATION LEVEL") })
    }

    fun assertRechecked() {
      assertEquals(2, probes)
      val firstCommit = events.indexOf("commit")
      val lock = events.indexOfFirst { it.contains("pg_advisory_xact_lock") }
      val isolation = events.indexOf("SET TRANSACTION ISOLATION LEVEL READ COMMITTED")
      val recheck = events.indexOfLast { it.trimStart().startsWith("WITH basics") }
      assertTrue(firstCommit >= 0 && firstCommit < isolation && isolation < lock && lock < recheck,
        "The read-only probe must end before acquiring the lock; the recheck needs a fresh READ COMMITTED snapshot")
      assertEquals(1, events.count { it.contains("pg_advisory_xact_lock") })
      assertEquals("close", events.last())
    }
  }

  @Test
  fun currentVersionNeverTakesTheInstallationLockEvenWhenUpgradeIsEnabled() {
    val fixture = Fixture(State())
    fixture.database.init(doCreate = true, doUpgrade = true)
    fixture.assertLockFree()
    assertEquals(1, fixture.events.count { it == "commit" })
  }

  @Test
  fun compatibleOlderVersionWithoutUpgradeStaysLockFree() {
    val fixture = Fixture(State(version = previousVersion))
    fixture.database.init(doUpgrade = false)
    fixture.assertLockFree()
  }

  @Test
  fun concurrentInstallerIsObservedAfterTakingTheLock() {
    val fixture = Fixture(State(schemaOid = null), State())
    fixture.database.init(doCreate = true)
    fixture.assertRechecked()
    assertEquals(0, fixture.installs)
    assertEquals(1234, fixture.database.schemaOid)
    assertEquals(5678, fixture.database.versionSequenceOid)
  }

  @Test
  fun concurrentUpgradeIsNotInstalledAgain() {
    val fixture = Fixture(State(version = previousVersion), State())
    fixture.database.init(doUpgrade = true)
    fixture.assertRechecked()
    assertEquals(0, fixture.installs)
  }

  @Test
  fun missingDatabaseIsInstalledOnlyAfterTheLockedRecheck() {
    val fixture = Fixture(State(schemaOid = null))
    fixture.database.init(doCreate = true)
    fixture.assertRechecked()
    assertEquals(1, fixture.installs)
    assertTrue(fixture.events.indexOf("install") < fixture.events.lastIndexOf("commit"))
  }

  @Test
  fun oldDatabaseIsUpgradedOnlyAfterTheLockedRecheck() {
    val fixture = Fixture(State(version = previousVersion))
    fixture.database.init(doUpgrade = true)
    fixture.assertRechecked()
    assertEquals(1, fixture.installs)
  }

  @Test
  fun identityIsRevalidatedAfterWaitingForAnotherInstaller() {
    for (numberMismatch in listOf(false, true)) {
      val changed = if (numberMismatch) State(number = 42L) else State(id = "other_storage")
      val fixture = Fixture(State(version = previousVersion), changed)
      val failure = assertFailsWith<NakshaException> { fixture.database.init(doUpgrade = true) }
      assertEquals(NakshaError.STORAGE_ID_MISMATCH, failure.error.code)
      fixture.assertRechecked()
      assertEquals(0, fixture.installs)
      assertEquals(1, fixture.events.count { it == "commit" })
    }
  }

  @Test
  fun newerVersionAfterWaitingIsNotDowngraded() {
    val fixture = Fixture(State(version = previousVersion), State(version = NakshaVersion.of("3.0.0-beta.55")))
    assertFailsWith<NakshaException> { fixture.database.init(doUpgrade = true) }
    fixture.assertRechecked()
    assertEquals(0, fixture.installs)
  }

  @Test
  fun deniedCreationAndUpgradeFailBeforeAcquiringTheLock() {
    for (state in listOf(State(schemaOid = null), State(version = NakshaVersion.of("3.0.0-beta.52")))) {
      val fixture = Fixture(state)
      assertFailsWith<NakshaException> { fixture.database.init(doCreate = false, doUpgrade = false) }
      fixture.assertLockFree()
      assertEquals(0, fixture.events.count { it == "commit" })
    }
  }

  @Test
  fun plv8AttachmentNeverLocksOrCompletesTheCallersTransaction() {
    val fixture = Fixture(State())
    fixture.database.init(doCreate = false, doUpgrade = false, attachExisting = true)
    fixture.assertLockFree()
    assertEquals(0, fixture.events.count { it == "commit" || it == "rollback" })
    assertFalse(fixture.events.any { it.startsWith("SET ") })
  }

  @Test
  fun failedInstallationClosesTheOwningConnectionWithoutCommittingDdl() {
    val fixture = Fixture(State(schemaOid = null))
    fixture.failInstallation = true
    assertFailsWith<NakshaException> { fixture.database.init(doCreate = true) }
    fixture.assertRechecked()
    assertEquals(1, fixture.events.count { it == "commit" }, "Only the read-only probe may commit")
    assertEquals(1, fixture.installs)
  }
}
