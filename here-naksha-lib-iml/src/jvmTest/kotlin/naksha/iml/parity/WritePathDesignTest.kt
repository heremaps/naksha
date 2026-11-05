package naksha.iml.parity

import naksha.iml.ImlConfig
import naksha.iml.ImlStorage
import naksha.iml.ImlWriteSession
import naksha.iml.parity.EdgeSupport.case
import naksha.iml.parity.EdgeSupport.describe
import naksha.iml.parity.Parity.observe
import naksha.model.Naksha
import naksha.model.NakshaContext
import naksha.model.objects.NakshaFeature
import naksha.model.request.ReadFeatures
import naksha.model.request.SuccessResponse
import naksha.model.request.Write
import naksha.model.request.WriteRequest
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

/**
 * Phase 4, topic "the writes: own control tables iml_catalogs, iml_transactions, iml_version_seq vs Hub's <table>_version_seq" (H8).
 *
 * Question: what happens when the opt-in production write path (`writeEnabled = true`) writes into layers the Hub owns,
 * and the Hub keeps writing afterwards? lib-iml allocates one version per transaction from its own storage-wide
 * `iml_version_seq`, reconciled once to max(version) over every registered layer; the Hub allocates per table from `<table>_version_seq`.
 *
 * Isolation: the Hub spaces are created by this test (`ev-p4-w*-<rand>`) and deleted at the end. lib-iml gets scratch metadata
 * schemas (`imlp4_m*_<rand>`) holding copies of only those spaces' `space_meta` rows, so its control tables are created there,
 * not in the Hub's `xyz_config`, and the reconciliation sees only the test layers. Both scratch schemas are dropped at the end.
 *
 * Fixture technique (labelled in the report): layer B's Hub sequence is advanced with `setval` to 99 960 before two Hub writes,
 * which simulates a layer with ~100 000 versions of history; the Hub itself then creates B's next history partition
 * (`ext.sql:1970-1972`, created ahead when `version % 100000 > 99950`).
 *
 * Records `EDGE|P4-writes|…` rows and `reports/edge/p4-*.json`; asserts only the fixture.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@org.junit.jupiter.api.Timeout(value = 10, unit = java.util.concurrent.TimeUnit.MINUTES)
class WritePathDesignTest {
  private val rnd = (1..5).map { "abcdefghijklmnopqrstuvwxyz0123456789".random() }.joinToString("")
  private val a = "ev-p4-wa-$rnd" // history, low versions
  private val b = "ev-p4-wb-$rnd" // history, ~100k versions (simulated)
  private val c = "ev-p4-wc-$rnd" // versionsToKeep = 1
  private val m1 = "imlp4_m1_$rnd" // registers A + B
  private val m2 = "imlp4_m2_$rnd" // registers C only
  private val configs = ArrayList<ImlConfig>()

  @BeforeAll
  fun setUp() {
    Parity.assumeEnabled()
    EdgeSupport.recreateSpace(a, 100)
    EdgeSupport.recreateSpace(b, 100)
    EdgeSupport.recreateSpace(c, 1)
    EdgeSupport.putFeatures(a, listOf(EdgeSupport.point("a1", 8.0, 50.0, mapOf("n" to 1))))
    EdgeSupport.putFeatures(a, listOf(EdgeSupport.point("a1", 8.0, 50.0, mapOf("n" to 2))))
    EdgeSupport.putFeatures(b, listOf(EdgeSupport.point("b1", 8.1, 50.0, mapOf("n" to 1))))
    EdgeSupport.scalar("SELECT setval(?::regclass, 99960, true)", "public.\"${EdgeSupport.table(b)}_version_seq\"")
    EdgeSupport.putFeatures(b, listOf(EdgeSupport.point("b1", 8.1, 50.0, mapOf("n" to 2))))
    EdgeSupport.putFeatures(b, listOf(EdgeSupport.point("b1", 8.1, 50.0, mapOf("n" to 3))))
    EdgeSupport.putFeatures(c, listOf(EdgeSupport.point("c1", 8.2, 50.0, mapOf("n" to 1))))
    EdgeSupport.putFeatures(c, listOf(EdgeSupport.point("c1", 8.2, 50.0, mapOf("n" to 2))))
    for ((schema, spaces) in listOf(m1 to listOf(a, b), m2 to listOf(c))) {
      EdgeSupport.execute("CREATE SCHEMA \"$schema\"")
      EdgeSupport.execute("CREATE TABLE \"$schema\".space_meta (LIKE xyz_config.space_meta INCLUDING ALL)")
      for (s in spaces) EdgeSupport.execute("INSERT INTO \"$schema\".space_meta SELECT * FROM xyz_config.space_meta WHERE id = ?", s)
    }
  }

  @AfterAll
  fun tearDown() {
    for (config in configs) runCatching { Naksha.removeStorage(config) }
    if (Parity.hubUrl.isNullOrBlank() || Parity.dbUrl.isNullOrBlank()) return
    for (s in listOf(a, b, c)) runCatching { EdgeSupport.deleteSpace(s) }
    for (schema in listOf(m1, m2)) runCatching { EdgeSupport.execute("DROP SCHEMA IF EXISTS \"$schema\" CASCADE") }
  }

  private fun open(metadataSchema: String): ImlStorage {
    val (config, storage) = Parity.open("iml-eval-p4-w-${metadataSchema.takeLast(9).replace('_', '-')}") {
      this.metadataSchema = metadataSchema
      writeEnabled = true
    }
    configs.add(config)
    return storage
  }

  private fun partitions(space: String): List<String> = EdgeSupport.db { conn ->
    conn.prepareStatement(
      "SELECT c.relname || ' ' || pg_get_expr(c.relpartbound, c.oid) FROM pg_inherits i JOIN pg_class c ON c.oid = i.inhrelid " +
        "JOIN pg_class p ON p.oid = i.inhparent WHERE p.relname = ? ORDER BY 1"
    ).use { st ->
      st.setString(1, EdgeSupport.table(space))
      st.executeQuery().use { rs -> generateSequence { if (rs.next()) rs.getString(1).replace(EdgeSupport.table(space)!!, "<t>") else null }.toList() }
    }
  }

  private fun rows(space: String, id: String): List<String> = EdgeSupport.db { conn ->
    conn.prepareStatement("SELECT version, next_version, operation, jsondata->'properties'->>'n', jsondata->'properties'->>'by', ST_AsText(geo) FROM public.\"${EdgeSupport.table(space)}\" WHERE id = ? ORDER BY version, next_version").use { st ->
      st.setString(1, id)
      st.executeQuery().use { rs ->
        generateSequence {
          if (!rs.next()) null else {
            val nv = rs.getLong(2).let { if (it == Long.MAX_VALUE) "MAX" else "$it" }
            "v=${rs.getLong(1)} nv=$nv op=${rs.getString(3)} n=${rs.getString(4)} by=${rs.getString(5)} geo=${rs.getString(6)}"
          }
        }.toList()
      }
    }
  }

  private fun hubSeq(space: String): Long = EdgeSupport.scalar("SELECT last_value FROM public.\"${EdgeSupport.table(space)}_version_seq\"") as Long

  private fun hubVersions(space: String, id: String, ref: String? = null, withNs: Boolean = false): String {
    val r = EdgeSupport.hub("GET", "/spaces/$space/features/${EdgeSupport.pathId(id)}", query = if (ref == null) emptyList() else listOf("version" to ref))
    if (r.status != 200) return "${r.status} ${r.body.take(160)}"
    val list = (r.json as? Map<*, *>)?.let { m -> if (m["type"] == "FeatureCollection") m["features"] as List<*> else listOf(m) } ?: emptyList<Any>()
    return list.joinToString(", ") { f ->
      val p = (f as Map<*, *>)["properties"] as Map<*, *>
      val ns = p["@ns:com:here:xyz"] as Map<*, *>
      "v=${ns["version"]} n=${p["n"]} by=${p["by"]}" + if (withNs) " ns=${ns.keys.sortedBy { it.toString() }} uuid=${ns["uuid"]} nsVersion=${ns["version"]}" else ""
    }
  }

  private fun hubPutRaw(space: String, id: String, lon: Double, n: Int) = EdgeSupport.hub(
    "PUT", "/spaces/$space/features",
    naksha.base.Platform.toJSON(mapOf("type" to "FeatureCollection", "features" to listOf(EdgeSupport.point(id, lon, 50.0, mapOf("n" to n))))), "application/geo+json"
  )

  /** Reads [id] through the writer, sets `properties.by = "lib-iml"`, upserts it and commits. Returns the allocated source version or the error. */
  private fun libUpdate(storage: ImlStorage, space: String, id: String): Pair<Long?, String> {
    val writer = storage.newWriteSession() as ImlWriteSession
    return writer.use { w ->
      try {
        val catalog = checkNotNull(w.getCatalogById("public"))
        val collection = checkNotNull(w.getCollectionById(catalog, space)) { "collection $space not discovered" }
        val current = (w.executeRead(ReadFeatures().apply { catalogId = "public"; collectionId = space; featureIds.add(id) }) as SuccessResponse).features.filterNotNull().single()
        current.properties.setRaw("by", "lib-iml")
        val response = w.executeWrite(WriteRequest().add(Write().upsertFeature(collection, current)).apply { returnResults = true }) as SuccessResponse
        val version = w.useTransaction().version.number ushr 2
        w.commit()
        version to "ok tn=${response.featureTupleList[0]?.tupleNumber} reconciledTables=${w.reconciledTables}"
      } catch (e: Throwable) {
        null to describe(e)
      }
    }
  }

  @Test
  fun `lib-iml writes into Hub layers next to Hub writes`() {
    NakshaContext.currentContext().withAppId("iml-eval-p4-writes")
    val o = LinkedHashMap<String, Any?>()
    o["fixture"] = mapOf(
      "A" to mapOf("space" to a, "versionsToKeep" to 100, "hubSeq" to hubSeq(a), "partitions" to partitions(a)),
      "B" to mapOf("space" to b, "versionsToKeep" to 100, "hubSeq" to hubSeq(b), "partitions" to partitions(b), "note" to "Hub sequence advanced by setval to 99960 (simulated history)"),
      "C" to mapOf("space" to c, "versionsToKeep" to 1, "hubSeq" to hubSeq(c), "partitions" to partitions(c))
    )
    assertTrue(partitions(b).any { it.startsWith("<t>_p1 ") }, "fixture: the Hub must have created B's second history partition")

    // 1. Storage m1 registers A and B. One lib-iml update of a1 (layer A, Hub versions 1..2).
    val s1 = open(m1)
    val seqA0 = hubSeq(a)
    val (vA, outA) = libUpdate(s1, a, "a1")
    val rowsA = rows(a, "a1")
    val seqA1 = hubSeq(a)
    // Three Hub replaces of a1, then a Hub write of a new feature in the same layer, then a Hub read at the lib version.
    val hubA = (1..3).map { hubPutRaw(a, "a1", 8.0, 10 + it).let { r -> "${r.status} ${r.body.take(160)}" } }
    val hubANew = hubPutRaw(a, "a2", 8.01, 1).let { r -> if (r.status == 200) "200 version=${Regex("\"version\"\\s*:\\s*(\\d+)").find(r.body)?.groupValues?.get(1)}" else "${r.status} ${r.body.take(160)}" }
    o["1 lib update a1 (A)"] = mapOf(
      "libVersion" to vA, "outcome" to outA, "rowsAfterLib" to rowsA, "hubSeqBefore" to seqA0, "hubSeqAfterLib" to seqA1,
      "hub PUT a1 x3 after lib" to hubA, "hubSeqAfterHubPuts" to hubSeq(a), "hub PUT new feature a2" to hubANew,
      "rowsAfterHub" to rows(a, "a1"), "hubHead a1" to hubVersions(a, "a1", withNs = true),
      // a2 was written after the lib-iml update of a1. Which a1 does the Hub return at a2's version?
      "hub a1 at a2's version" to Regex("version=(\\d+)").find(hubANew)?.groupValues?.get(1)?.let { "version=$it: " + hubVersions(a, "a1", it) },
      "hub changesets" to EdgeSupport.hub("GET", "/spaces/$a/changesets", query = listOf("startVersion" to "0", "endVersion" to "100000")).let { r ->
        "${r.status} versions=${Regex("\"(\\d+)\":\\{\"").findAll(r.body).map { it.groupValues[1] }.toList()}"
      }
    )
    case("P4-writes", "lib-iml update of a feature in Hub layer A (versions 1–2) while another registered layer B is at ~99 962",
      "Hub: next version of A is 3 (`<table>_version_seq`, GetNextVersion.java:47)",
      if (vA == null) "fails: $outA" else "lib version $vA (Hub sequence stays $seqA1); then Hub PUT a1 x3: ${hubA.map { it.take(3) }}; Hub PUT of new a2: $hubANew",
      "High (write path)")

    // 2. Same storage, update of b1 (layer B), then a Hub update of b1.
    val seqB0 = hubSeq(b)
    val (vB, outB) = libUpdate(s1, b, "b1")
    val rowsAfterLib = rows(b, "b1")
    val seqB1 = hubSeq(b)
    val libJson = EdgeSupport.scalar("SELECT jsondata->'properties'->'@ns:com:here:xyz' FROM public.\"${EdgeSupport.table(b)}\" WHERE id = 'b1' AND next_version = 9223372036854775807")?.toString()
    val hubPut = hubPutRaw(b, "b1", 8.1, 4)
    val hubPutVersion = if (hubPut.status != 200) "${hubPut.status} ${hubPut.body.take(300)}" else
      (((((hubPut.json as Map<*, *>)["features"] as List<*>)[0] as Map<*, *>)["properties"] as Map<*, *>)["@ns:com:here:xyz"] as Map<*, *>)["version"]
    // A second Hub attempt with conflictDetection=false explicitly, and a PATCH, to see whether the failure depends on the write mode.
    val hubRetry = if (hubPut.status == 200) null else hubPutRaw(b, "b1", 8.1, 5)
    val hubPatch = if (hubPut.status == 200) null else EdgeSupport.hub("PATCH", "/spaces/$b/features/b1", naksha.base.Platform.toJSON(mapOf("type" to "Feature", "properties" to mapOf("n" to 6))), "application/geo+json")
    val libHead = s1.newReadSession().use { r ->
      (r.executeRead(ReadFeatures().apply { catalogId = "public"; collectionId = b; featureIds.add("b1") }) as SuccessResponse).features.filterNotNull()
        .map { "v=${(it.properties.xyz.getRaw("version") as Number).toLong() ushr 2} n=${it.properties.getRaw("n")}" }
    }
    o["2 lib update b1 (B), then Hub update b1"] = mapOf(
      "libVersion" to vB, "libOutcome" to outB, "hubSeqBeforeLib" to seqB0, "hubSeqAfterLib" to seqB1, "rowsAfterLib" to rowsAfterLib,
      "libWrittenXyzNamespace" to libJson,
      "hubPutVersion" to hubPutVersion,
      "hubRetry" to hubRetry?.let { "${it.status} ${it.body.take(300)}" },
      "hubPatch" to hubPatch?.let { "${it.status} ${it.body.take(300)}" },
      "rowsAfterHub" to rows(b, "b1"),
      "hubHead" to hubVersions(b, "b1"), "hubAllVersions" to hubVersions(b, "b1", "*"),
      "hubAtLibVersion" to (vB?.let { hubVersions(b, "b1", "$it", withNs = true) }), "libHead" to libHead
    )
    case("P4-writes", "lib-iml update of b1 in layer B, then a Hub update of b1",
      "versions per layer strictly increase; every state is visible at its own version",
      "lib version $vB (Hub sequence stays $seqB1); Hub then writes version $hubPutVersion; rows ${rows(b, "b1")}; Hub HEAD ${hubVersions(b, "b1")}",
      "High (write path)")

    // 3. Storage m2 registers only C (versionsToKeep = 1).
    val s2 = open(m2)
    val seqC0 = hubSeq(c)
    fun bboxC() = EdgeSupport.hub("GET", "/spaces/$c/bbox", query = listOf("west" to "8", "south" to "49", "east" to "9", "north" to "51")).let { r -> "${r.status} ids=${Regex("\"id\"\\s*:\\s*\"([^\"]+)\"").findAll(r.body).map { it.groupValues[1] }.toList()}" }
    val bboxBefore = bboxC()
    val (vC, outC) = libUpdate(s2, c, "c1")
    val rowsCAfterLib = rows(c, "c1")
    val seqC1 = hubSeq(c)
    val hubHeadCBeforeHubWrite = hubVersions(c, "c1")
    val hubPutC = hubPutRaw(c, "c1", 8.2, 3)
    o["3 lib update c1 (C, versionsToKeep=1), then Hub update c1"] = mapOf(
      "libVersion" to vC, "libOutcome" to outC, "collectionStoreHistory" to s2.newReadSession().use { r -> r.getCollectionById(r.getCatalogById("public")!!, c)?.storeHistory?.toString() },
      "hubSeqBefore" to seqC0, "hubSeqAfterLib" to seqC1, "rowsAfterLib" to rowsCAfterLib, "hubHeadAfterLib" to hubHeadCBeforeHubWrite,
      "hubBboxBeforeLib" to bboxBefore, "hubBboxAfterLib" to bboxC(),
      "hubIterateAfterLib" to EdgeSupport.hub("GET", "/spaces/$c/iterate").let { r -> "${r.status} ids=${Regex("\"id\"\\s*:\\s*\"([^\"]+)\"").findAll(r.body).map { it.groupValues[1] }.toList()}" },
      "hubPut" to "${hubPutC.status} ${hubPutC.body.take(200)}", "hubSeqAfterHubPut" to hubSeq(c), "rowsAfterHub" to rows(c, "c1"),
      "hubHead" to hubVersions(c, "c1"), "hubAllVersions" to hubVersions(c, "c1", "*")
    )
    case("P4-writes", "lib-iml update of c1 in a versionsToKeep=1 layer, then a Hub update",
      "v1 layer: one row per feature, updated in place (internals doc §4.2)",
      "lib version $vC, rows after lib $rowsCAfterLib; Hub PUT ${hubPutC.status}; rows after Hub ${rows(c, "c1")}", "High (write path)")

    // 4. What the write path created, and where.
    fun relations(schema: String) = EdgeSupport.db { conn ->
      conn.prepareStatement("SELECT c.relname || ':' || c.relkind::text FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace WHERE n.nspname = ? AND c.relkind IN ('r','S','p') ORDER BY 1").use { st ->
        st.setString(1, schema)
        st.executeQuery().use { rs -> generateSequence { if (rs.next()) rs.getString(1) else null }.toList() }
      }
    }
    o["4 control objects"] = mapOf(
      m1 to relations(m1), m2 to relations(m2),
      "iml_version_seq m1" to EdgeSupport.scalar("SELECT last_value FROM \"$m1\".iml_version_seq"),
      "iml_transactions m1" to EdgeSupport.scalar("SELECT count(*) FROM \"$m1\".iml_transactions"),
      "xyz_config untouched" to (EdgeSupport.scalar("SELECT count(*) FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace WHERE n.nspname = 'xyz_config' AND c.relname LIKE 'iml_%'") as Long == 0L)
    )
    observe("p4-write-coexistence", o)
    assertEquals(0L, EdgeSupport.scalar("SELECT count(*) FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace WHERE n.nspname = 'xyz_config' AND c.relname LIKE 'iml_%'"))
  }
}
