package naksha.iml

import java.util.Locale
import java.util.UUID
import java.sql.Connection
import java.sql.PreparedStatement
import naksha.base.AnyList
import naksha.base.Platform
import naksha.base.PlatformList
import naksha.base.PlatformMap
import naksha.base.proxy
import naksha.base.TupleNumber
import naksha.geo.SpBoundingBox
import naksha.model.Naksha
import naksha.model.NakshaContext
import naksha.model.SessionOptions
import naksha.model.objects.XyzMembers
import naksha.model.request.ReadFeatures
import naksha.model.request.SuccessResponse
import naksha.model.request.ops.Intersects
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test

/**
 * Opt-in, read-only wall-clock experiment, not a latency regression gate. Ten overlapping Hyderabad boxes return roughly 100000 cumulative features per scenario.
 * @since 3.0
 */
class ImlBoundingBoxPerformanceTest {
  private data class Box(val dx: Double, val dy: Double) {
    fun bounds() = SpBoundingBox(78.435 + dx, 17.210 + dy, 78.645 + dx, 17.420 + dy)
  }

  private data class Measurement(
    val selected: Int,
    val hits: Int,
    val repeated: Int,
    val selectionNanos: Long,
    val loadingNanos: Long,
    val decodeNanos: Long,
    val encodedBytes: Long,
    val checksum: Long
  )

  private data class DatabaseTime(val rows: Long, val executionMs: Double, val planningMs: Double)

  private val boxes = listOf(
    Box(-0.010, -0.010), Box(0.000, 0.000), Box(0.010, 0.010),
    Box(-0.010, 0.010), Box(0.010, -0.010), Box(-0.005, 0.000),
    Box(0.005, 0.000), Box(0.000, -0.005), Box(0.000, 0.005), Box(0.005, 0.005)
  )

