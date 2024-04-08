import com.here.naksha.lib.jbon.*
import com.here.naksha.lib.plv8.*
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.runner.RunWith
import java.nio.charset.StandardCharsets
import java.sql.Connection
import java.sql.DriverManager
import java.util.concurrent.atomic.AtomicReferenceArray
import kotlin.test.assertEquals

@Suppress("ArrayInDataClass")
@ExtendWith(Plv8TestContainer::class)
class Plv8PerfTest : JbTest() {

    val GRID = 111

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
        private val FeaturesPerRound = 1000
        private val Rounds = 10
        private val BulkThreads = 8
        private val BulkSize = 10 * 1000

        private val topologyJson = Plv8PerfTest::class.java.getResource("/topology.json")!!.readText(StandardCharsets.UTF_8)
        internal var topologyTemplate: IMap? = null
        private val smallTopologyJson = Plv8PerfTest::class.java.getResource("/small_topology.json")!!.readText(StandardCharsets.UTF_8)
        internal var smallTopologyTemplate: IMap? = null
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
            opArr[i] = builder.buildXyzOp(XYZ_OP_CREATE, id, null, GRID)
            featureArr[i] = builder.buildFeatureFromMap(topology)
            i++
        }
        return Features(size, idArr, opArr, featureArr, geoTypeArr, geoArr, tagsArr)
    }

    @Order(1)
    @Test
    fun createBaseline() {
        var stmt = conn.prepareStatement("""DROP TABLE IF EXISTS ptest;
CREATE TABLE ptest (uid int8, txn_next int8, geo_type int2, id text, xyz bytea, tags bytea, feature bytea, geo bytea);
""")
        stmt.use {
            stmt.executeUpdate()
        }
        val features = createFeatures(FeaturesPerRound)
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
    @Disabled
    fun insertFeatures() {
        val session = NakshaSession.get()
        val builder = XyzBuilder.create(65536)

        var op = builder.buildXyzOp(XYZ_OP_DELETE, "v2_perf_test", null, GRID)
        var feature = builder.buildFeatureFromMap(asMap(env.parse("""{"id":"v2_perf_test"}""")))
        var result = session.writeCollections(arrayOf(op), arrayOf(feature), arrayOf(GEO_TYPE_NULL), arrayOf(null), arrayOf(null))
        var table = assertInstanceOf(JvmPlv8Table::class.java, result)
        assertEquals(1, table.rows.size)
        assertTrue(XYZ_EXEC_RETAINED == table.rows[0][RET_OP] || XYZ_EXEC_DELETED == table.rows[0][RET_OP]) { table.rows[0][RET_ERR_MSG] }

        op = builder.buildXyzOp(XYZ_OP_CREATE, "v2_perf_test", null, GRID)
        feature = builder.buildFeatureFromMap(asMap(env.parse("""{"id":"v2_perf_test"}""")))
        result = session.writeCollections(arrayOf(op), arrayOf(feature), arrayOf(GEO_TYPE_NULL), arrayOf(null), arrayOf(null))
        table = assertInstanceOf(JvmPlv8Table::class.java, result)
        assertEquals(1, table.rows.size)
        assertTrue(XYZ_EXEC_CREATED == table.rows[0][RET_OP]) { table.rows[0][RET_ERR_MSG] }

        session.sql.execute("commit")

        val useBatch = false
        var totalTime = 0L
        var r = 0
        while (r++ < Rounds) {
            val features = createFeatures(FeaturesPerRound)
            val start = currentMicros()
            if (useBatch) {
                val stmt = conn.prepareStatement("INSERT INTO v2_perf_test (id, geo_grid, feature) VALUES (?, ?, ?)")
                stmt.use {
                    var i = 0
                    while (i < FeaturesPerRound) {
                        stmt.setString(1, features.idArr[i])
                        stmt.setInt(2, GRID)
                        stmt.setBytes(3, features.featureArr[i])
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
        printStatistics(FeaturesPerRound, Rounds, totalTime, baseLine)
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
        val seconds = us.toSeconds()
        val featuresPerSecond = featuresWritten / seconds
        val microsPerFeature = us.toDouble() / featuresWritten
        if (baseLine == 0.0) {
            println("Write $featuresPerSecond features per second, ${microsPerFeature}us per feature, total time: ${seconds}s")
        } else {
            println("Write $featuresPerSecond features per second, ${microsPerFeature}us per feature (baseline=${baseLine}us), total time: ${seconds}s")
        }
        return microsPerFeature
    }

    data class BulkFeature(
            val id: String,
            val partId: Int,
            val op: ByteArray,
            val feature: ByteArray,
            val geoType: Short = GEO_TYPE_NULL,
            val geometry: ByteArray? = null,
            val tags: ByteArray? = null
    )

    private val xyzBuilder = XyzBuilder.create(65536)

    private fun getTopologyFeature(): IMap {
        var topology = topologyTemplate
        if (topology == null) {
            topology = asMap(env.parse(topologyJson))
            topologyTemplate = topology
        }
        return topology
    }

    private fun getSmallTopologyFeature(): IMap {
        var topology = smallTopologyTemplate
        if (topology == null) {
            topology = asMap(env.parse(smallTopologyJson))
            smallTopologyTemplate = topology
        }
        return topology
    }

    private var featureBytes: ByteArray? = null

    private fun createBulkFeature(): BulkFeature {
        val id = env.randomString(12)
        val topology = getSmallTopologyFeature()
        topology["id"] = id
        val partId = Static.partitionNumber(id)
        val op = xyzBuilder.buildXyzOp(XYZ_OP_CREATE, id, null, GRID)
        var featureBytes = this.featureBytes
        if (featureBytes == null) {
            featureBytes = xyzBuilder.buildFeatureFromMap(topology)
            this.featureBytes = featureBytes
            println("---------- Create feature of size ${featureBytes.size} -----------")
        }
        return BulkFeature(id, partId, op, featureBytes)
    }

    @Order(3)
    @Disabled
    @Test
    fun bulkLoadFeatures() {
        val tableName = "v2_bulk_test"
        createCollection(tableName, partition = true, disableHistory = true)

        // Run for 8 threads.
        val features = Array<ArrayList<BulkFeature>>(PARTITION_COUNT.toInt()) { ArrayList() }
        val featuresDone = AtomicReferenceArray<Boolean>(PARTITION_COUNT.toInt())
        var i = 0
        while (i < BulkSize) {
            val f = createBulkFeature()
            val p = f.partId
            check(p in 0..<PARTITION_COUNT)
            check(p == Static.partitionNumber(f.id))
            featuresDone.setRelease(p, false)
            val list = features[p]
            list.add(f)
            i++
        }
        i = 0
        while (i < features.size) {
            println("Features in bulk $i: ${features[i].size}")
            i++
        }
        println("Sleep 2 seconds")
        System.out.flush()
        Thread.sleep(2000)
        println("RUN")
        System.out.flush()
        val threads = Array(BulkThreads) {
            Thread {
                val threadId = it
                val conn = DriverManager.getConnection(Plv8TestContainer.url)
                conn.use {
                    val env = JvmPlv8Env.get()
                    env.startSession(
                            conn,
                            Plv8TestContainer.schema,
                            "plv8_${threadId}_thread",
                            env.randomString(),
                            "plv8_${threadId}_app",
                            "plv8_${threadId}_author"
                    )
                    val threadSession = NakshaSession.get()
                    conn.commit()
                    var p = 0
                    while (p < PARTITION_COUNT) {
                        if (featuresDone.compareAndSet(p, false, true)) {
                            val list = features[p]
                            val partName = Static.PARTITION_ID[p]
                            val partTableName = "${tableName}_p${partName}"
                            var j = 0
                            try {
                                val opArr = ArrayList<ByteArray>(list.size)
                                val fArr = ArrayList<ByteArray?>(list.size)
                                val geoTypeArr = ArrayList<Short>(list.size)
                                val geoArr = ArrayList<ByteArray?>(list.size)
                                val tagsArr = ArrayList<ByteArray?>(list.size)
                                while (j < list.size) {
                                    val f = list[j++]
                                    opArr.add(f.op)
                                    fArr.add(f.feature)
                                    geoArr.add(f.geometry)
                                    tagsArr.add(f.tags)
                                    geoTypeArr.add(f.geoType)
                                }
                                threadSession.bulkWriteFeatures(partTableName, opArr.toTypedArray(), fArr.toTypedArray(), geoTypeArr.toTypedArray(), geoArr.toTypedArray(), tagsArr.toTypedArray())
                                threadSession.sql.execute("commit")
                            } catch (e: Exception) {
                                throw e
                            }
                        }
                        p++
                    }
                }
            }
        }
        val start = currentMicros()
        i = 0
        while (i < threads.size) {
            threads[i++].start()
        }
        i = 0
        while (i < threads.size) {
            threads[i++].join()
        }
        val end = currentMicros()
        printStatistics(BulkSize, 1, (end - start), baseLine)
    }

    @Order(4)
    @Test
    fun bulkInsertFeatures() {
        val session = NakshaSession.get()

        val tableName = "v2_bulk_insert"
        createCollection(tableName, partition = false, disableHistory = false)

        var i = 0
        val numOfFeatures = 10_000
        val opArr = ArrayList<ByteArray>(numOfFeatures)
        val fArr = ArrayList<ByteArray?>(numOfFeatures)
        val geoTypeArr = ArrayList<Short>(numOfFeatures)
        val geoArr = ArrayList<ByteArray?>(numOfFeatures)
        val tagsArr = ArrayList<ByteArray?>(numOfFeatures)

        // insert features
        while (i < numOfFeatures) {
            val f = createBulkFeature()
            opArr.add(f.op)
            fArr.add(f.feature)
            geoArr.add(f.geometry)
            tagsArr.add(f.tags)
            geoTypeArr.add(f.geoType)
            i++
        }
        val insertsStart = currentMicros()
        val insertResult = session.bulkWriteFeatures(tableName, opArr.toTypedArray(), fArr.toTypedArray(), geoTypeArr.toTypedArray(), geoArr.toTypedArray(), tagsArr.toTypedArray())
        assertTrue(session.sql.rows(insertResult).isNullOrEmpty())
        session.sql.execute("commit")
        session.clear()

        println("Inserts done in: ${(currentMicros() - insertsStart).toSeconds()}s")

        // update features

        for (o in opArr.withIndex()) {
            val xyzOp = XyzOp().mapBytes(o.value)
            opArr[o.index] = XyzBuilder().buildXyzOp(XYZ_OP_UPDATE, xyzOp.id(), xyzOp.uuid(), xyzOp.grid())
        }
        val updateStart = currentMicros()
        val rowsUpdated = session.bulkWriteFeatures(tableName, opArr.toTypedArray(), fArr.toTypedArray(), geoTypeArr.toTypedArray(), geoArr.toTypedArray(), tagsArr.toTypedArray()) as JvmPlv8Table
        assertEquals(0, rowsUpdated.rows.size)
        session.sql.execute("commit")
        session.clear()
        println("Update ended in: ${(currentMicros() - updateStart).toSeconds()}s")


        // delete features
        for (o in opArr.withIndex()) {
            val xyzOp = XyzOp().mapBytes(o.value)
            opArr[o.index] = XyzBuilder().buildXyzOp(XYZ_OP_DELETE, xyzOp.id(), xyzOp.uuid(), xyzOp.grid())
        }
        val delStart = currentMicros()
        val rowsDeleted = session.bulkWriteFeatures(tableName, opArr.toTypedArray(), fArr.toTypedArray(), geoTypeArr.toTypedArray(), geoArr.toTypedArray(), tagsArr.toTypedArray()) as JvmPlv8Table
        assertEquals(0, rowsDeleted.rows.size)
        session.sql.execute("commit")
        session.clear()

        println("Delete ended in: ${(currentMicros() - delStart).toSeconds()}s")
    }

    @Order(5)
    @Test
    fun bulkAtomicsCheck() {
        // executed after bulkInsertFeatures
        val tableName = "v2_bulk_insert"
        val f = createBulkFeature()
        val opArr: Array<ByteArray> = arrayOf(f.op)
        val fArr: Array<ByteArray?> = arrayOf(f.feature)
        val geoTypeArr: Array<Short> = arrayOf(f.geoType)
        val geoArr: Array<ByteArray?> = arrayOf(f.geometry)
        val tagsArr: Array<ByteArray?> = arrayOf(f.tags)

        // insert features
        val insertResult = session.bulkWriteFeatures(tableName, opArr, fArr, geoTypeArr, geoArr, tagsArr)
        assertTrue(session.sql.rows(insertResult).isNullOrEmpty())
        session.sql.execute("commit")
        session.clear()


        val operationWithInvalidUuidToCheck: (Int) -> Unit = { operation ->
            for (o in opArr.withIndex()) {
                val xyzOp = XyzOp().mapBytes(o.value)
                opArr[o.index] = XyzBuilder().buildXyzOp(operation, xyzOp.id(), "invalid:uid:2024:1:1:1:1", xyzOp.grid())
            }
            val operationResult = session.bulkWriteFeatures(tableName, opArr, fArr, geoTypeArr, geoArr, tagsArr) as JvmPlv8Table
            val cols = asMap(operationResult.rows[0])
            assertEquals("ERROR", cols[RET_OP])
            assertEquals(ERR_CHECK_VIOLATION, cols[RET_ERR_NO])
            session.sql.execute("rollback")
            session.clear()
        }

        // update using wrong state
        operationWithInvalidUuidToCheck(XYZ_OP_UPDATE)
        // delete using wrong state
        operationWithInvalidUuidToCheck(XYZ_OP_DELETE)
        // purge using wrong state
        operationWithInvalidUuidToCheck(XYZ_OP_PURGE)
    }

    private fun createCollection(tableName: String, partition: Boolean, disableHistory: Boolean) {
        val builder = XyzBuilder.create(65536)
        var op = builder.buildXyzOp(XYZ_OP_DELETE, "$tableName", null, GRID)
        var feature = builder.buildFeatureFromMap(asMap(env.parse("""{"id":"$tableName"}""")))
        var result = session.writeCollections(arrayOf(op), arrayOf(feature), arrayOf(GEO_TYPE_NULL), arrayOf(null), arrayOf(null))
        var table = assertInstanceOf(JvmPlv8Table::class.java, result)
        assertEquals(1, table.rows.size)
        assertTrue(XYZ_EXEC_RETAINED == table.rows[0][RET_OP] || XYZ_EXEC_DELETED == table.rows[0][RET_OP]) { table.rows[0][RET_ERR_MSG] }

        op = builder.buildXyzOp(XYZ_OP_CREATE, "$tableName", null, GRID)
        feature = builder.buildFeatureFromMap(asMap(env.parse("""{"id":"$tableName","partition":$partition,"disableHistory": $disableHistory}""")))
        result = session.writeCollections(arrayOf(op), arrayOf(feature), arrayOf(GEO_TYPE_NULL), arrayOf(null), arrayOf(null))
        table = assertInstanceOf(JvmPlv8Table::class.java, result)
        assertEquals(1, table.rows.size)
        assertTrue(XYZ_EXEC_CREATED == table.rows[0][RET_OP]) { table.rows[0][RET_ERR_MSG] }

        session.sql.execute("commit")
    }

    private fun Long.toSeconds(): Double = this.toDouble() / 1_000_000.0
}