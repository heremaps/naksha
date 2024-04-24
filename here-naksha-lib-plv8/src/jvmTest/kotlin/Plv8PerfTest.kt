import com.here.naksha.lib.jbon.*
import com.here.naksha.lib.plv8.*
import com.here.naksha.lib.plv8.Static.PARTITION_COUNT
import com.here.naksha.lib.plv8.Static.PARTITION_ID
import com.here.naksha.lib.plv8.Static.SC_CONSISTENT
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
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
            val flagsArr: Array<Int>,
            val geoArr: Array<ByteArray?>,
            val tagsArr: Array<ByteArray?>
    )

    companion object {
        private const val UseSmallFeatures = false
        private val BaselineFeatures = if (UseSmallFeatures) 100_000 else 10_000
        private val InsertFeaturesPerRound = if (UseSmallFeatures) 10_000 else 1_000
        private val InsertRounds = 10
        private val BulkLoadThreads = 8
        private val BulkLoadSize = BulkLoadThreads * if (UseSmallFeatures) 35_000 else 10_000
        private val BulkWriteSize = if (UseSmallFeatures) 25_000 else 5_000

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

    private fun createFeatures(size: Int): Features {
        val builder = XyzBuilder.create(65536)
        val topology = asMap(env.parse(if (UseSmallFeatures) smallTopologyJson else topologyJson))
        val idArr = Array<String?>(size) { null }
        val opArr = Array<ByteArray?>(size) { null }
        val featureArr = Array<ByteArray?>(size) { null }
        val flagsArr = Array(size) { GEO_TYPE_NULL }
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
        return Features(size, idArr, opArr, featureArr, flagsArr, geoArr, tagsArr)
    }

    @Order(1)
    @Test
    fun createBaseline() {
        var stmt = conn.prepareStatement("""DROP TABLE IF EXISTS baseline_test;
CREATE TABLE baseline_test (uid int8, txn_next int8, flags int4, id text, xyz bytea, tags bytea, feature bytea, geo bytea);
""")
        stmt.use {
            stmt.executeUpdate()
        }
        val features = createFeatures(BaselineFeatures)
        println("Create baseline for features of size ${features.featureArr[0]!!.size}")
        val start = currentMicros()
        stmt = conn.prepareStatement("INSERT INTO baseline_test (id, feature) VALUES (?, ?)")
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
        while (r++ < InsertRounds) {
            val features = createFeatures(InsertFeaturesPerRound)
            val start = currentMicros()
            if (useBatch) {
                val stmt = conn.prepareStatement("INSERT INTO v2_perf_test (id, geo_grid, feature) VALUES (?, ?, ?)")
                stmt.use {
                    var i = 0
                    while (i < InsertFeaturesPerRound) {
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
                    stmt.setArray(4, conn.createArrayOf(SQL_INT16, features.flagsArr))
                    stmt.setArray(5, conn.createArrayOf(SQL_BYTE_ARRAY, features.geoArr))
                    stmt.setArray(6, conn.createArrayOf(SQL_BYTE_ARRAY, features.tagsArr))
                    stmt.executeQuery()
                    conn.commit()
                }
            }
            val end = currentMicros()
            totalTime += (end - start)
        }
        printStatistics(InsertFeaturesPerRound, InsertRounds, totalTime, baseLine)
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
            val flags: Int = GEO_TYPE_NULL,
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
        val topology = if (UseSmallFeatures) getSmallTopologyFeature() else getTopologyFeature()
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
    @Test
    fun bulkLoadFeatures() {
        val tableName = "v2_bulk_load"
        createCollection(tableName, partition = true, disableHistory = true, storageClass = SC_CONSISTENT)

        // Run for bulk threads in virtual partitions.
        val featuresByVp = Array<ArrayList<BulkFeature>>(BulkLoadThreads) { ArrayList() }
        val partNameByVp = Array<String?>(BulkLoadThreads) { null }
        val featuresDoneByVp = AtomicReferenceArray<Boolean>(BulkLoadThreads)
        var i = 0
        while (i < BulkLoadSize) {
            val f = createBulkFeature()

            // Verify physical partition
            val p = f.partId
            check(p in 0..<PARTITION_COUNT)
            check(p == Static.partitionNumber(f.id))

            // Assign to virtual partition.
            val vp = Static.partitionIndex(f.id, BulkLoadThreads)
            check(vp in 0..<BulkLoadThreads)
            partNameByVp[vp] = PARTITION_ID[p]
            featuresDoneByVp.setRelease(vp, false)
            val features = featuresByVp[vp]
            features.add(f)
            i++
        }
        i = 0
        while (i < featuresByVp.size) {
            println("Features in bulk $i: ${featuresByVp[i].size}")
            i++
        }
        println("Sleep 2 seconds")
        System.out.flush()
        Thread.sleep(2000)
        println("RUN")
        System.out.flush()
        val threads = Array(BulkLoadThreads) {
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
                    var vp = 0
                    while (vp < BulkLoadThreads) {
                        threadSession.sql.execute("SET LOCAL session_replication_role = replica;")
                        if (featuresDoneByVp.compareAndSet(vp, false, true)) {
                            val features = featuresByVp[vp]
                            var j = 0
                            try {
                                val opArr = ArrayList<ByteArray>(features.size)
                                val fArr = ArrayList<ByteArray?>(features.size)
                                val flagsArr = ArrayList<Int>(features.size)
                                val geoArr = ArrayList<ByteArray?>(features.size)
                                val tagsArr = ArrayList<ByteArray?>(features.size)
                                while (j < features.size) {
                                    val f = features[j++]
                                    opArr.add(f.op)
                                    fArr.add(f.feature)
                                    geoArr.add(f.geometry)
                                    tagsArr.add(f.tags)
                                    flagsArr.add(f.flags)
                                }
                                val result = threadSession.writeFeatures(tableName, opArr.toTypedArray(), fArr.toTypedArray(), flagsArr.toTypedArray(), geoArr.toTypedArray(), tagsArr.toTypedArray())
                                if (result is JvmPlv8Table && result.rows.size > 0) {
                                    val err = result.rows[0]
                                    println("Error: ${err.getAny(RET_ERR_NO) as String} - ${err.getAny(RET_ERR_MSG) as String}")
                                }
                                threadSession.sql.execute("commit")
                            } catch (e: Exception) {
                                e.printStackTrace()
                                throw e
                            }
                        }
                        vp++
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
        printStatistics(BulkLoadSize, 1, (end - start), baseLine)
    }

    @Order(4)
    @Test
    fun bulkWriteFeatures() {
        val session = NakshaSession.get()

        val tableName = "v2_bulk_write"
        createCollection(tableName, partition = true, disableHistory = false, storageClass = SC_CONSISTENT)

        // We only run with a single thread!
        var i = 0
        val opArr = ArrayList<ByteArray>(BulkWriteSize)
        val fArr = ArrayList<ByteArray?>(BulkWriteSize)
        val flagsArr = ArrayList<Int>(BulkWriteSize)
        val geoArr = ArrayList<ByteArray?>(BulkWriteSize)
        val tagsArr = ArrayList<ByteArray?>(BulkWriteSize)

        // insert features
        while (i < BulkWriteSize) {
            val f = createBulkFeature()
            opArr.add(f.op)
            fArr.add(f.feature)
            geoArr.add(f.geometry)
            tagsArr.add(f.tags)
            flagsArr.add(f.flags)
            i++
        }
        val insertsStart = currentMicros()
        val insertResult = session.writeFeatures(tableName, opArr.toTypedArray(), fArr.toTypedArray(), flagsArr.toTypedArray(), geoArr.toTypedArray(), tagsArr.toTypedArray())
        assertTrue(session.sql.rows(insertResult).isNullOrEmpty())
        session.sql.execute("commit")
        val insertEnd = currentMicros()
        session.clear()

        println("Inserts done in: ${(insertEnd - insertsStart).toSeconds()}s")
        printStatistics(BulkWriteSize, 1, (insertEnd - insertsStart), baseLine)

        // update features
        var updateCount = 0
        for (o in opArr.withIndex()) {
            val xyzOp = XyzOp().mapBytes(o.value)
            opArr[o.index] = XyzBuilder().buildXyzOp(XYZ_OP_UPDATE, xyzOp.id(), xyzOp.uuid(), xyzOp.grid())
            updateCount++
        }
        val updateStart = currentMicros()
        val rowsUpdated = session.writeFeatures(tableName, opArr.toTypedArray(), fArr.toTypedArray(), flagsArr.toTypedArray(), geoArr.toTypedArray(), tagsArr.toTypedArray()) as JvmPlv8Table
        assertEquals(0, rowsUpdated.rows.size)
        session.sql.execute("commit")
        val updateEnd = currentMicros()
        session.clear()
        println("Update ended in: ${(updateEnd - updateStart).toSeconds()}s")
        printStatistics(updateCount, 1, (updateEnd - updateStart), baseLine)


        // delete features
        var deleteCount = 0
        for (o in opArr.withIndex()) {
            val xyzOp = XyzOp().mapBytes(o.value)
            opArr[o.index] = XyzBuilder().buildXyzOp(XYZ_OP_DELETE, xyzOp.id(), xyzOp.uuid(), xyzOp.grid())
            deleteCount++
        }
        val delStart = currentMicros()
        val rowsDeleted = session.writeFeatures(tableName, opArr.toTypedArray(), fArr.toTypedArray(), flagsArr.toTypedArray(), geoArr.toTypedArray(), tagsArr.toTypedArray()) as JvmPlv8Table
        assertEquals(0, rowsDeleted.rows.size)
        session.sql.execute("commit")
        val delEnd = currentMicros()
        session.clear()

        println("Delete ended in: ${(delEnd - delStart).toSeconds()}s")
        printStatistics(deleteCount, 1, (delEnd - delStart), baseLine)
    }

    @Order(5)
    @Test
    fun bulkAtomicsCheck() {
        // executed after bulkInsertFeatures
        val tableName = "v2_bulk_write"
        val f = createBulkFeature()
        val opArr: Array<ByteArray> = arrayOf(f.op)
        val fArr: Array<ByteArray?> = arrayOf(f.feature)
        val flagsArr: Array<Int?> = arrayOf(f.flags)
        val geoArr: Array<ByteArray?> = arrayOf(f.geometry)
        val tagsArr: Array<ByteArray?> = arrayOf(f.tags)

        // insert features
        val insertResult = session.writeFeatures(tableName, opArr, fArr, flagsArr, geoArr, tagsArr)
        assertTrue(session.sql.rows(insertResult).isNullOrEmpty())
        session.sql.execute("commit")
        session.clear()


        val operationWithInvalidUuidToCheck: (Int) -> Unit = { operation ->
            for (o in opArr.withIndex()) {
                val xyzOp = XyzOp().mapBytes(o.value)
                opArr[o.index] = XyzBuilder().buildXyzOp(operation, xyzOp.id(), "invalid:uid:2024:1:1:1:1", xyzOp.grid())
            }
            val operationResult = session.writeFeatures(tableName, opArr, fArr, flagsArr, geoArr, tagsArr) as JvmPlv8Table
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

    private fun createCollection(tableName: String, partition: Boolean, disableHistory: Boolean, storageClass: String?=null) {
        val builder = XyzBuilder.create(65536)
        var op = builder.buildXyzOp(XYZ_OP_DELETE, tableName, null, GRID)
        var feature = builder.buildFeatureFromMap(asMap(env.parse("""{"id":"$tableName"}""")))
        var result = session.writeCollections(arrayOf(op), arrayOf(feature), arrayOf(GEO_TYPE_NULL), arrayOf(null), arrayOf(null))
        var table = assertInstanceOf(JvmPlv8Table::class.java, result)
        assertEquals(1, table.rows.size)
        assertTrue(XYZ_EXEC_RETAINED == table.rows[0][RET_OP] || XYZ_EXEC_DELETED == table.rows[0][RET_OP]) { table.rows[0][RET_ERR_MSG] }

        op = builder.buildXyzOp(XYZ_OP_CREATE, tableName, null, GRID)
        val sc = if (storageClass==null) "null" else "\"$storageClass\""
        feature = builder.buildFeatureFromMap(asMap(env.parse("""{"id":"$tableName","partition":$partition,"disableHistory":$disableHistory,"storageClass":$sc}""")))
        result = session.writeCollections(arrayOf(op), arrayOf(feature), arrayOf(GEO_TYPE_NULL), arrayOf(null), arrayOf(null))
        table = assertInstanceOf(JvmPlv8Table::class.java, result)
        assertEquals(1, table.rows.size)
        assertTrue(XYZ_EXEC_CREATED == table.rows[0][RET_OP]) { table.rows[0][RET_ERR_MSG] }

        session.sql.execute("commit")
    }

    private fun Long.toSeconds(): Double = this.toDouble() / 1_000_000.0
}