  @Test
  fun `measure two phase reads across ten overlapping bounding boxes`() {
    assumeTrue(System.getenv("NAKSHA_RUN_IML_PERFORMANCE_TEST") == "true",
      "Set NAKSHA_RUN_IML_PERFORMANCE_TEST=true to run the performance experiment")
    val url = System.getenv("NAKSHA_TEST_IML_REMOTE_DB_URL")
    require(!url.isNullOrBlank()) { "NAKSHA_TEST_IML_REMOTE_DB_URL is required when the performance test is enabled" }
    val layer = System.getenv("NAKSHA_TEST_IML_REMOTE_LAYER")
      ?: "hrn:here-dev:data::olp-here:m499-drgnstn-source-08:amz-20m"
    NakshaContext.currentContext().withAppId("iml-bbox-performance")
    val config = ImlConfig("iml-perf-${UUID.randomUUID()}", url).apply { hardCap = 20000 }
    val options = SessionOptions(connectTimeout = 15000, socketTimeout = 180000, stmtTimeout = 120000)
    try {
      val storage = Naksha.useStorage(config) as ImlStorage
      assertTrue(!storage.config.writeEnabled)
      storage.newReadSession(options).use { session ->
        fun select(box: Box, limit: Int = storage.hardCap): SuccessResponse =
          session.executeRead(ReadFeatures().apply {
            catalogId = "public"
            collectionId = layer
            this.limit = limit
            queryMembers = Intersects(XyzMembers.XyzGeometry, box.bounds().toPolygon())
          }) as SuccessResponse

        // Warm up connection/query/mapping/decoding paths without populating the measured cache.
        val warmup = select(boxes[0], 100).featureTupleList
        session.loadTuples(warmup)
        for (entry in warmup) checkNotNull(entry?.tuple).decodeFeature(null)
        Naksha.cache.clear(storage)

        println("IML bbox experiment: ten boxes per scenario; counts include repeated features")
        println("load_ms includes cache lookup, database misses, tuple construction AND attachment decoding")
        println("redecode_ms is additional fresh decoding, excluded from two_phase_ms")
        println("Application-cache cold only: PostgreSQL/OS caches, JIT, GC and network are not controlled")
        val scenarios = LinkedHashMap<String, List<Measurement>>()
        for (coldEveryQuery in arrayOf(true, false)) {
          val scenario = if (coldEveryQuery) "cold-each" else "overlap-reuse"
          Naksha.cache.clear(storage)
          val measurements = ArrayList<Measurement>()
          val seen = HashSet<TupleNumber>()
          var common: MutableSet<TupleNumber>? = null
          var largest = 0
          println("scenario,query,features,cache_hits,repeated,new,select_ms,load_ms,redecode_ms,two_phase_ms,encoded_mib,select_features_per_s,load_features_per_s,redecode_features_per_s,two_phase_features_per_s")
          for ((index, box) in boxes.withIndex()) {
            if (coldEveryQuery) Naksha.cache.clear(storage)
            val selectionStart = System.nanoTime()
            val entries = select(box).featureTupleList
            val selectionNanos = System.nanoTime() - selectionStart
            assertTrue(entries.size < storage.hardCap, "Box ${index + 1} was truncated; recalibrate its size")
            assertTrue(entries.size in 5000..15000, "Unexpected box volume; recalibrate the fixed dataset/boxes")

            var hits = 0
            var repeated = 0
            val identities = HashSet<TupleNumber>(entries.size)
            for (entry in entries) {
              val selected = checkNotNull(entry)
              assertTrue(selected.tuple == null, "Phase one must return unloaded tuple identities")
              if (Naksha.cache[selected.tupleNumber] != null) hits++
              if (!seen.add(selected.tupleNumber)) repeated++
              identities.add(selected.tupleNumber)
            }
            if (common == null) common = identities.toMutableSet() else common.retainAll(identities)
            largest = maxOf(largest, entries.size)

            val loadingStart = System.nanoTime()
            session.loadTuples(entries)
            val loadingNanos = System.nanoTime() - loadingStart
            var encodedBytes = 0L
            var loaded = 0
            for (entry in entries) {
              val tuple = entry?.tuple ?: continue
              loaded++
              encodedBytes += tuple.featureBytes.size
            }
            assertEquals(entries.size, loaded, "Exact states disappeared or could not be loaded")

            var checksum = 0L
            val decodingStart = System.nanoTime()
            for (entry in entries) {
              val feature = checkNotNull(entry?.tuple).decodeFeature(null)
              checksum += feature.id.hashCode().toLong()
            }
            val decodeNanos = System.nanoTime() - decodingStart
            val measurement = Measurement(entries.size, hits, repeated, selectionNanos, loadingNanos, decodeNanos, encodedBytes, checksum)
            measurements.add(measurement)
            println(String.format(Locale.ROOT, "%s,%d,%d,%d,%d,%d,%.3f,%.3f,%.3f,%.3f,%.3f,%.1f,%.1f,%.1f,%.1f",
              scenario, index + 1, loaded, hits, repeated, loaded - repeated,
              millis(selectionNanos), millis(loadingNanos), millis(decodeNanos),
              millis(selectionNanos + loadingNanos), encodedBytes / 1048576.0,
              rate(loaded.toLong(), millis(selectionNanos)), rate(loaded.toLong(), millis(loadingNanos)),
              rate(loaded.toLong(), millis(decodeNanos)), rate(loaded.toLong(), millis(selectionNanos + loadingNanos))))
          }
          val total = measurements.sumOf { it.selected }
          val commonCount = common?.size ?: 0
          assertTrue(total in 80000..120000, "Expected roughly 100000 cumulative results per scenario")
          assertTrue(commonCount >= largest * 0.70, "Actual common feature overlap is below 70%; recalibrate boxes")
          println("$scenario overlap: cumulative=$total distinct=${seen.size} common=$commonCount common/largest=${percent(commonCount.toLong(), largest.toLong())}%")
          summary(scenario, measurements)
          scenarios[scenario] = measurements
        }
        val cold = scenarios.getValue("cold-each").sumOf { it.loadingNanos }
        val reused = scenarios.getValue("overlap-reuse").sumOf { it.loadingNanos }
        println(String.format(Locale.ROOT, "Materialization ratio cold/reuse: %.3fx (descriptive, not a pass/fail threshold)", cold.toDouble() / maxOf(1L, reused)))
        // Run diagnostics afterward so EXPLAIN does not warm pages between the application measurements.
        databaseMeasurements(session, storage, layer)
      }
    } finally {
      Naksha.removeStorage(config)
    }
  }

