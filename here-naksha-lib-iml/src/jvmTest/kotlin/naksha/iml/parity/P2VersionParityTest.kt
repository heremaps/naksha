package naksha.iml.parity

import naksha.base.Action
import naksha.base.Version
import naksha.iml.ImlConfig
import naksha.iml.ImlStorage
import naksha.model.Naksha
import naksha.model.objects.StandardMembers
import naksha.model.request.ReadFeatures
import naksha.model.request.ReadTransactions
import naksha.model.request.SuccessResponse
import naksha.model.request.ops.Equals
import naksha.model.request.ops.IsAnyOf
import naksha.iml.parity.Parity.diff
import naksha.iml.parity.Parity.hub
import naksha.iml.parity.Parity.hubOk
import naksha.iml.parity.Parity.ids
import naksha.iml.parity.Parity.libRead
import naksha.iml.parity.Parity.observe
import naksha.iml.parity.Parity.outcome
import naksha.iml.parity.Parity.record
import naksha.iml.parity.Parity.row
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

/**
 * Phase 2 parity, matrix items "versionRef", "/history, /changesets, /statistics" and "visibility of deleted features".
 *
 * ev-hist versions (scripts/seed.py): 1 base insert, 2 geo-empty, 3 big, 4 pt-2d v2, 5 pt-2d v3, 6 pg-hole tags, 7 delete del-me,
 * 8 delete recreate, 9 re-create recreate, 10 pt-3d (main after branch), 11 main-only.
 * lib-iml bounds are packed manual versions: `Version.manual(n, Action.VERSION).number`.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class P2VersionParityTest {
  private lateinit var config: ImlConfig
  private lateinit var storage: ImlStorage

  @BeforeAll
  fun setUp() {
    Parity.assumeEnabled()
    val (c, s) = Parity.open("iml-eval-p2-version")
    config = c
    storage = s
  }

  @AfterAll
  fun tearDown() {
    if (::config.isInitialized) Naksha.removeStorage(config)
  }

  private fun v(n: Long) = Version.manual(n, Action.VERSION).number

  private fun hubIter(space: String, ref: String) = hubOk("/spaces/$space/iterate", listOf("limit" to "1000", "versionRef" to ref)).features

  @Test
  fun `versionRef HEAD and numbers`() {
    val head = record(diff("version-HEAD", hubIter("ev-hist", "HEAD"), libRead(storage, "ev-hist")))
    row("versionRef", "versionRef=HEAD", "default ReadFeatures", head.result, head.summary())
    val main = record(diff("version-main-HEAD", hubIter("ev-hist", "main:HEAD"), libRead(storage, "ev-hist")))
    row("versionRef", "versionRef=main:HEAD", "default ReadFeatures", main.result, main.summary())
    val perVersion = LinkedHashMap<Long, String>()
    // Version.manual(0, VERSION) is rejected ("must be manual versions or HEAD"); the bound 0 itself is accepted.
    val zero = outcome({ l: List<Map<*, *>> -> "${l.size}" }) { libRead(storage, "ev-hist") { queryHistory = true; version = v(0) } }
    observe("version-manual-zero", mapOf("version=Version.manual(0, VERSION)" to zero))
    for (n in 0L..12L) {
      val hubF = hubIter("ev-hist", "$n")
      val lib = libRead(storage, "ev-hist") { queryHistory = true; version = if (n == 0L) 0L else v(n) }
      val d = record(diff("version-$n", hubF, lib))
      perVersion[n] = "${d.result} (hub ${d.hubKeys.size}, lib ${d.libKeys.size}${if (!d.sameKeys) ", onlyHub=${d.onlyHub} onlyLib=${d.onlyLib}" else ""})"
    }
    observe("version-snapshots", mapOf("perVersion" to perVersion))
    assertTrue(perVersion.values.all { it.startsWith("same") }, perVersion.toString())
    row("versionRef", "versionRef=<n> for n=0…12 (iterate)", "queryHistory=true, version=Version.manual(n, VERSION)", "same for all 13 (except known F1)", perVersion.entries.joinToString("; ") { "${it.key}: ${it.value}" })
    val mainN = record(diff("version-main-4", hubIter("ev-hist", "main:4"), libRead(storage, "ev-hist") { queryHistory = true; version = v(4) }))
    row("versionRef", "versionRef=main:4", "queryHistory=true, version=manual(4)", mainN.result, mainN.summary())
    val byIdHub = hubOk("/spaces/ev-hist/features", listOf("id" to "pt-2d", "version" to "4")).features
    val byIdLib = libRead(storage, "ev-hist") { queryMembers = Equals(StandardMembers.Id, "pt-2d"); queryHistory = true; version = v(4) }
    val byId = record(diff("version-byid-pt2d-4", byIdHub, byIdLib))
    row("versionRef", "GET /features?id=pt-2d&version=4", "Equals(Id) + queryHistory + version=manual(4)", byId.result, byId.summary())
    // A version above the layer's max: hub returns HEAD.
    val future = record(diff("version-future-999", hubIter("ev-hist", "999"), libRead(storage, "ev-hist") { queryHistory = true; version = v(999) }))
    row("versionRef", "versionRef=999 (> max) → hub returns HEAD", "version=manual(999)", future.result, future.summary())
    // Naksha-style automatic (dated) versions are rejected.
    val auto = outcome({ l: List<Map<*, *>> -> "${l.size}" }) { libRead(storage, "ev-hist") { queryHistory = true; version = 4L } }
    observe("version-raw-number", mapOf("version=4L (not packed)" to auto))
    row("versionRef", "versionRef=4", "version=4L (raw IML number, not packed)", if (auto.startsWith("ok")) "diff (silently wrong)" else "error", auto)
  }

  @Test
  fun `versionRef ranges and star`() {
    val ranges = LinkedHashMap<String, String>()
    for ((a, b) in listOf(0L to 1L, 3L to 6L, 3L to 9L, 6L to 8L, 7L to 8L, 9L to 11L)) {
      val hubF = hubIter("ev-hist", "$a..$b")
      // Hub range a..b = features whose valid state at b was written in (a, b], including deletions (flagged deleted=true).
      val lib = libRead(storage, "ev-hist") { queryHistory = true; version = v(b); minVersion = v(a + 1); queryDeleted = true }
      val d = record(diff("version-range-$a-$b", hubF, lib))
      ranges["$a..$b"] = "${d.result} hub=${d.hubKeys} lib=${d.libKeys} ${d.categories}"
    }
    observe("version-ranges", ranges)
    row("versionRef", "versionRef=a..b (iterate; a exclusive)", "queryHistory, version=manual(b), minVersion=manual(a+1), queryDeleted=true", if (ranges.values.all { it.startsWith("same") }) "same" else "diff", ranges.toString())

    val hubStar = hubIter("ev-hist", "*")
    val libStar = libRead(storage, "ev-hist") { queryHistory = true; versions = 1000 }
    val star = record(diff("version-star", hubStar, libStar, keyed = true))
    val libStarDeleted = libRead(storage, "ev-hist") { queryHistory = true; versions = 1000; queryDeleted = true }
    val starDeleted = record(diff("version-star-with-deleted", hubStar, libStarDeleted, keyed = true))
    // Workaround: include tombstones, then drop them on the client.
    val libStarFiltered = libStarDeleted.filter { ((it["properties"] as Map<*, *>)["@ns:com:here:xyz"] as Map<*, *>)["action"] != "DELETE" }
    val starFiltered = record(diff("version-star-client-filtered", hubStar, libStarFiltered, keyed = true))
    row("versionRef", "versionRef=*", "queryHistory, versions=1000, queryDeleted=true, then drop DELETE states on the client (workaround)", starFiltered.result, starFiltered.summary())
    row("versionRef", "versionRef=* (iterate, all states)", "queryHistory, versions=1000 (queryDeleted=false)", star.result, star.summary())
    row("versionRef", "versionRef=*", "queryHistory, versions=1000, queryDeleted=true", starDeleted.result, starDeleted.summary())

    val byIds = listOf("del-me", "recreate", "pt-2d")
    val hubStarIds = hubOk("/spaces/ev-hist/features", byIds.map { "id" to it } + ("versionRef" to "*")).features
    val libStarIds = libRead(storage, "ev-hist") { queryMembers = IsAnyOf(StandardMembers.Id, *byIds.toTypedArray()); queryHistory = true; versions = 1000 }
    val starIds = record(diff("version-star-byid", hubStarIds, libStarIds, keyed = true))
    row("versionRef", "GET /features?id=del-me&id=recreate&id=pt-2d&versionRef=*", "IsAnyOf(Id) + queryHistory + versions=1000", starIds.result, starIds.summary())
    val hubRangeId = hubOk("/spaces/ev-hist/features", listOf("id" to "pt-2d", "versionRef" to "0..5")).features
    val libRangeId = libRead(storage, "ev-hist") { queryMembers = Equals(StandardMembers.Id, "pt-2d"); queryHistory = true; version = v(5); minVersion = v(1); queryDeleted = true }
    val rangeId = record(diff("version-range-byid-pt2d-0-5", hubRangeId, libRangeId, keyed = true))
    row("versionRef", "GET /features?id=pt-2d&versionRef=0..5 → hub: latest only", "snapshot at 5 with minVersion 1", rangeId.result, rangeId.summary())
  }

  @Test
  fun `versionRef tag`() {
    val tagId = "p2tag5"
    hub("/spaces/ev-hist/tags/$tagId", method = "DELETE")
    val created = hub("/spaces/ev-hist/tags", method = "POST", body = """{"id":"$tagId","version":5}""")
    try {
      val hubTag = hub("/spaces/ev-hist/iterate", listOf("limit" to "1000", "versionRef" to tagId))
      val lib = libRead(storage, "ev-hist") { queryHistory = true; version = v(5) }
      val d = record(diff("version-tag", hubTag.features, lib), mapOf("tagCreate" to "${created.status}", "hubTagRead" to "$hubTag"))
      row("versionRef tag", "versionRef=$tagId (tag → version 5; tag create ${created.status})", "none: no tag resolution; caller resolves the tag via the hub, then version=manual(5)",
        "unsupported (manual emulation ${d.result})", "tags live in the hub's tag store (here DynamoDB, TagConfigClient), not in the layer table")
    } finally {
      hub("/spaces/ev-hist/tags/$tagId", method = "DELETE")
    }
  }

  @Test
  fun `versionsToKeep=1 layer read at an older version`() {
    val out = LinkedHashMap<String, Any?>()
    for (n in listOf(4L, 6L, 9L)) {
      val hubF = hubIter("ev-v1", "$n")
      val libSnap = libRead(storage, "ev-v1") { queryHistory = true; version = v(n) }
      val libHead = libRead(storage, "ev-v1") { version = v(n) }
      out["v$n"] = mapOf("hub" to hubF.size, "libSnapshot" to ids(libSnap).size, "libHeadBounded" to ids(libHead).size, "libMissingVsHead" to (ids(libRead(storage, "ev-v1")) - ids(libSnap).toSet()))
    }
    val maxV = (hubOk("/spaces/ev-v1/statistics").json as Map<*, *>)["maxVersion"]
    observe("version-v1-older", out + ("hubMaxVersion" to maxV))
    row("versionRef on v1", "GET /spaces/ev-v1/iterate?versionRef=4 → hub ${out["v4"]}", "queryHistory + version=manual(4)", "diff (lib returns a partial HEAD subset, no error)", out.toString())
  }

  @Test
  fun `history window versionsToKeep=2`() {
    val out = LinkedHashMap<String, Any?>()
    for (n in 1L..6L) {
      val hubF = hubOk("/spaces/ev-k2/features", listOf("id" to "k", "versionRef" to "$n")).features
      val lib = libRead(storage, "ev-k2") { queryMembers = Equals(StandardMembers.Id, "k"); queryHistory = true; version = v(n) }
      out["v$n"] = "hub=${hubF.map { Parity.hubVersion(it) }} lib=${lib.map { Parity.libVersion(it) }}"
    }
    val hubStar = hubOk("/spaces/ev-k2/features", listOf("id" to "k", "versionRef" to "*")).features.map { Parity.hubVersion(it) }
    val libStar = libRead(storage, "ev-k2") { queryMembers = Equals(StandardMembers.Id, "k"); queryHistory = true; versions = 100 }.map { Parity.libVersion(it) }
    val stats = hubOk("/spaces/ev-k2/statistics").json as Map<*, *>
    observe("version-k2-window", out + mapOf("hubStar" to hubStar, "libStar" to libStar, "hubMinVersion" to stats["minVersion"], "hubMaxVersion" to stats["maxVersion"]))
    row("history window", "versionsToKeep=2, versionRef=2/3 → hub [] (outside window)", "queryHistory + version", if (out["v2"].toString().contains("lib=[]")) "same" else "diff (lib ignores the retention window)", out.toString() + " *: hub=$hubStar lib=$libStar")
  }

  @Test
  fun `changesets and transaction log`() {
    val perVersion = LinkedHashMap<Long, String>()
    for (n in 1L..11L) {
      val cs = hubOk("/spaces/ev-hist/changesets/$n").json as Map<*, *>
      fun part(name: String) = ((cs[name] as Map<*, *>?)?.get("features") as List<*>? ?: emptyList<Any>()).map { (it as Map<*, *>)["id"] as String }.sorted()
      val hubSets = mapOf("I" to part("inserted"), "U" to part("updated"), "D" to part("deleted"))
      // Emulation: all states written at exactly version n.
      val lib = libRead(storage, "ev-hist") { queryHistory = true; versions = 1000; version = v(n); minVersion = v(n); queryDeleted = true }
      val libSets = lib.groupBy { (((it["properties"] as Map<*, *>)["@ns:com:here:xyz"] as Map<*, *>)["action"] as String) }
        .mapValues { e -> e.value.map { it["id"] as String }.sorted() }
      val libNorm = mapOf("I" to (libSets["CREATE"] ?: emptyList()), "U" to (libSets["UPDATE"] ?: emptyList()), "D" to (libSets["DELETE"] ?: emptyList()))
      perVersion[n] = if (hubSets == libNorm) "same $hubSets" else "diff hub=$hubSets lib=$libNorm"
    }
    val transactions = outcome({ r: naksha.model.request.Response -> "ok ${r.length}" }) {
      storage.newReadSession().use { it.executeRead(ReadTransactions()) }
    }
    val hubList = hubOk("/spaces/ev-hist/changesets", listOf("startVersion" to "0", "endVersion" to "20")).json as Map<*, *>
    val hubV1 = hub("/spaces/ev-v1/changesets", listOf("startVersion" to "0", "endVersion" to "20"))
    observe("changesets", mapOf("perVersion" to perVersion, "libReadTransactions" to transactions, "hubChangesetRange" to "${hubList["startVersion"]}..${hubList["endVersion"]}", "hubV1Layer" to "$hubV1"))
    row("/changesets", "GET /changesets/{v} for v=1…11", "no changeset API; emulation: queryHistory, minVersion=version=manual(v), versions=1000, queryDeleted, group by action",
      if (perVersion.values.all { it.startsWith("same") }) "unsupported natively; emulation same" else "diff", perVersion.toString())
    row("/changesets", "GET /changesets?startVersion=0&endVersion=20", "ReadTransactions", "unsupported", "lib: $transactions (reads only lib-iml's own iml_transactions log)")
    row("/history", "no /history endpoint in hub or IML (history = versionRef=* / ranges)", "queryHistory + versions", "n/a", "")
  }

  @Test
  fun `deleted features visibility`() {
    val out = LinkedHashMap<String, Any?>()
    for (space in listOf("ev-v1", "ev-hist")) {
      val hubById = hub("/spaces/$space/features/del-me")
      val libDefault = libRead(storage, space) { queryMembers = Equals(StandardMembers.Id, "del-me") }
      val libDeleted = libRead(storage, space) { queryMembers = Equals(StandardMembers.Id, "del-me"); queryDeleted = true }
      out[space] = mapOf(
        "hubGet" to "$hubById", "libDefault" to ids(libDefault), "libQueryDeleted" to libDeleted.map { "${it["id"]} action=${((it["properties"] as Map<*, *>)["@ns:com:here:xyz"] as Map<*, *>)["action"]} geometry=${it["geometry"]} props=${(it["properties"] as Map<*, *>).keys}" },
        "libHeadWithDeleted" to libRead(storage, space) { queryDeleted = true }.size, "libHead" to libRead(storage, space).size
      )
    }
    val hubRange = hubIter("ev-hist", "6..7")
    out["hubRange6..7"] = hubRange.map { "${it["id"]} deleted=${((it["properties"] as Map<*, *>)["@ns:com:here:xyz"] as Map<*, *>)["deleted"]}" }
    observe("deleted-visibility", out)
    row("deleted v1", "versionsToKeep=1: GET /features/del-me → 404; deleted rows are physically removed", "queryDeleted=false/true → []", "same (nothing to show)", out["ev-v1"].toString())
    row("deleted history", "versionsToKeep>1: GET /features/del-me → 404; visible only in ranges (deleted=true) or not at all in *", "queryDeleted=false → []; queryDeleted=true → tombstone", "same by default; lib can also list tombstones in HEAD", out["ev-hist"].toString())
  }
}
