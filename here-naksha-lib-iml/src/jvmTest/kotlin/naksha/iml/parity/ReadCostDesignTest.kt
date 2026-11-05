package naksha.iml.parity

import naksha.iml.ImlConfig
import naksha.iml.ImlStorage
import naksha.iml.ImlTuple
import naksha.iml.parity.EdgeSupport.case
import naksha.iml.parity.Parity.observe
import naksha.iml.parity.Parity.request
import naksha.model.Naksha
import naksha.model.Tuple
import naksha.model.request.SuccessResponse
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.sql.DriverManager

/**
 * Phase 4, topic "two-phase reads plus tuple cache vs IML's single-query reads".
 *
 * F15 (Phase 2/3) measured lib-iml 8–22× slower than the Hub for full reads. This test splits that cost, on `ev-big`
 * (120 000 points, versionsToKeep = 1), into:
 *  - A: lib-iml as shipped: selection (`executeRead`) + exact loading (`session.loadTuples`), cache cleared;
 *  - B: one statement with lib-iml's own projection (`ImlReadSession.kt:314`), strings only: JDBC + network floor;
 *  - C: B + `ImlTuple.read` per row (JSON parse → NakshaFeature → JBON tuple, `ImlTuple.kt:52-67`);
 *  - D: C + `Tuple.decodeFeature(null)` per tuple, which `ImlTuple.attach` does on every load (`ImlTuple.kt:204-207`);
 *  - H: Hub `/iterate?limit=10000` over all pages, HTTP only (server-side read + serialization).
 * So A − D is the cost of the two-phase protocol, D − B the cost of the conversion pipeline.
 * Three runs each; medians reported. Run with JaCoCo off (`scripts/p4-design.sh … off`, report 03 B12). Gated by `IML_P3_LARGE=true`.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@org.junit.jupiter.api.Timeout(value = 20, unit = java.util.concurrent.TimeUnit.MINUTES)
class ReadCostDesignTest {
  private lateinit var config: ImlConfig
  private lateinit var storage: ImlStorage
  private val space = "ev-big"
  private val runs = 3

  @BeforeAll
  fun setUp() {
    Parity.assumeEnabled()
    assumeTrue(System.getenv("IML_P3_LARGE") == "true", "Set IML_P3_LARGE=true")
    val (c, s) = Parity.open("iml-eval-p4-cost")
    config = c
    storage = s
  }

  @AfterAll
  fun tearDown() {
    if (::config.isInitialized) Naksha.removeStorage(config)
  }

  private fun ms(start: Long) = (System.nanoTime() - start) / 1_000_000

  private fun singleStatement(mode: Char): Pair<Int, Long> {
    val layer = checkNotNull(storage.metadata.getLayer("public", space))
    val catalogNumber = storage.metadata.catalog.catalogNumber
    val members = layer.collection.useMembers()
    val start = System.nanoTime()
    var n = 0
    var sink = 0L
    DriverManager.getConnection(Parity.dbUrl!!).use { conn ->
      conn.autoCommit = false
      conn.isReadOnly = true
      conn.prepareStatement(
        "SELECT id, i, version, next_version, operation, author, jsondata, ST_AsGeoJSON(geo, 15, 0) AS geometry_json FROM ${layer.sqlTable} " +
          "WHERE next_version = 9223372036854775807 AND operation != 'D' ORDER BY i"
      ).use { st ->
        st.fetchSize = 1000
        st.executeQuery().use { rs ->
          while (rs.next()) {
            when (mode) {
              'B' -> sink += (rs.getString("jsondata")?.length ?: 0) + (rs.getString("geometry_json")?.length ?: 0) + rs.getLong("i")
              'P' -> {
                // JSON parsing only, as in ImlTuple.read before encode(): body and geometry to NakshaFeature / platform maps.
                val f = naksha.model.objects.NakshaFeature.fromJson(ImlTuple.jsonNumbers(rs.getString("jsondata")))
                val g = rs.getString("geometry_json")?.let { naksha.base.Platform.fromJSON(ImlTuple.jsonNumbers(it)) }
                sink += f.id.length + (if (g == null) 0 else 1)
              }
              'C' -> sink += ImlTuple.read(rs, storage.number, catalogNumber, layer.number, members).featureBytes.size
              else -> {
                val t: Tuple = ImlTuple.read(rs, storage.number, catalogNumber, layer.number, members)
                sink += t.decodeFeature(null).id.length
              }
            }
            n++
          }
        }
      }
      conn.rollback()
    }
    check(sink > 0)
    return n to ms(start)
  }

  private fun libTwoPhase(): Triple<Int, Long, Long> {
    Naksha.cache.clear(storage)
    return storage.newReadSession().use { s ->
      var start = System.nanoTime()
      val r = s.executeRead(request(space)) as SuccessResponse
      val list = r.featureTupleList
      val selection = ms(start)
      start = System.nanoTime()
      s.loadTuples(list, 0, list.size)
      val load = ms(start)
      Naksha.cache.clear(storage)
      Triple(list.count { it?.tuple != null }, selection, load)
    }
  }

  private fun hubIterate(): Pair<Int, Long> {
    val token = Regex("\"(?:nextPageToken|handle)\"\\s*:\\s*\"([^\"]+)\"")
    val count = Regex("\"type\"\\s*:\\s*\"Feature\"")
    val start = System.nanoTime()
    var n = 0
    var handle: String? = null
    do {
      val q = arrayListOf("limit" to "10000")
      if (handle != null) q.add("handle" to handle)
      val r = EdgeSupport.hub("GET", "/spaces/$space/iterate", query = q)
      check(r.status == 200) { "iterate ${r.status}" }
      n += count.findAll(r.body).count()
      handle = token.find(r.body)?.groupValues?.get(1)
    } while (handle != null)
    return n to ms(start)
  }

  private fun median(values: List<Long>) = values.sorted()[values.size / 2]

  @Test
  fun `cost split of a full read`() {
    // Warm-up: one of each, not measured.
    libTwoPhase(); singleStatement('D'); hubIterate()
    val a = (1..runs).map { libTwoPhase() }
    val b = (1..runs).map { singleStatement('B') }
    val p = (1..runs).map { singleStatement('P') }
    val c = (1..runs).map { singleStatement('C') }
    val d = (1..runs).map { singleStatement('D') }
    val h = (1..runs).map { hubIterate() }
    val counts = setOf(a[0].first, b[0].first, p[0].first, c[0].first, d[0].first, h[0].first)
    val pMs = median(p.map { it.second })
    val aSel = median(a.map { it.second })
    val aLoad = median(a.map { it.third })
    val aTotal = median(a.map { it.second + it.third })
    val bMs = median(b.map { it.second })
    val cMs = median(c.map { it.second })
    val dMs = median(d.map { it.second })
    val hMs = median(h.map { it.second })
    val o = linkedMapOf<String, Any?>(
      "features" to counts,
      "A lib two-phase (selection + loadTuples) ms" to a.map { "${it.second}+${it.third}" },
      "B single statement, strings only ms" to b.map { it.second },
      "P single statement + JSON parse only ms" to p.map { it.second },
      "C single statement + ImlTuple.read ms" to c.map { it.second },
      "D single statement + ImlTuple.read + decodeFeature ms" to d.map { it.second },
      "H Hub iterate limit=10000 ms" to h.map { it.second },
      "median ms" to linkedMapOf("A.selection" to aSel, "A.load" to aLoad, "A" to aTotal, "B" to bMs, "P" to pMs, "C" to cMs, "D" to dMs, "H" to hMs),
      "of which JSON parse P-B ms" to (pMs - bMs),
      "of which copy+members+JBON encode C-P ms" to (cMs - pMs),
      "two-phase protocol cost A-D ms" to (aTotal - dMs),
      "conversion cost D-B ms" to (dMs - bMs),
      "of which JSON->JBON C-B ms" to (cMs - bMs),
      "of which decode D-C ms" to (dMs - cMs),
      "ratios" to linkedMapOf("A/H" to "%.1f".format(aTotal.toDouble() / hMs), "D/H" to "%.1f".format(dMs.toDouble() / hMs), "B/H" to "%.1f".format(bMs.toDouble() / hMs))
    )
    observe("p4-read-cost-split", o)
    case("P4-cost", "full read of 120 000 points: where the time goes (medians of $runs)",
      "Hub /iterate: $hMs ms",
      "lib-iml $aTotal ms (selection $aSel + load $aLoad); one statement: strings $bMs, +ImlTuple.read $cMs, +decode $dMs ms", "High (F15)")
    assertEquals(1, counts.size, "all paths must return the same number of features: $counts")
  }
}
