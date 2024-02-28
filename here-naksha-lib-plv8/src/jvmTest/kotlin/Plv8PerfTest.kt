import com.here.naksha.lib.jbon.*
import com.here.naksha.lib.plv8.*
import com.here.naksha.lib.plv8.GEO_TYPE_NULL
import com.here.naksha.lib.plv8.RET_OP
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import java.nio.charset.StandardCharsets
import java.sql.Connection
import kotlin.test.assertEquals

class Plv8PerfTest : Plv8TestContainer() {

    data class Features(
            val size: Int,
            val idArr: Array<String?>,
            val opArr: Array<ByteArray?>,
            val featureArr: Array<ByteArray?>,
            val geoTypeArr: Array<Short>,
            val geoArr: Array<ByteArray?>,
            val tagsArr: Array<ByteArray?>
    )

    companion object {
        private val topologyJson = Plv8PerfTest::class.java.getResource("/topology.json")!!.readText(StandardCharsets.UTF_8)
        private lateinit var jvmSql: JvmPlv8Sql
        private lateinit var conn: Connection
        private lateinit var session: NakshaSession
        private var baseLine: Double = 0.0

        @BeforeAll
        @JvmStatic
        fun prepare() {
            session = NakshaSession.get()
            jvmSql = session.sql as JvmPlv8Sql
            val conn = jvmSql.conn
            check(conn != null)
            this.conn = conn
        }
    }

    private fun currentMicros(): Long = System.nanoTime() / 1000

    private fun createFeatures(size: Int = 2000): Features {
        val builder = XyzBuilder.create(65536)
        val topology = asMap(env.parse(topologyJson))
        val idArr = Array<String?>(size) { null }
        val opArr = Array<ByteArray?>(size) { null }
        val featureArr = Array<ByteArray?>(size) { null }
        val geoTypeArr = Array(size) { GEO_TYPE_NULL }
        val geoArr = Array<ByteArray?>(size) { null }
        val tagsArr = Array<ByteArray?>(size) { null }
        var i = 0
        while (i < size) {
            val id: String = env.randomString(12)
            topology["id"] = id
            idArr[i] = id
            opArr[i] = builder.buildXyzOp(XYZ_OP_CREATE, id, null)
            featureArr[i] = builder.buildFeatureFromMap(topology)
            i++
        }
        return Features(size, idArr, opArr, featureArr, geoTypeArr, geoArr, tagsArr)
    }

    @Order(1)
    @Test
    fun createBaseline() {
        var stmt = conn.prepareStatement("""DROP TABLE ptest;
CREATE TABLE ptest (uid int8, txn_next int8, geo_type int2, id text, xyz bytea, tags bytea, feature bytea, geo bytea);
""")
        stmt.use {
            stmt.executeUpdate()
        }
        conn.commit()
        val features = createFeatures(5000)
        val start = currentMicros()
        stmt = conn.prepareStatement("INSERT INTO ptest (id, feature) VALUES (?, ?)")
        stmt.use {
            var i = 0
            while (i < features.size) {
                stmt.setString(1, features.idArr[i])
                stmt.setBytes(2, features.featureArr[i])
                stmt.addBatch()
                i++
            }
            stmt.executeBatch()
        }
        val end = currentMicros()
        conn.commit()
        baseLine = printStatistics(features.size, 1, end - start)
    }

    @Order(2)
    @Test
    fun insertFeatures() {
        val session = NakshaSession.get()
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

        val useBatch = true
        var totalTime = 0L
        val chunkSize = 2000
        val rounds = 10
        var r = 0
        while (r++ < rounds) {
            val features = createFeatures(chunkSize)
            val start = currentMicros()
            val jvmSql = session.sql as JvmPlv8Sql
            val conn = jvmSql.conn
            check(conn != null)
            if (useBatch) {
                val stmt = conn.prepareStatement("INSERT INTO v2_perf_test (id, feature) VALUES (?, ?)")
                stmt.use {
                    var i = 0
                    while (i < chunkSize) {
                        stmt.setString(1, features.idArr[i])
                        stmt.setBytes(2, features.featureArr[i])
                        stmt.addBatch()
                        i++
                    }
                    stmt.executeBatch()
                    conn.commit()
                }
            } else {
                val stmt = conn.prepareStatement("SELECT * FROM naksha_write_features(?, ?, ?, ?, ?, ?)")
                stmt.use {
                    stmt.setString(1, "v2_perf_test")
                    stmt.setArray(2, conn.createArrayOf(SQL_BYTE_ARRAY, features.opArr))
                    stmt.setArray(3, conn.createArrayOf(SQL_BYTE_ARRAY, features.featureArr))
                    stmt.setArray(4, conn.createArrayOf(SQL_INT16, features.geoTypeArr))
                    stmt.setArray(5, conn.createArrayOf(SQL_BYTE_ARRAY, features.geoArr))
                    stmt.setArray(6, conn.createArrayOf(SQL_BYTE_ARRAY, features.tagsArr))
                    stmt.executeQuery()
                    conn.commit()
                }
            }
            val end = currentMicros()
            totalTime += (end - start)
        }
        printStatistics(chunkSize, rounds, totalTime, baseLine)
    }

    /**
     * Print statistics.
     * @param size Amount of features per round.
     * @param rounds Amount of rounds.
     * @param us Amount of microseconds the test took.
     * @param baseLine Base-line in microseconds.
     */
    private fun printStatistics(size: Int, rounds: Int, us: Long, baseLine: Double = 0.0): Double {
        val featuresWritten = (rounds * size).toDouble()
        val seconds = us.toDouble() / 1_000_000.0
        val featuresPerSecond = featuresWritten / seconds
        var microsPerFeature = us.toDouble() / featuresWritten
        if (baseLine == 0.0) {
            println("Write $featuresPerSecond features per second, ${microsPerFeature}us per feature, total time: ${seconds}s")
        } else {
            microsPerFeature -= baseLine
            println("Write $featuresPerSecond features per second, ${microsPerFeature}us per feature (baseline=${baseLine}us), total time: ${seconds}s")
        }
        return microsPerFeature
    }
}