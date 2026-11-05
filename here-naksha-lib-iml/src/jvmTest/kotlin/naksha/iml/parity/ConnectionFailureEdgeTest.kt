package naksha.iml.parity

import naksha.iml.ImlConfig
import naksha.iml.ImlStorage
import naksha.model.Naksha
import naksha.model.SessionOptions
import naksha.model.request.SuccessResponse
import naksha.iml.parity.EdgeSupport.case
import naksha.iml.parity.EdgeSupport.describe
import naksha.iml.parity.Parity.observe
import naksha.iml.parity.Parity.request
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestMethodOrder

/**
 * Phase 3, item "a DB restart or dropped connections in the middle of a read".
 * Kills lib-iml's backends with pg_terminate_backend, restarts and pauses the eval stack's own postgres container (imleval-postgres, compose
 * project started in Phase 1). Reads `ev-big` (120 000 points) and `ev-1m`. Destructive tests run last and wait until the DB and Hub are back.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@org.junit.jupiter.api.Timeout(value = 10, unit = java.util.concurrent.TimeUnit.MINUTES)
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class ConnectionFailureEdgeTest {
  private lateinit var config: ImlConfig
  private lateinit var storage: ImlStorage
  private val app = "p3-kill"

  @BeforeAll
  fun setUp() {
    Parity.assumeEnabled()
    val (c, s) = Parity.open("iml-eval-p3-conn")
    config = c
    storage = s
  }

  @AfterAll
  fun tearDown() {
    runCatching { EdgeSupport.docker("unpause", "imleval-postgres") }
    if (::config.isInitialized) runCatching { Naksha.removeStorage(config) }
  }

  private fun options(socketTimeout: Int? = null) = SessionOptions(appId = "iml-eval-parity", appName = app).let {
    if (socketTimeout != null) it.copyWithTimeouts(socketTimeout = socketTimeout) else it
  }

  // Lazy materialization (response.features → TupleCache) opens its own session with default options, so its backend does not carry
  // the caller's application name; match lib-iml's exact-load statement text as well (ImlReadSession.loadExact).
  private fun killLib(): Int = (EdgeSupport.scalar(
    "SELECT count(pg_terminate_backend(pid)) FROM pg_stat_activity WHERE (application_name = ? OR query LIKE 'SELECT id, i, version, next_version, operation, author, jsondata%') " +
      "AND pid <> pg_backend_pid()", app) as Number).toInt()

  /** Runs a full read of `space` in a thread and kills lib's backends after `delayMs`. */
  private fun readAndKill(space: String, delayMs: Long, phase: String): Map<String, Any?> {
    Naksha.cache.clear(storage)
    var outcome = "?"
    var elapsed = 0L
    var selectMs = -1L
    val t = Thread {
      naksha.model.NakshaContext.currentContext().withAppId("iml-eval-parity")
      val start = System.currentTimeMillis()
      outcome = try {
        storage.newReadSession(options()).use { s ->
          val r = s.executeRead(request(space)) as SuccessResponse
          selectMs = System.currentTimeMillis() - start
          val f = r.features
          "ok: ${f.filterNotNull().size} features, ${f.count { it == null }} null entries of ${r.featureTupleList.size}"
        }
      } catch (e: Throwable) { describe(e) }
      elapsed = System.currentTimeMillis() - start
    }
    t.start()
    Thread.sleep(delayMs)
    val killed = killLib()
    t.join(300_000)
    val after = try { storage.newReadSession(options()).use { s -> "ok ${(s.executeRead(request("ev-hist") { limit = 5 }) as SuccessResponse).features.filterNotNull().size}" } } catch (e: Throwable) { describe(e) }
    return linkedMapOf("space" to space, "phase" to phase, "killAfterMs" to delayMs, "backendsKilled" to killed, "readOutcome" to outcome,
      "selectionDoneAfterMs" to selectMs, "readMs" to elapsed, "nextReadSameStorage" to after)
  }

  @Test
  @Order(1)
  fun `backend terminated during selection and during materialization`() {
    val sel = readAndKill("ev-1m", 150, "selection (1M rows streamed with fetchSize 1000)")
    val mat = readAndKill("ev-big", 2_000, "materialization (120k, batches of 500 on one connection)")
    observe("conn-kill", mapOf("selection" to sel, "materialization" to mat))
    case("3 connection loss", "pg_terminate_backend during the selection of ev-1m (after ${sel["killAfterMs"]} ms)",
      "Hub: 502/504 for that request; next request works",
      "killed ${sel["backendsKilled"]}; read: ${sel["readOutcome"]} after ${sel["readMs"]} ms; next read: ${sel["nextReadSameStorage"]}",
      "Medium: the read fails (no partial result), but with a raw PSQLException (F13); the storage stays usable")
    case("3 connection loss", "pg_terminate_backend during materialization of ev-big (after ${mat["killAfterMs"]} ms)",
      "Hub: 502/504; next request works",
      "killed ${mat["backendsKilled"]}; selection done after ${mat["selectionDoneAfterMs"]} ms; read: ${mat["readOutcome"]} after ${mat["readMs"]} ms; next read: ${mat["nextReadSameStorage"]}",
      "Medium: as above; no retry, the whole read is lost")
  }

  @Test
  @Order(2)
  fun `network stall with socketTimeout, and a second reader on the same storage`() {
    val o = LinkedHashMap<String, Any?>()
    for ((variant, timeout) in listOf("lazy response.features" to 5_000, "explicit session.loadTuples" to 5_000)) {
      Naksha.cache.clear(storage)
      var first = "?"
      var firstMs = 0L
      var second = "?"
      var secondMs = 0L
      var secondStartedAt = 0L
      val t1 = Thread {
        naksha.model.NakshaContext.currentContext().withAppId("iml-eval-parity")
        val start = System.currentTimeMillis()
        first = try {
          storage.newReadSession(options(timeout)).use { s ->
            val r = s.executeRead(request("ev-big")) as SuccessResponse
            if (variant.startsWith("explicit")) s.loadTuples(r.featureTupleList, 0, r.featureTupleList.size)
            "ok ${r.features.filterNotNull().size}"
          }
        } catch (e: Throwable) { describe(e) }
        firstMs = System.currentTimeMillis() - start
      }
      t1.start()
      Thread.sleep(1_500) // materializing
      val pause = EdgeSupport.docker("pause", "imleval-postgres")
      val pausedAt = System.currentTimeMillis()
      val t2 = Thread {
        naksha.model.NakshaContext.currentContext().withAppId("iml-eval-parity")
        Thread.sleep(500)
        secondStartedAt = System.currentTimeMillis() - pausedAt
        val start = System.currentTimeMillis()
        // Already-open session object on the same storage; the read is tiny.
        second = try { storage.newReadSession(options(timeout)).use { s -> "ok ${(s.executeRead(request("ev-hist") { limit = 1 }) as SuccessResponse).features.size}" } } catch (e: Throwable) { describe(e) }
        secondMs = System.currentTimeMillis() - start
      }
      t2.start()
      Thread.sleep(12_000)
      val unpause = EdgeSupport.docker("unpause", "imleval-postgres")
      val unpausedAfter = System.currentTimeMillis() - pausedAt
      t1.join(180_000)
      t2.join(180_000)
      o["$variant, socketTimeout=$timeout"] = linkedMapOf("pause" to pause.first, "unpauseAfterMs" to unpausedAfter, "unpause" to unpause.first,
        "firstRead" to "$first in $firstMs ms", "secondRead" to "$second in $secondMs ms (started ${secondStartedAt} ms after the pause)")
    }
    // Default socketTimeout (60 s) is not stalled here on purpose: it would only show the same mechanism with a longer wait.
    o["defaultSocketTimeoutMs"] = SessionOptions(appId = "x").socketTimeout
    o["defaultConnectTimeoutMs"] = SessionOptions(appId = "x").connectTimeout
    observe("conn-stall", o)
    val r = o["lazy response.features, socketTimeout=5000"] as Map<*, *>
    val e = o["explicit session.loadTuples, socketTimeout=5000"] as Map<*, *>
    case("3 connection loss", "network stall (docker pause 12 s) during materialization, session socketTimeout=5 s",
      "Hub: connector timeout 26 s → 504; other requests are served by other pool connections",
      "lazy response.features: ${r["firstRead"]} (the session's socketTimeout is not applied); explicit session.loadTuples: ${e["firstRead"]}; " +
        "a second tiny read on the same storage: ${r["secondRead"]} / ${e["secondRead"]}",
      "High: reads on one storage are serialized (ImlStorage.withConnection, one permit), so one stalled read blocks every other reader of that storage until its socketTimeout (default ${o["defaultSocketTimeoutMs"]} ms) expires")
  }

  @Test
  @Order(3)
  fun `database restart between selection and materialization, and storage reuse afterwards`() {
    val o = LinkedHashMap<String, Any?>()
    Naksha.cache.clear(storage)
    val session = storage.newReadSession(options())
    val lazy = session.executeRead(request("ev-hist")) as SuccessResponse
    val restart = EdgeSupport.docker("restart", "imleval-postgres")
    o["dockerRestart"] = restart.first
    // Immediately after restart returns the server may still be starting.
    o["materializeImmediately"] = try { "ok ${lazy.features.filterNotNull().size} of ${lazy.featureTupleList.size}" } catch (e: Throwable) { describe(e) }
    o["dbUpAfterMs"] = EdgeSupport.waitFor(120_000, "postgres") { EdgeSupport.dbUp() }
    o["sameSessionNewRead"] = try { "ok ${(session.executeRead(request("ev-hist")) as SuccessResponse).features.filterNotNull().size}" } catch (e: Throwable) { describe(e) }
    o["sameStorageNewSession"] = try { storage.newReadSession(options()).use { s -> "ok ${(s.executeRead(request("ev-big") { limit = 10 }) as SuccessResponse).features.filterNotNull().size}" } } catch (e: Throwable) { describe(e) }
    session.close()
    o["hubUpAfterMs"] = EdgeSupport.waitFor(180_000, "hub") { EdgeSupport.hubUp() }
    o["hubFirstRead"] = Parity.hub("/spaces/ev-hist/iterate", listOf("limit" to "5")).toString()
    var hubOkAfter = -1L
    val start = System.currentTimeMillis()
    while (System.currentTimeMillis() - start < 120_000) {
      if (Parity.hub("/spaces/ev-hist/iterate", listOf("limit" to "5")).status == 200) { hubOkAfter = System.currentTimeMillis() - start; break }
      Thread.sleep(500)
    }
    o["hubReadOkAfterMs"] = hubOkAfter
    observe("conn-restart", o)
    case("3 connection loss", "DB restart between selection and lazy materialization",
      "Hub: n/a (single-phase); requests during the restart fail with 502/504",
      "materialization right after restart: ${o["materializeImmediately"]}",
      "Medium: raw PSQLException; the selection result is lost")
    case("3 connection loss", "DB restart, then reuse of the same storage and session",
      "Hub: pooled connections are re-established (first Hub read after restart: ${o["hubFirstRead"]}, OK after ${o["hubReadOkAfterMs"]} ms)",
      "lib: same session ${o["sameSessionNewRead"]}; same storage new session ${o["sameStorageNewSession"]} (DB up after ${o["dbUpAfterMs"]} ms)",
      "OK (positive): no pool, so nothing stale survives a restart; every read opens a fresh connection")
  }
}
