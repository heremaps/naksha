@file:OptIn(naksha.model.v30_experimental::class)

package naksha.iml.parity

import naksha.base.Id
import naksha.base.NakshaException
import naksha.base.Platform
import naksha.iml.ImlConfig
import naksha.iml.parity.Parity.hubIterateAll
import naksha.iml.parity.Parity.hubOk
import naksha.model.Naksha
import naksha.model.NakshaContext
import naksha.model.SessionOptions
import naksha.model.objects.NakshaFeature
import naksha.model.objects.StoreMode
import naksha.model.request.ErrorResponse
import naksha.model.request.SuccessResponse
import naksha.model.request.Write
import naksha.model.request.WriteRequest
import naksha.model.request.query.DoubleOp
import naksha.model.request.query.PQuery
import naksha.model.request.query.Property
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

/**
 * Phase 5: acceptance tests for the Critical/High findings. Each test asserts the behaviour IML has (or an explicit failure where IML has
 * no equivalent), so each one **FAILS until its finding is fixed**. They run only with `IML_PARITY_STRICT=true`
 * (`scripts/p5-review.sh <log> true naksha.iml.parity.P5ExpectedBehaviourTest`) and are meant to be handed to the module's author as
 * regression tests for the fixes.
 *
 * Fixture: one Hub space `ev-p5-v1-<rnd>` (versionsToKeep=1, 10 points p0…p9 with `n` = 0…9), deleted at the end, plus scratch schemas
 * ([P5.Scratch]). Read-only on the Phase 1–3 seed layers `ev-ext`, `ev-big`, `ev-v1`, `ev-p3-geo`.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@org.junit.jupiter.api.Timeout(value = 10, unit = java.util.concurrent.TimeUnit.MINUTES)
class P5ExpectedBehaviourTest {
  private val rnd = P5.rnd()
  private val v1 = "ev-p5-v1-$rnd"
  private lateinit var scratch: P5.Scratch
  private val configs = ArrayList<ImlConfig>()

  @BeforeAll
  fun setUp() {
    Parity.assumeEnabled()
    assumeTrue(P5.strict, "Set IML_PARITY_STRICT=true to run tests that assert the fixed behaviour")
    NakshaContext.currentContext().withAppId("iml-eval-parity")
    P3.recreateSpace(v1, 1)
    P3.putFeatures(v1, (0 until 10).map { P3.point("p$it", 9.0 + it * 0.001, 51.0, mapOf("n" to it)) })
    scratch = P5.Scratch("x")
    scratch.addLayer("p5-lock", "t_lock", "ev-v1")
    scratch.addLayer("p5-free", "t_free", "ev-v1")
    scratch.addLayer("p5-write", "t_write", "ev-v1")
    scratch.addLayer("p5-hist", "t_hist", "ev-hist")
  }

  @AfterAll
  fun tearDown() {
    for (c in configs) runCatching { Naksha.removeStorage(c) }
    if (!P5.strict || Parity.hubUrl.isNullOrBlank() || Parity.dbUrl.isNullOrBlank()) return
    runCatching { P3.deleteSpace(v1) }
    if (::scratch.isInitialized) scratch.close()
  }

  private fun open(id: String, configure: ImlConfig.() -> Unit = {}) = Parity.open("iml-eval-p5x-$id-$rnd", configure).also { configs.add(it.first) }

  private fun coordinates(f: Map<*, *>?): Any? = (f?.get("geometry") as Map<*, *>?)

  @Test
  fun `F1 - a LineString with whole-number coordinates reads like the Hub`() {
    val hub = hubOk("/spaces/ev-p3-geo/features/g-line-whole").json as Map<*, *>
    val (_, storage) = open("f1")
    val lib = Parity.libRead(storage, "ev-p3-geo") { queryMembers = naksha.model.request.ops.Equals("id", "g-line-whole") }.single()
    assertNull(P5.coordinateDiff(coordinates(hub), coordinates(lib), 1e-7), "lib-iml geometry differs from the Hub")
  }

  @Test
  fun `F2 - a composite layer is composed like the Hub or rejected explicitly`() {
    val (hub, _) = hubIterateAll("ev-ext", 1000)
    val (_, storage) = open("f2")
    val lib = try { Parity.libRead(storage, "ev-ext") } catch (e: NakshaException) { return } // explicit rejection is acceptable
    assertEquals(Parity.ids(hub), Parity.ids(lib), "ev-ext: lib-iml returns the delta table (incl. hide rows), not the composite")
  }

  @Test
  fun `F16 - a versionsToKeep=1 read under concurrent Hub updates loses no feature`() {
    val (_, storage) = open("f16")
    Naksha.cache.clear(storage)
    storage.newReadSession().use { s ->
      val r = s.executeRead(Parity.request(v1)) as SuccessResponse
      assertEquals(10, r.featureTupleList.size, "fixture")
      // The Hub updates every feature between selection and materialization (IML: one request = one consistent result).
      P3.putFeatures(v1, (0 until 10).map { P3.point("p$it", 9.0 + it * 0.001, 51.0, mapOf("n" to it, "rev" to rnd)) })
      s.loadTuples(r.featureTupleList, 0, r.featureTupleList.size)
      assertEquals(10, r.features.filterNotNull().size, "features lost between selection and materialization")
    }
  }

  @Test
  fun `H4 - a property search is complete or fails, also when the matches lie beyond hardCap candidates`() {
    val (_, storage) = open("h4") { hardCap = 5 }
    val result = try {
      Parity.libRead(storage, v1) { query.properties = PQuery(Property("properties", "n"), DoubleOp.EQ, 9) }
    } catch (e: NakshaException) { return } // an explicit "incomplete/too large" error is acceptable
    assertEquals(listOf("p9"), Parity.ids(result), "silently incomplete property search")
  }

  @Test
  fun `F12 - the first page of a continuation over a 120 000-feature layer is served`() {
    val (_, storage) = open("f12")
    storage.newReadSession().use { s ->
      val r = s.executeRead(Parity.request("ev-big") { limit = 100; returnHandle = true }) as SuccessResponse
      assertEquals(100, r.features.size)
      assertTrue(r.getRaw("handle") is String, "no continuation handle")
    }
  }

  @Test
  fun `F19 - one 32-bit layer-number collision does not take down the storage`() {
    val a = "layer-8647"
    val b = "layer-22052"
    require(Id.textToInt(a) == Id.textToInt(b)) { "fixture: the ids must collide" }
    scratch.addLayer(a, "t_free", null)
    scratch.addLayer(b, "t_free", null)
    try {
      val (_, storage) = scratch.open("iml-eval-p5x-f19-$rnd")
      val n = storage.newReadSession().use { (it.executeRead(scratch.request("p5-free") { limit = 3 }) as SuccessResponse).features.size }
      assertEquals(3, n)
    } finally {
      P3.execute("DELETE FROM \"${scratch.meta}\".space_meta WHERE id IN (?, ?)", a, b)
    }
  }

  @Test
  fun `F18 - a stalled read does not block a read of another layer on the same storage`() {
    val (_, storage) = scratch.open("iml-eval-p5x-f18-$rnd")
    val lock = P5.lock(scratch.sqlTable("t_lock"))
    try {
      val stalled = P5.Probe("f18-stalled") {
        storage.newReadSession(SessionOptions(appId = "iml-eval-parity", lockTimeout = 30_000)).use { it.executeRead(scratch.request("p5-lock") { limit = 1 }); "ok" }
      }.start()
      assertTrue(P5.waitFor(10_000) { P5.lockWaiters("t_lock").isNotEmpty() } >= 0, "fixture")
      val other = P5.Probe("f18-other") {
        storage.newReadSession().use { (it.executeRead(scratch.request("p5-free") { limit = 1 }) as SuccessResponse).features.size.toString() }
      }.start().join(3_000)
      assertTrue(!other.running && other.outcome == "1", "a read of an unlocked layer waited for the stalled read (still running after 3 s)")
      stalled.running
    } finally {
      P5.release(lock)
    }
  }

  @Test
  fun `F28 - a stalled read does not block Naksha useStorage of an unrelated storage`() {
    val (configA, storageA) = scratch.open("iml-eval-p5x-f28-$rnd")
    val other = ImlConfig("iml-eval-p5x-f28-other-$rnd", Parity.dbUrl!!).also { configs.add(it) }
    val lock = P5.lock(scratch.sqlTable("t_lock"))
    try {
      P5.Probe("f28-stalled") {
        storageA.newReadSession(SessionOptions(appId = "iml-eval-parity", lockTimeout = 30_000)).use { it.executeRead(scratch.request("p5-lock") { limit = 1 }); "ok" }
      }.start()
      assertTrue(P5.waitFor(10_000) { P5.lockWaiters("t_lock").isNotEmpty() } >= 0, "fixture")
      P5.Probe("f28-session") { storageA.newReadSession().use { "ok" } }.start()
      Thread.sleep(300)
      P5.Probe("f28-remove") { "${Naksha.removeStorage(configA) != null}" }.start()
      Thread.sleep(300)
      val unrelated = P5.Probe("f28-unrelated") { Naksha.useStorage(other).id }.start().join(3_000)
      assertTrue(!unrelated.running, "Naksha.useStorage of an unrelated storage was blocked for > 3 s")
    } finally {
      P5.release(lock)
    }
  }

  @Test
  fun `F23 - a feature write to a Hub-managed layer is refused`() {
    val (_, storage) = scratch.open("iml-eval-p5x-f23-$rnd") { writeEnabled = true }
    val outcome = try {
      storage.newWriteSession().use { w ->
        val collection = checkNotNull(w.getCollectionById(checkNotNull(w.getCatalogById(scratch.data)), "p5-write"))
        w.executeWrite(WriteRequest().add(Write().upsertFeature(collection, NakshaFeature("p5-new").apply { properties.setRaw("by", "lib-iml") })))
        w.commit()
        "committed"
      }
    } catch (e: NakshaException) { return }
    fail<Unit>("lib-iml wrote into a layer without a nakshaCollection descriptor (Hub-managed): $outcome")
  }

  @Test
  fun `H-W2 - a descriptor update of a Hub-managed layer does not delete its history`() {
    val table = scratch.sqlTable("t_hist")
    val before = (P3.scalar("SELECT count(*) FROM $table WHERE next_version <> 9223372036854775807") as Number).toLong()
    require(before > 0) { "fixture: history rows" }
    val (_, storage) = scratch.open("iml-eval-p5x-hw2-$rnd") { writeEnabled = true }
    val context = NakshaContext.currentContext()
    val previous = context.su
    context.su = true
    try {
      storage.newWriteSession().use { w ->
        val collection = checkNotNull(w.getCollectionById(checkNotNull(w.getCatalogById(scratch.data)), "p5-hist"))
        collection.storeHistory = StoreMode.OFF
        w.executeWrite(WriteRequest().add(Write().updateCollection(collection, false)))
        w.commit()
      }
    } catch (e: NakshaException) {
      return
    } finally {
      context.su = previous
    }
    val after = (P3.scalar("SELECT count(*) FROM $table WHERE next_version <> 9223372036854775807") as Number).toLong()
    assertEquals(before, after, "history rows deleted by a collection descriptor update")
  }

  @Test
  fun `F13-F22 - a layer whose table was dropped gives COLLECTION_NOT_FOUND, not a raw PSQLException`() {
    scratch.addLayer("p5-dropped", "t_dropped", "ev-v1")
    val (_, storage) = scratch.open("iml-eval-p5x-f22-$rnd")
    P3.execute("DROP TABLE ${scratch.sqlTable("t_dropped")}")
    try {
      val response = storage.newReadSession().use { it.executeRead(scratch.request("p5-dropped")) }
      val error = (response as? ErrorResponse)?.error?.code
      assertEquals("CollectionNotFound", error, "expected an ErrorResponse(COLLECTION_NOT_FOUND)")
    } catch (e: NakshaException) {
      assertEquals(naksha.base.NakshaError.COLLECTION_NOT_FOUND, e.error.code)
    } catch (e: Throwable) {
      fail<Unit>("raw ${P3.describe(e)}")
    } finally {
      P3.execute("DELETE FROM \"${scratch.meta}\".space_meta WHERE id = 'p5-dropped'")
    }
  }

  @Test
  fun `A7 - a killed backend's original SQLState reaches the caller`() {
    val (_, storage) = scratch.open("iml-eval-p5x-a7-$rnd")
    val app = "p5x-kill-$rnd"
    var thrown: Throwable? = null
    val lock = P5.lock(scratch.sqlTable("t_lock"))
    try {
      val t = Thread {
        NakshaContext.currentContext().withAppId("iml-eval-parity")
        try { storage.newReadSession(SessionOptions(appId = "iml-eval-parity", appName = app, lockTimeout = 30_000)).use { it.executeRead(scratch.request("p5-lock")) } }
        catch (e: Throwable) { thrown = e }
      }.apply { start() }
      P5.waitFor(10_000) { P5.lockWaiters("t_lock").any { it.second == app } }
      P5.lockWaiters("t_lock").filter { it.second == app }.forEach { P5.terminate(it.first) }
      t.join(30_000)
    } finally {
      P5.release(lock)
    }
    val e = checkNotNull(thrown) { "fixture: the read must fail" }
    assertTrue(P5.sqlStates(e).contains("57P01"), "original error lost: ${P5.chain(e)}")
  }

  @Test
  fun `F17 - lazy materialization uses the session's options in a thread without a context appId`() {
    val (_, storage) = open("f17")
    Naksha.cache.clear(storage)
    var outcome = "?"
    val t = Thread {
      outcome = try {
        storage.newReadSession(SessionOptions(appId = "iml-eval-parity")).use { s ->
          "ok ${(s.executeRead(Parity.request(v1)) as SuccessResponse).features.filterNotNull().size}"
        }
      } catch (e: Throwable) { P3.describe(e) }
    }.apply { start() }
    t.join(60_000)
    assertEquals("ok 10", outcome)
  }

  @Test
  fun `F24 - a versionsToKeep=1 layer is not described as a history layer`() {
    val (_, storage) = open("f24")
    val collection = storage.newReadSession().use { s -> checkNotNull(s.getCollectionById(checkNotNull(s.getCatalogById("public")), "ev-v1")) }
    assertTrue(collection.storeHistory != StoreMode.ON, "ev-v1 (versionsToKeep=1) is described with storeHistory=${collection.storeHistory}")
  }

  @Suppress("unused")
  private fun json(x: Any?) = Platform.toJSON(x)
}