  private fun summary(name: String, measurements: List<Measurement>) {
    val count = measurements.sumOf { it.selected }.toLong()
    val selection = measurements.sumOf { it.selectionNanos }
    val loading = measurements.sumOf { it.loadingNanos }
    val decode = measurements.sumOf { it.decodeNanos }
    println(String.format(Locale.ROOT,
      "%s totals: features=%d select_ms=%.3f load_ms=%.3f redecode_ms=%.3f two_phase_ms=%.3f cache_hit_pct=%s encoded_mib=%.3f checksum=%d",
      name, count, millis(selection), millis(loading), millis(decode), millis(selection + loading),
      percent(measurements.sumOf { it.hits }.toLong(), count), measurements.sumOf { it.encodedBytes } / 1048576.0, measurements.sumOf { it.checksum }))
    println(String.format(Locale.ROOT, "%s medians: select_ms=%.3f load_ms=%.3f redecode_ms=%.3f",
      name, millis(median(measurements.map { it.selectionNanos })), millis(median(measurements.map { it.loadingNanos })),
      millis(median(measurements.map { it.decodeNanos }))))
    println(String.format(Locale.ROOT,
      "%s throughput_features_per_s: selection=%.1f materialization=%.1f fresh_redecode=%.1f two_phase=%.1f",
      name, rate(count, millis(selection)), rate(count, millis(loading)), rate(count, millis(decode)), rate(count, millis(selection + loading))))
  }

  private fun databaseMeasurements(session: ImlReadSession, storage: ImlStorage, layerId: String) {
    val layer = checkNotNull(storage.metadata.getLayer("public", layerId))
    val projection = "id, i, version, next_version, operation, author, jsondata, ST_AsGeoJSON(geo, 15, 0) AS geometry_json"
    val seen = HashSet<TupleNumber>()
    val totals = LinkedHashMap<String, DatabaseTime>()
    println("Database-only diagnostic pass: EXPLAIN ANALYZE, warm server caches, all ten boxes")
    println("Execution Time excludes JDBC row transfer/decoding and includes EXPLAIN instrumentation; planning is separate")
    println("Exact loading uses the production 500-pair batches; overlap-misses assumes all previously seen tuples are retained")
    println("EXPLAIN discards result rows: timings are executor cost, not full wire serialization or client throughput")
    println("db_query,box,rows,execution_ms,planning_ms,execution_features_per_s,planning_plus_execution_features_per_s")
    for ((index, box) in boxes.withIndex()) {
      val request = ReadFeatures().apply {
        catalogId = "public"
        collectionId = layerId
        limit = storage.hardCap
        queryMembers = Intersects(XyzMembers.XyzGeometry, box.bounds().toPolygon())
      }
      // Capture the exact same tuple identities used by production's second-phase predicate.
      val entries = (session.executeRead(request) as SuccessResponse).featureTupleList
      val numbers = ArrayList<TupleNumber>(entries.size)
      val misses = ArrayList<TupleNumber>()
      for (entry in entries) {
        val number = checkNotNull(entry).tupleNumber
        numbers.add(number)
        if (seen.add(number)) misses.add(number)
      }
      val query = ImlQuery(request, storage.hardCap, layer.collection.useMembers())
      val selection = query.history.select(layer.sqlTable, query.predicate, query.orderBy) + " LIMIT ?"
      val full = "SELECT $projection FROM ${layer.sqlTable} WHERE ${query.predicate} ORDER BY ${query.orderBy} LIMIT ?"
      session.withConnection { connection ->
        val timings = linkedMapOf(
          "bbox-identities" to explain(connection, selection) { it.setInt(query.bind(it), query.limit) },
          "bbox-full-projection" to explain(connection, full) { it.setInt(query.bind(it), query.limit) },
          "exact-all" to explainExact(connection, layer.sqlTable, projection, numbers),
          "exact-overlap-misses" to explainExact(connection, layer.sqlTable, projection, misses)
        )
        for ((name, timing) in timings) {
          val expected = if (name == "exact-overlap-misses") misses.size else numbers.size
          assertEquals(expected.toLong(), timing.rows, "Database diagnostic row count changed")
          println(String.format(Locale.ROOT, "%s,%d,%d,%.3f,%.3f,%.1f,%.1f", name, index + 1,
            timing.rows, timing.executionMs, timing.planningMs, rate(timing.rows, timing.executionMs),
            rate(timing.rows, timing.executionMs + timing.planningMs)))
          val previous = totals[name] ?: DatabaseTime(0, 0.0, 0.0)
          totals[name] = DatabaseTime(previous.rows + timing.rows, previous.executionMs + timing.executionMs, previous.planningMs + timing.planningMs)
        }
      }
    }
    for ((name, timing) in totals) {
      println(String.format(Locale.ROOT, "database %s totals: rows=%d execution_ms=%.3f planning_ms=%.3f execution_features_per_s=%.1f planning_plus_execution_features_per_s=%.1f",
        name, timing.rows, timing.executionMs, timing.planningMs, rate(timing.rows, timing.executionMs), rate(timing.rows, timing.executionMs + timing.planningMs)))
    }
  }

