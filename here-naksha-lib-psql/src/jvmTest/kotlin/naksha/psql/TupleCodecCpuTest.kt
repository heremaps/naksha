package naksha.psql

import naksha.base.Action
import naksha.base.Id
import naksha.base.JvmMap
import naksha.base.Platform
import naksha.base.Version
import naksha.jbon.JbDecoder2
import naksha.jbon.JbEncoder2
import naksha.model.Tuple
import naksha.model.CompiledMemberLayout
import naksha.model.objects.NakshaCollection
import naksha.model.objects.NakshaFeature
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertContentEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** In-process codec measurements without PostgreSQL, fixture copying or JSON parsing in the timed loops. Tuple includes member extraction/coercion and its normal gzip policy; bare JBON does not. @since 3.0 */
@EnabledIfEnvironmentVariable(named = "NAKSHA_TEST_CODEC_CPU", matches = "true")
class TupleCodecCpuTest {
  @Volatile private var sink = 0L

  @Test
  fun measureBareJbonAndTupleEncodingAndDecoding() {
    JvmCpuProfile.enableCounters()
    val database = Id("codec_cpu")
    val catalog = Id("codec_catalog")
    val definition = NakshaCollection("codec_features", catalog.text)
    PgCollection.resolveLayout(definition)
    val members = definition.validatedMembers
    val layout = CompiledMemberLayout(members)
    val collection = Id(definition.id)
    val version = Version.now(0L, Action.VERSION).number
    for (payload in listOf("small", "large")) {
      // Do not reuse Plv8PerfTest's companion: initializing that subclass also initializes PgTestBase's database-backed static fixture.
      val resource = assertNotNull(javaClass.getResource(if (payload == "small") "/small_topology.json" else "/topology.json"))
      val template = (Platform.fromJSON(resource.readText()) as JvmMap).proxy(NakshaFeature::class)
      val fixtures = Array(64) { i -> template.copy<NakshaFeature>(recursive = true).apply { id = "codec_$i" } }
      val bytes = Array(fixtures.size) { i -> JbEncoder2().buildTupleFromMap(fixtures[i]) }
      val tuples = Array(fixtures.size) { i -> Tuple.encodeRaw(fixtures[i], database.number, catalog.intValue,
        collection.intValue, members, version, requestedAction = Action.CREATE).tuple }
      val compiledTuples = Array(fixtures.size) { i -> Tuple.encodeWithLayout(fixtures[i], database.number, catalog.intValue,
        collection.intValue, layout, version, requestedAction = Action.CREATE).tuple }
      for (i in fixtures.indices) {
        assertContentEquals(tuples[i].featureBytes, compiledTuples[i].featureBytes)
        val decoder = JbDecoder2()
        decoder.mapBytes(bytes[i])
        assertEquals(fixtures[i].id, decoder.toAnyObject().getRaw("id"))
        val feature = tuples[i].decodeFeature(null)
        assertEquals(fixtures[i].id, feature.id)
        assertEquals(fixtures[i].hasRaw("geometry"), feature.hasRaw("geometry"), "Preserve the fixture's geometry presence; the large topology fixture has no top-level geometry")
      }
      val iterations = if (payload == "small") 20000 else 2000
      for (operation in listOf("JBON_ENCODE", "JBON_DECODE", "TUPLE_ENCODE", "TUPLE_ENCODE_COMPILED", "TUPLE_DECODE", "TUPLE_DECODE_COMPILED", "TUPLE_DECODE_FRESH")) {
        val wall = ArrayList<Double>(5)
        val cpu = ArrayList<Double>(5)
        val allocations = ArrayList<Double>(5)
        var recording: jdk.jfr.Recording? = null
        try {
          for (round in 0 until 8) {
            // Codec recordings intentionally span all five measured rounds for sample density. The write-path recording uses only its last round. These instrumented timings are not unprofiled capacity estimates.
            if (round == 3) recording = JvmCpuProfile.recording("codec_${payload}_${operation.lowercase()}")
            val start = System.nanoTime()
            val cpuStart = JvmCpuProfile.cpuNanos()
            val allocationStart = JvmCpuProfile.allocatedBytes()
            var result = 0L
            for (i in 0 until iterations) {
              val index = i % fixtures.size
              when (operation) {
                "JBON_ENCODE" -> result += JbEncoder2().buildTupleFromMap(fixtures[index]).size
                "JBON_DECODE" -> {
                  val decoder = JbDecoder2()
                  decoder.mapBytes(bytes[index])
                  result += (decoder.toAnyObject().getRaw("id") as String).length
                }
                "TUPLE_ENCODE" -> result += Tuple.encodeRaw(fixtures[index], database.number, catalog.intValue,
                  collection.intValue, members, version, requestedAction = Action.CREATE).tuple.featureBytes.size
                "TUPLE_ENCODE_COMPILED" -> result += Tuple.encodeWithLayout(fixtures[index], database.number, catalog.intValue,
                  collection.intValue, layout, version, requestedAction = Action.CREATE).tuple.featureBytes.size
                "TUPLE_DECODE" -> result += tuples[index].decodeFeature(null).id.length
                "TUPLE_DECODE_COMPILED" -> result += compiledTuples[index].decodeFeature(null).id.length
                else -> result += Tuple(tuples[index].featureBytes, tuples[index].membersBook).decodeFeature(null).id.length
              }
            }
            val allocated = JvmCpuProfile.allocatedBytes() - allocationStart
            val cpuNanos = JvmCpuProfile.cpuNanos() - cpuStart
            val elapsed = System.nanoTime() - start
            sink = result
            assertTrue(sink > 0)
            if (round >= 3) {
              wall.add(elapsed / 1000.0 / iterations)
              cpu.add(cpuNanos / 1000.0 / iterations)
              allocations.add(allocated.toDouble() / iterations)
            }
          }
        } finally { JvmCpuProfile.finish(recording) }
        wall.sort(); cpu.sort(); allocations.sort()
        println("CODEC_CPU payload=$payload operation=$operation samples=${cpu.size} operations_per_sample=$iterations median_wall_us=${wall[wall.size / 2]} median_cpu_us=${cpu[cpu.size / 2]} median_allocated_bytes=${allocations[allocations.size / 2]} raw_jbon_bytes=${bytes[0].size} stored_tuple_bytes=${tuples[0].featureBytes.size} database=false processors=false fixture_copy=false jfr=${recording != null}")
      }
    }
  }
}
