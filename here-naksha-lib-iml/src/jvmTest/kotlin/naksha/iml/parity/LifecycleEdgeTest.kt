package naksha.iml.parity

import naksha.iml.ImlConfig
import naksha.iml.ImlStorage
import naksha.model.Naksha
import naksha.model.request.ReadCollections
import naksha.model.request.ReadHandle
import naksha.model.request.SuccessResponse
import naksha.iml.parity.EdgeSupport.case
import naksha.iml.parity.EdgeSupport.describe
import naksha.iml.parity.Parity.observe
import naksha.iml.parity.Parity.request
import naksha.iml.parity.Parity.timed
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

/**
 * Phase 3, items
 * - "a layer, or a branch, created in the Hub after the storage was initialized (the metadata cache)" (hypothesis H7), and
 * - "dropping a space or branch in the Hub while lib-iml holds handles".
 * Creates and deletes its own spaces `ev-p3-late*`, `ev-p3-drop*`, `ev-p3-br`; scratch metadata schemas are created and dropped by the test.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@org.junit.jupiter.api.Timeout(value = 10, unit = java.util.concurrent.TimeUnit.MINUTES)
class LifecycleEdgeTest {
  private val created = ArrayList<String>()
  private val scratchSchemas = ArrayList<String>()

  @BeforeAll
  fun setUp() = Parity.assumeEnabled()

  @AfterAll
  fun tearDown() {
    for (s in created) runCatching { EdgeSupport.deleteSpace(s) }
    for (s in scratchSchemas) runCatching { EdgeSupport.execute("DROP SCHEMA IF EXISTS \"$s\" CASCADE") }
  }

  private fun space(id: String, v2k: Int, count: Int, prefix: String = "f"): String {
    EdgeSupport.recreateSpace(id, v2k)
    created += id
    if (count > 0) for (chunk in EdgeSupport.grid(prefix, count, 75.0, 10.0).chunked(10_000)) EdgeSupport.putFeatures(id, chunk)
    return id
  }

  private fun libCount(storage: ImlStorage, space: String): String = try {
    storage.newReadSession().use { s -> "ok ${(s.executeRead(request(space)) as SuccessResponse).features.filterNotNull().size}" }
  } catch (e: Throwable) { describe(e) }

  private fun listed(storage: ImlStorage, space: String): Boolean = storage.newReadSession().use { s ->
    (s.executeRead(ReadCollections().apply { mapId = "public" }) as SuccessResponse).features.any { it?.id == space }
  }

  @Test
  fun `layer created after storage init`() {
    EdgeSupport.deleteSpace("ev-p3-late")
    val (config, storage) = Parity.open("iml-eval-p3-late")
    val (wConfig, wStorage) = Parity.open("iml-eval-p3-late-write") { writeEnabled = true }
    val o = LinkedHashMap<String, Any?>()
    try {
      space("ev-p3-late", 100, 3)
      o["hubAfterCreate"] = Parity.hub("/spaces/ev-p3-late/iterate").toString()
      o["libRead"] = libCount(storage, "ev-p3-late")
      o["libListed"] = listed(storage, "ev-p3-late")
      o["writeEnabledStorageRead"] = libCount(wStorage, "ev-p3-late")
      o["imlTransactionsTable"] = EdgeSupport.scalar("SELECT to_regclass('xyz_config.iml_transactions')::text")
      // Naksha.useStorage with an equal config returns the cached instance: still the old metadata.
      val again = Naksha.useStorage(config) as ImlStorage
      o["useStorageSameInstance"] = (again === storage)
      o["useStorageAgainRead"] = libCount(again, "ev-p3-late")
      Naksha.removeStorage(config)
      val (recreatedTime, fresh) = timed { Naksha.useStorage(config) as ImlStorage }.let { it.second to it.first }
      o["recreateMs"] = recreatedTime
      o["afterRecreateRead"] = libCount(fresh, "ev-p3-late")
    } finally {
      Naksha.removeStorage(config)
      Naksha.removeStorage(wConfig)
    }
    observe("lifecycle-late-layer", o)
    case("2 metadata cache", "layer created in the Hub after storage init",
      "Hub: visible immediately (${o["hubAfterCreate"]})",
      "lib: ${o["libRead"]}; not in ReadCollections (${o["libListed"]}); writeEnabled storage: ${o["writeEnabledStorageRead"]} (refresh needs ${o["imlTransactionsTable"] ?: "xyz_config.iml_transactions, which the Hub never writes"}); " +
        "Naksha.useStorage(same config) returns the same instance=${o["useStorageSameInstance"]} → ${o["useStorageAgainRead"]}; after removeStorage + useStorage (${o["recreateMs"]} ms): ${o["afterRecreateRead"]}",
      "High for a long-running reader: H7 confirmed; no refresh API, the whole storage (tuple cache, handles) must be recreated")
    assertTrue((o["libRead"] as String).contains("COLLECTION_NOT_FOUND") || (o["libRead"] as String).contains("CollectionNotFound"))
  }

  @Test
  fun `layer deleted and recreated with the same id`() {
    space("ev-p3-recreate", 100, 5, "old")
    val (config, storage) = Parity.open("iml-eval-p3-recreate")
    val o = LinkedHashMap<String, Any?>()
    try {
      o["tableBefore"] = EdgeSupport.table("ev-p3-recreate")
      o["readBefore"] = libCount(storage, "ev-p3-recreate")
      EdgeSupport.deleteSpace("ev-p3-recreate")
      o["tableAfterDelete"] = EdgeSupport.scalar("SELECT to_regclass(?)::text", "public.\"${o["tableBefore"]}\"")
      o["readAfterDelete"] = libCount(storage, "ev-p3-recreate")
      space("ev-p3-recreate", 100, 7, "new")
      o["tableAfterRecreate"] = EdgeSupport.table("ev-p3-recreate")
      o["readAfterRecreate(stale storage)"] = libCount(storage, "ev-p3-recreate")
      Naksha.removeStorage(config)
      val fresh = Naksha.useStorage(config) as ImlStorage
      o["readAfterStorageRecreate"] = fresh.newReadSession().use { s ->
        (s.executeRead(request("ev-p3-recreate")) as SuccessResponse).features.filterNotNull().map { it.id }.sorted().take(3)
      }
    } finally {
      Naksha.removeStorage(config)
    }
    observe("lifecycle-recreate-same-id", o)
    case("2 metadata cache", "layer deleted and recreated with the same id (new physical table)",
      "Hub: 404 after delete, new content after recreate",
      "lib (old storage): after delete ${o["readAfterDelete"]}; after recreate ${o["readAfterRecreate(stale storage)"]}; recreated storage: ${o["readAfterStorageRecreate"]}",
      "Medium: raw PSQLException instead of COLLECTION_NOT_FOUND; no stale data (new table name ${o["tableBefore"]} → ${o["tableAfterRecreate"]})")
  }

  @Test
  fun `branch created after init`() {
    space("ev-p3-br", 100, 3, "m")
    val (config, storage) = Parity.open("iml-eval-p3-branch")
    val o = LinkedHashMap<String, Any?>()
    try {
      val metaBefore = EdgeSupport.scalar("SELECT count(*) FROM xyz_config.space_meta")
      val head = (Parity.hubOk("/spaces/ev-p3-br/statistics").field("maxVersion") as Map<*, *>)["value"]
      val b = EdgeSupport.hub("POST", "/spaces/ev-p3-br/branches", """{"id":"bx","baseRef":"main:$head"}""")
      o["branchCreate"] = "${b.status}"
      EdgeSupport.putFeatures("ev-p3-br", listOf(EdgeSupport.point("b-only", 75.5, 10.5, mapOf("name" to "branch"))), listOf("versionRef" to "bx"))
      o["hubBranchRead"] = Parity.hub("/spaces/ev-p3-br/iterate", listOf("versionRef" to "bx")).features.map { it["id"] as String }.sorted()
      o["spaceMetaRowsBefore/After"] = "$metaBefore/${EdgeSupport.scalar("SELECT count(*) FROM xyz_config.space_meta")}"
      val root = EdgeSupport.table("ev-p3-br")!!
      o["branchTable"] = EdgeSupport.scalar("SELECT relname::text FROM pg_class WHERE relname LIKE ? AND relkind = 'p'", "${root}\\_%")
      o["libReadDefault"] = storage.newReadSession().use { s -> (s.executeRead(request("ev-p3-br")) as SuccessResponse).features.filterNotNull().map { it.id }.sorted() }
      o["libListedCollections"] = storage.newReadSession().use { s -> (s.executeRead(ReadCollections().apply { mapId = "public" }) as SuccessResponse).features.mapNotNull { it?.id }.filter { it.startsWith("ev-p3-br") } }
    } finally {
      Naksha.removeStorage(config)
    }
    observe("lifecycle-branch-after-init", o)
    case("2 metadata cache", "branch created in the Hub after storage init",
      "Hub: versionRef=bx works at once (${o["hubBranchRead"]})",
      "Hub branch creation adds no space_meta row (${o["spaceMetaRowsBefore/After"]}); the branch table ${o["branchTable"]} is invisible to lib-iml before and after a storage recreate; lib reads main: ${o["libReadDefault"]}",
      "High (= H1): the metadata cache is not the cause; branches are never discoverable from space_meta")
  }

  @Test
  fun `discovery cost with many layers`() {
    val scratch = "p3meta_" + System.nanoTime().toString(36)
    scratchSchemas += scratch
    val table = EdgeSupport.table("ev-hist")!!
    val o = LinkedHashMap<String, Any?>()
    EdgeSupport.execute("CREATE SCHEMA \"$scratch\"")
    EdgeSupport.execute("CREATE TABLE \"$scratch\".space_meta (id text NOT NULL, schem text NOT NULL, h_id text NOT NULL, meta jsonb NOT NULL DEFAULT '{}'::jsonb, PRIMARY KEY(id,schem))")
    for (count in listOf(1_000, 10_000, 50_000)) {
      EdgeSupport.execute("TRUNCATE \"$scratch\".space_meta")
      EdgeSupport.execute("INSERT INTO \"$scratch\".space_meta SELECT 'layer-' || g, 'public', ?, '{}' FROM generate_series(1, ?) g", table, count)
      val id = "iml-eval-p3-disc-$count"
      val heap = EdgeSupport.HeapSampler()
      val (pair, ms) = timed { runCatching { Parity.open(id) { metadataSchema = scratch } } }
      heap.close()
      o["$count layers"] = mapOf("initMs" to ms, "outcome" to (pair.exceptionOrNull()?.let { describe(it) } ?: "ok"), "heap" to heap.summary())
      pair.getOrNull()?.let { Naksha.removeStorage(it.first) }
    }
    // Which generated IDs collide? Collection numbers are 32-bit (Id.textToInt keeps 31 bits + sign).
    val seen = HashMap<Int, String>()
    val collisions = ArrayList<String>()
    for (g in 1..50_000) {
      val id = "layer-$g"
      val n = naksha.base.Id.textToInt(id)
      val other = seen.put(n, id)
      if (other != null) collisions += "$other/$id → $n"
    }
    o["collisionsAmong50000"] = collisions
    o["birthdayProbability(31 bit)"] = listOf(1_153, 10_000, 50_000, 100_000).associate { n -> "$n" to "%.4f".format(1 - Math.exp(-n.toDouble() * n / (2.0 * (1L shl 31)))) }
    observe("lifecycle-discovery-cost", o)
    case("2 metadata cache", "initialization with 1 000 / 10 000 / 50 000 space_meta rows (remote SIT has 1 153)",
      "Hub: per-request space config lookup (cached), no full scan at start",
      o.entries.filter { it.value is Map<*, *> && (it.value as Map<*, *>).containsKey("initMs") }.joinToString("; ") { "${it.key}: ${(it.value as Map<*, *>)["initMs"]} ms ${(it.value as Map<*, *>)["outcome"]}" },
      "Low (time): init and every refresh (= recreate) read and encode all layers")
    case("2 metadata cache", "32-bit collection-number collision between two layer ids",
      "Hub: layers are addressed by id; no numeric identity",
      "colliding pairs among layer-1…layer-50000: ${o["collisionsAmong50000"]}; birthday probability of ≥1 collision for N layers: ${o["birthdayProbability(31 bit)"]}",
      "High at scale: one collision anywhere in space_meta fails initialization of the whole storage (ImlMetadata 'Duplicate or colliding IML layer identities')")
  }

  @Test
  fun `space dropped while lib holds handles and lazy responses`() {
    space("ev-p3-drop", 100, 2_000, "d")
    val (config, storage) = Parity.open("iml-eval-p3-drop")
    val o = LinkedHashMap<String, Any?>()
    try {
      val session = storage.newReadSession()
      val first = session.executeRead(request("ev-p3-drop") { limit = 100; returnHandle = true }) as SuccessResponse
      val handle = first.getRaw("handle") as String
      Naksha.cache.clear(storage)
      val lazy = session.executeRead(request("ev-p3-drop")) as SuccessResponse
      val del = EdgeSupport.deleteSpace("ev-p3-drop")
      o["hubDelete"] = "${del.status} in ${del.millis} ms"
      o["tableAfterDelete"] = EdgeSupport.scalar("SELECT to_regclass(?)::text", "public.\"${EdgeSupport.table("ev-p3-drop") ?: "x"}\"")
      o["hubIterateAfterDelete"] = Parity.hub("/spaces/ev-p3-drop/iterate").toString()
      var pages = 0
      var served = 0
      o["handleAfterDelete"] = try {
        var r = session.executeRead(ReadHandle(handle).apply { returnHandle = true }) as SuccessResponse
        while (true) {
          pages++
          served += r.features.filterNotNull().size
          val h = r.getRaw("handle") as? String ?: break
          r = session.executeRead(ReadHandle(h).apply { returnHandle = true }) as SuccessResponse
        }
        "ok: $pages more pages, $served features served from the frozen snapshot"
      } catch (e: Throwable) { describe(e) }
      o["lazyMaterializeAfterDelete"] = try { "ok ${lazy.features.filterNotNull().size} of ${lazy.featureTupleList.size}" } catch (e: Throwable) { describe(e) }
      o["newReadAfterDelete"] = libCount(storage, "ev-p3-drop")
      session.close()
    } finally {
      Naksha.removeStorage(config)
    }
    observe("lifecycle-drop-space", o)
    case("10 drop", "space deleted in the Hub while lib holds a continuation handle",
      "Hub: deleted space → ${o["hubIterateAfterDelete"]}; IML handles are stateless, a later page fails with 404",
      "${o["handleAfterDelete"]}",
      "Medium: data of a deleted layer is served for up to 5 min; no invalidation")
    case("10 drop", "space deleted between selection and lazy materialization",
      "Hub: n/a (single-phase)", "${o["lazyMaterializeAfterDelete"]}; new read: ${o["newReadAfterDelete"]}",
      "Medium: raw PSQLException (relation does not exist), not COLLECTION_NOT_FOUND (F13)")
  }

  @Test
  fun `space dropped while a lib read is streaming`() {
    space("ev-p3-drop2", 1, 50_000, "s")
    val (config, storage) = Parity.open("iml-eval-p3-drop2")
    val o = LinkedHashMap<String, Any?>()
    try {
      Naksha.cache.clear(storage)
      var libOutcome = "?"
      var libMs = 0L
      val reader = Thread {
        naksha.model.NakshaContext.currentContext().withAppId("iml-eval-parity") // lazy loading (TupleCache) opens its own session from the thread context
        val start = System.currentTimeMillis()
        libOutcome = try { storage.newReadSession(naksha.model.SessionOptions(appId = "iml-eval-parity")).use { s -> "ok ${(s.executeRead(request("ev-p3-drop2")) as SuccessResponse).features.filterNotNull().size}" } } catch (e: Throwable) { describe(e) }
        libMs = System.currentTimeMillis() - start
      }
      reader.start()
      Thread.sleep(800) // selection is done after ~100 ms; materialization of 50 000 takes seconds
      val waiting = EdgeSupport.scalar("SELECT count(*) FROM pg_locks l JOIN pg_class c ON c.oid = l.relation WHERE c.relname LIKE ? AND l.granted", "${EdgeSupport.table("ev-p3-drop2")}%")
      val del = EdgeSupport.deleteSpace("ev-p3-drop2")
      reader.join(120_000)
      o["libLocksHeldWhenDeleteStarted"] = waiting
      o["hubDelete"] = "${del.status} ${del.error ?: ""} in ${del.millis} ms"
      o["libRead"] = "$libOutcome in $libMs ms"
    } finally {
      Naksha.removeStorage(config)
    }
    observe("lifecycle-drop-during-read", o)
    case("10 drop", "Hub deletes a space while lib materializes 50 000 features from it",
      "Hub: DELETE returns promptly",
      "Hub DELETE ${o["hubDelete"]} (lib held ${o["libLocksHeldWhenDeleteStarted"]} relation locks); lib read: ${o["libRead"]}",
      "Medium: one materialization transaction per read holds AccessShare locks on all partitions, so the Hub's DROP waits for it")
  }

  @Test
  fun `branch dropped while lib reads its table`() {
    space("ev-p3-brdrop", 100, 3, "m")
    val head = (Parity.hubOk("/spaces/ev-p3-brdrop/statistics").field("maxVersion") as Map<*, *>)["value"]
    check(EdgeSupport.hub("POST", "/spaces/ev-p3-brdrop/branches", """{"id":"bd","baseRef":"main:$head"}""").status == 200)
    EdgeSupport.putFeatures("ev-p3-brdrop", EdgeSupport.grid("bd", 300, 76.0, 10.0), listOf("versionRef" to "bd"))
    val root = EdgeSupport.table("ev-p3-brdrop")!!
    val branchTable = EdgeSupport.scalar("SELECT relname::text FROM pg_class WHERE relname LIKE ? AND relkind = 'p'", "${root}\\_%") as String
    // Register the branch table as a layer in a scratch metadata schema (the Phase 2 PoC approach; branches have no space_meta row).
    val scratch = "p3br_" + System.nanoTime().toString(36)
    scratchSchemas += scratch
    EdgeSupport.execute("CREATE SCHEMA \"$scratch\"")
    EdgeSupport.execute("CREATE TABLE \"$scratch\".space_meta (id text NOT NULL, schem text NOT NULL, h_id text NOT NULL, meta jsonb NOT NULL DEFAULT '{}'::jsonb, PRIMARY KEY(id,schem))")
    EdgeSupport.execute("INSERT INTO \"$scratch\".space_meta VALUES ('bd-delta', 'public', ?, '{}')", branchTable)
    val (config, storage) = Parity.open("iml-eval-p3-brdrop") { metadataSchema = scratch }
    val o = LinkedHashMap<String, Any?>()
    try {
      val session = storage.newReadSession()
      val first = session.executeRead(request("bd-delta") { limit = 100; returnHandle = true }) as SuccessResponse
      val handle = first.getRaw("handle") as String
      val del = EdgeSupport.hub("DELETE", "/spaces/ev-p3-brdrop/branches/bd")
      o["hubBranchDelete"] = "${del.status} ${del.error ?: ""}"
      o["branchTableAfterDelete"] = EdgeSupport.scalar("SELECT to_regclass(?)::text", "public.\"$branchTable\"")
      o["hubReadDeletedBranch"] = Parity.hub("/spaces/ev-p3-brdrop/iterate", listOf("versionRef" to "bd")).toString()
      o["handleAfterBranchDelete"] = try {
        val r = session.executeRead(ReadHandle(handle).apply { returnHandle = true }) as SuccessResponse
        "ok ${r.features.filterNotNull().size} features"
      } catch (e: Throwable) { describe(e) }
      o["newReadAfterBranchDelete"] = libCount(storage, "bd-delta")
      session.close()
    } finally {
      Naksha.removeStorage(config)
    }
    observe("lifecycle-drop-branch", o)
    case("10 drop", "branch deleted in the Hub while lib holds a handle on its table (scratch space_meta, Phase 2 PoC)",
      "Hub: ${o["hubReadDeletedBranch"]} after delete",
      "Hub delete ${o["hubBranchDelete"]}, table after delete: ${o["branchTableAfterDelete"]}; handle: ${o["handleAfterBranchDelete"]}; new read: ${o["newReadAfterBranchDelete"]}",
      "Medium: same as for spaces; relevant once branches are supported")
  }
}
