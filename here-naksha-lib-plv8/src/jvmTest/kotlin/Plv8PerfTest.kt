import com.here.naksha.lib.jbon.*
import com.here.naksha.lib.plv8.*
import com.here.naksha.lib.plv8.GEO_TYPE_NULL
import com.here.naksha.lib.plv8.RET_OP
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.charset.StandardCharsets
import kotlin.test.assertEquals

class Plv8PerfTest : Plv8TestContainer() {

    private val topologyJson = Plv8PerfTest::class.java.getResource("/topology.json")!!.readText(StandardCharsets.UTF_8)

    @Test
    fun insertFeatures() {
        val session = NakshaSession.get()
        val topology = asMap(env.parse(topologyJson))
        val builder = XyzBuilder.create(65536)

        var op = builder.buildXyzOp(XYZ_OP_DELETE, "v2_perf_test", null)
        var feature = builder.buildFeatureFromMap(asMap(env.parse("""{"id":"v2_perf_test"}""")))
        var result = session.writeCollections(arrayOf(op), arrayOf(feature), arrayOf(GEO_TYPE_NULL), arrayOf(null), arrayOf(null))
        var table = assertInstanceOf(JvmPlv8Table::class.java, result)
        assertEquals(1, table.rows.size)
        assertTrue(XYZ_EXEC_RETAINED == table.rows[0][RET_OP] || XYZ_EXEC_DELETED == table.rows[0][RET_OP]) { table.rows[0][RET_ERR_MSG] }

        op = builder.buildXyzOp(XYZ_OP_CREATE, "v2_perf_test", null)
        feature = builder.buildFeatureFromMap(asMap(env.parse("""{"id":"v2_perf_test"}""")))
        result = session.writeCollections(arrayOf(op), arrayOf(feature), arrayOf(GEO_TYPE_NULL), arrayOf(null), arrayOf(null))
        table = assertInstanceOf(JvmPlv8Table::class.java, result)
        assertEquals(1, table.rows.size)
        assertTrue(XYZ_EXEC_CREATED == table.rows[0][RET_OP]) { table.rows[0][RET_ERR_MSG] }

        session.sql.execute("commit")

        var time = 0L
        val chunkSize = 1000
        val rounds = 10
        var r = 0
        while (r++ < rounds) {
            val op_arr = Array<ByteArray?>(chunkSize) { null }
            val feature_arr = Array<ByteArray?>(chunkSize) { null }
            val geo_type_arr = Array(chunkSize) { GEO_TYPE_NULL }
            val geo_arr = Array<ByteArray?>(chunkSize) { null }
            val tags_arr = Array<ByteArray?>(chunkSize) { null }
            var i = 0
            while (i < chunkSize) {
                val id: String = env.randomString(12)
                topology["id"] = id
                op_arr[i] = builder.buildXyzOp(XYZ_OP_CREATE, id, null)
                feature_arr[i] = builder.buildFeatureFromMap(topology)
                i++
            }
            val start = env.currentMillis()
            @Suppress("UNCHECKED_CAST")
            result = session.writeFeatures("v2_perf_test", op_arr as Array<ByteArray>, feature_arr, geo_type_arr, geo_arr, tags_arr)
            table = assertInstanceOf(JvmPlv8Table::class.java, result)
            assertEquals(chunkSize, table.rows.size)
            val end = env.currentMillis()
            time += (end - start).toLong()
        }
        val features_per_second = (rounds * chunkSize) / (time / 1000)
        println("Write $features_per_second features per second, total time: ${time}ms")
    }
}