  private fun explainExact(connection: Connection, table: String, projection: String, numbers: List<TupleNumber>): DatabaseTime {
    var total = DatabaseTime(0, 0.0, 0.0)
    var start = 0
    while (start < numbers.size) {
      val end = minOf(start + 500, numbers.size)
      val predicates = ArrayList<String>(end - start)
      for (index in start until end) predicates.add("(i = ? AND version = ?)")
      val timing = explain(connection, "SELECT $projection FROM $table WHERE ${predicates.joinToString(" OR ")}") {
        var parameter = 1
        for (index in start until end) {
          it.setLong(parameter++, numbers[index].featureNumber)
          it.setLong(parameter++, numbers[index].version ushr 2)
        }
      }
      total = DatabaseTime(total.rows + timing.rows, total.executionMs + timing.executionMs, total.planningMs + timing.planningMs)
      start = end
    }
    return total
  }

  private fun explain(connection: Connection, sql: String, bind: (PreparedStatement) -> Unit): DatabaseTime =
    connection.prepareStatement("EXPLAIN (ANALYZE, FORMAT JSON, TIMING OFF) $sql").use { statement ->
      bind(statement)
      statement.executeQuery().use { rows ->
        check(rows.next())
        val result = (Platform.fromJSON(rows.getString(1)) as PlatformList).proxy(AnyList::class)[0] as naksha.base.AnyObject
        val plan = (result.getRaw("Plan") as PlatformMap).proxy(naksha.base.AnyObject::class)
        DatabaseTime((plan.getRaw("Actual Rows") as Number).toLong(),
          (result.getRaw("Execution Time") as Number).toDouble(), (result.getRaw("Planning Time") as Number).toDouble())
      }
    }

  private fun median(values: List<Long>): Long {
    val sorted = values.sorted()
    return (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2
  }

  private fun millis(nanos: Long): Double = nanos / 1000000.0
  private fun rate(rows: Long, milliseconds: Double): Double = if (rows == 0L) 0.0 else rows * 1000.0 / maxOf(0.001, milliseconds)
  private fun percent(part: Long, total: Long): String = String.format(Locale.ROOT, "%.2f", part * 100.0 / maxOf(1L, total))
}
