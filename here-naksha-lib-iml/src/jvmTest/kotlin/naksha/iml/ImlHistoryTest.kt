package naksha.iml

import naksha.base.Action
import naksha.base.NakshaError
import naksha.base.NakshaException
import naksha.base.Version
import naksha.model.NakshaContext
import naksha.model.request.ReadFeatures
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ImlHistoryTest {
  @BeforeEach
  fun context() {
    NakshaContext.currentContext().appId = "iml-history-test"
  }

  @Test
  fun `default and explicit HEAD select only current nondeleted states`() {
    for (maximum in listOf(null, Version.HEAD.number)) {
      val history = ImlHistory(ReadFeatures().apply { version = maximum })
      assertEquals("next_version = ? AND operation != 'D'", history.predicate)
      assertEquals(listOf(Long.MAX_VALUE), history.parameters)
      assertEquals(1, history.versions)
      assertFalse(history.historical)
      assertFalse(history.snapshot)
      assertEquals(
        "SELECT id, i, version, operation FROM \"schema\".\"layer\" WHERE next_version = ? AND operation != 'D' ORDER BY i ASC",
        history.select("\"schema\".\"layer\"", history.predicate, "i ASC")
      )
    }
  }

  @Test
  fun `nonhistorical bounds restrict HEAD without searching older states`() {
    val history = ImlHistory(ReadFeatures().apply {
      minVersion = Version.manual(10, Action.CREATE).number
      version = Version.manual(20, Action.DELETE).number
      queryDeleted = true
    })
    assertEquals("next_version = ? AND version <= ? AND version >= ?", history.predicate)
    assertEquals(listOf(Long.MAX_VALUE, 20L, 10L), history.parameters)
    assertFalse(history.snapshot)
    assertFalse(history.select("\"layer\"", history.predicate, "i ASC").contains("OVER"))
  }

  @Test
  fun `finite snapshot uses half open validity interval and optional lower bound`() {
    for (action in listOf(Action.CREATE, Action.UPDATE, Action.DELETE, Action.VERSION)) {
      val request = ReadFeatures().apply {
        queryHistory = true
        version = Version.manual(20, action).number
      }
      val history = ImlHistory(request)
      assertTrue(history.historical)
      assertTrue(history.snapshot)
      assertEquals("version <= ? AND next_version > ? AND operation != 'D'", history.predicate)
      assertEquals(listOf(20L, 20L), history.parameters)
      request.minVersion = Version.manual(10, Action.VERSION).number
      request.queryDeleted = true
      val bounded = ImlHistory(request)
      assertEquals("version <= ? AND next_version > ? AND version >= ?", bounded.predicate)
      assertEquals(listOf(20L, 20L, 10L), bounded.parameters)
    }
  }

  @Test
  fun `historical HEAD snapshot uses source HEAD sentinel`() {
    for (maximum in listOf(null, Version.HEAD.number)) {
      val history = ImlHistory(ReadFeatures().apply {
        queryHistory = true
        queryDeleted = true
        version = maximum
        minVersion = Version.manual(10, Action.CREATE).number
      })
      assertTrue(history.snapshot)
      assertEquals("next_version = ? AND version >= ?", history.predicate)
      assertEquals(listOf(Long.MAX_VALUE, 10L), history.parameters)
    }
  }

  @Test
  fun `history ranks filtered candidates before group and row deletion suppression`() {
    val history = ImlHistory(ReadFeatures().apply {
      queryHistory = true
      versions = 3
      minVersion = Version.manual(10, Action.CREATE).number
      version = Version.manual(20, Action.VERSION).number
    })
    assertEquals(3, history.versions)
    assertTrue(history.historical)
    assertFalse(history.snapshot)
    assertEquals("version <= ? AND version >= ?", history.predicate)
    assertEquals(listOf(20L, 10L), history.parameters)
    val combined = "${history.predicate} AND (author = ? OR id = ?)"
    val sql = history.select("\"schema\".\"layer\"", combined, "id ASC, version DESC, i DESC")
    val ordering = "version DESC, CASE operation WHEN 'I' THEN 0 WHEN 'U' THEN 1 WHEN 'D' THEN 2 ELSE 3 END DESC, i DESC"
    assertEquals(
      "WITH candidates AS (SELECT *, row_number() OVER (PARTITION BY id ORDER BY $ordering) AS history_rank, " +
        "first_value(operation) OVER (PARTITION BY id ORDER BY $ordering) AS latest_operation " +
        "FROM \"schema\".\"layer\" WHERE $combined) SELECT id, i, version, operation FROM candidates " +
        "WHERE history_rank <= 3 AND latest_operation != 'D' AND operation != 'D' ORDER BY id ASC, version DESC, i DESC",
      sql
    )
    assertFalse(sql.contains("next_version ="))
    assertFalse(sql.contains("LIMIT"))
    assertFalse(sql.contains("OFFSET"))
  }

  @Test
  fun `unbounded history includes all candidates and queryDeleted retains tombstones`() {
    for (maximum in listOf(null, Version.HEAD.number)) {
      val history = ImlHistory(ReadFeatures().apply {
        queryHistory = true
        queryDeleted = true
        versions = Int.MAX_VALUE
        version = maximum
      })
      assertEquals("TRUE", history.predicate)
      assertEquals(emptyList<Any?>(), history.parameters)
      val sql = history.select("\"layer\"", "TRUE AND id = ?", "i ASC")
      assertTrue(sql.contains("FROM \"layer\" WHERE TRUE AND id = ?)"))
      assertTrue(sql.endsWith("WHERE history_rank <= 2147483647 ORDER BY i ASC"))
      assertFalse(sql.contains("!= 'D'"))
    }
  }

  @Test
  fun `zero and equal bounds are allowed and HEAD lower bound is never shifted`() {
    val zero = ImlHistory(ReadFeatures().apply {
      queryHistory = true
      version = 0L
    })
    assertEquals(listOf(0L, 0L), zero.parameters)
    val equal = ImlHistory(ReadFeatures().apply {
      queryHistory = true
      version = Version.MIN_MANUAL.number
      minVersion = version
    })
    assertEquals(listOf(1L, 1L, 1L), equal.parameters)
    val head = ImlHistory(ReadFeatures().apply {
      queryHistory = true
      versions = 2
      minVersion = Version.HEAD.number
    })
    assertEquals("version >= ?", head.predicate)
    assertEquals(listOf(Long.MAX_VALUE), head.parameters)
  }

  @Test
  fun `invalid version bounds and multiple versions without history are rejected`() {
    val requests = arrayListOf(
      ReadFeatures().apply { versions = 2 },
      ReadFeatures().apply {
        minVersion = Version.manual(20, Action.CREATE).number
        version = Version.manual(10, Action.VERSION).number
      },
      ReadFeatures().apply { minVersion = Version.MIN_DATED.number }
    )
    for (bound in listOf(-1L, 1L, 2L, 3L, Version.MIN_DATED.number, Version.MAX_MANUAL.number + 1, Long.MAX_VALUE)) {
      requests.add(ReadFeatures().apply { version = bound })
    }
    for (request in requests) {
      val error = assertThrows(NakshaException::class.java) { ImlHistory(request) }
      assertEquals(NakshaError.ILLEGAL_ARGUMENT, error.error.code)
    }
  }

  @Test
  fun `compiled properties are independent of later request mutation`() {
    val request = ReadFeatures().apply {
      queryHistory = true
      versions = 2
      version = Version.manual(20, Action.VERSION).number
    }
    val history = ImlHistory(request)
    val sql = history.select("\"layer\"", history.predicate, "i ASC")
    request.queryHistory = false
    request.queryDeleted = true
    request.versions = 1
    request.version = null
    assertEquals(2, history.versions)
    assertTrue(history.historical)
    assertFalse(history.snapshot)
    assertEquals(listOf(20L), history.parameters)
    assertEquals(sql, history.select("\"layer\"", history.predicate, "i ASC"))
    assertThrows(UnsupportedOperationException::class.java) { (history.parameters as MutableList<Any?>).add(0L) }
  }
}
