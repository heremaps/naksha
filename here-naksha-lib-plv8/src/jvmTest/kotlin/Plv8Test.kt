import com.here.naksha.lib.jbon.BigInt64
import com.here.naksha.lib.jbon.Jb
import com.here.naksha.lib.jbon.JvmMap
import com.here.naksha.lib.jbon.SQL_STRING
import com.here.naksha.lib.jbon.XYZ_EXEC_CREATED
import com.here.naksha.lib.jbon.XYZ_OP_CREATE
import com.here.naksha.lib.jbon.XyzBuilder
import com.here.naksha.lib.jbon.XyzVersion
import com.here.naksha.lib.jbon.asMap
import com.here.naksha.lib.jbon.get
import com.here.naksha.lib.jbon.newMap
import com.here.naksha.lib.jbon.put
import com.here.naksha.lib.jbon.set
import com.here.naksha.lib.jbon.shl
import com.here.naksha.lib.jbon.toInt
import com.here.naksha.lib.plv8.COL_FEATURE
import com.here.naksha.lib.plv8.COL_GEOMETRY
import com.here.naksha.lib.plv8.COL_FLAGS
import com.here.naksha.lib.plv8.COL_ID
import com.here.naksha.lib.plv8.COL_TAGS
import com.here.naksha.lib.plv8.COL_TXN
import com.here.naksha.lib.plv8.COL_TXN_NEXT
import com.here.naksha.lib.plv8.COL_UID
import com.here.naksha.lib.plv8.GEO_TYPE_EWKB
import com.here.naksha.lib.plv8.GEO_TYPE_NULL
import com.here.naksha.lib.plv8.JvmPlv8Table
import com.here.naksha.lib.plv8.NKC_DISABLE_HISTORY
import com.here.naksha.lib.plv8.NKC_TABLE_ESC
import com.here.naksha.lib.plv8.NakshaCollection
import com.here.naksha.lib.plv8.NakshaSession
import com.here.naksha.lib.plv8.PgTrigger
import com.here.naksha.lib.plv8.Static
import com.here.naksha.lib.plv8.Static.PARTITION_COUNT
import com.here.naksha.lib.plv8.TG_LEVEL_ROW
import com.here.naksha.lib.plv8.TG_OP_INSERT
import com.here.naksha.lib.plv8.TG_OP_UPDATE
import com.here.naksha.lib.plv8.TG_WHEN_BEFORE
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.nio.charset.StandardCharsets
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

@ExtendWith(Plv8TestContainer::class)
class Plv8Test : JbTest() {
    private val topologyJson = Plv8PerfTest::class.java.getResource("/topology.json")!!.readText(StandardCharsets.UTF_8)

    @Order(1)
    @Test
    fun selectJbonModule() {
        val session = NakshaSession.get()
        val plan = session.sql.prepare("SELECT * FROM commonjs2_modules WHERE module = $1", arrayOf(SQL_STRING))
        try {
            val cursor = plan.cursor(arrayOf("naksha"))
            try {
                var row = cursor.fetch()
                Assertions.assertNotNull(row)
                while (row != null) {
                    check(row is HashMap<*, *>)
                    Jb.log.info("row: ", row)
                    row = cursor.fetch()
                }
            } finally {
                cursor.close()
            }
        } finally {
            plan.free()
        }
    }

    @Order(2)
    @Test
    fun queryVersion() {
        val session = NakshaSession.get()
        val result = session.sql.execute("select naksha_version() as version")
        assertNull(session.sql.affectedRows(result))
        val rows = assertIs<Array<JvmMap>>(session.sql.rows(result))
        for (row in rows) {
            assertEquals(1, row.size)
            assertEquals(BigInt64(0L), row["version"])
            assertEquals(1, Jb.map.size(row))
            assertEquals(BigInt64(0L), Jb.map.get(row, "version"))
        }
    }

    @Order(3)
    @Test
    fun testHereTile() {
        assertEquals("", Static.calculateHereTileId(0.0, 0.0, 0))
        assertEquals("12", Static.calculateHereTileId(0.0, 0.0, 2))
        assertEquals("13", Static.calculateHereTileId(45.0, 90.0, 2))
        assertEquals("000", Static.calculateHereTileId(-90.0, -180.0, 3))
        assertEquals("100", Static.calculateHereTileId(-90.0, 0.0, 3))
        assertEquals("000", Static.calculateHereTileId(-90.0, 180.0, 3))
        assertEquals("022222222222", Static.calculateHereTileId(90.0, -180.0, 12))
        assertEquals("122", Static.calculateHereTileId(90.0, 0.0, 3))
        assertEquals("022222222222", Static.calculateHereTileId(90.0, 180.0, 12))
        assertEquals("02", Static.calculateHereTileId(0.0, -180.0, 2))
        assertEquals("02", Static.calculateHereTileId(0.0, 180.0, 2))
        assertEquals("132", Static.calculateHereTileId(45.0, 90.0, 3))
    }

    @Order(3)
    @Test
    fun testGrid() {
        val session = NakshaSession.get()
        val grid = session.grid("foo", GEO_TYPE_NULL, null)
        assertNotNull(grid)
        // FIXME
        assertEquals(0, grid)
    }

    @Suppress("LocalVariableName")
    @Order(4)
    @Test
    fun testVersion() {
        val v1_0_0 = XyzVersion(1, 0, 0)
        assertEquals(XyzVersion(1, 0, 0), XyzVersion.fromString("1.0.0"))
        assertEquals(XyzVersion(1, 0, 0), XyzVersion.fromString("1.0"))
        assertEquals(XyzVersion(1, 0, 0), XyzVersion.fromString("1"))
        assertEquals(v1_0_0, XyzVersion.fromBigInt(BigInt64(1) shl 32))
        assertEquals(v1_0_0.toBigInt(), BigInt64(1) shl 32)
        val v1_2_3 = XyzVersion(1, 2, 3)
        assertEquals("1.2.3", v1_2_3.toString())
        assertEquals(v1_2_3, XyzVersion.fromString("1.2.3"))
        assertTrue(v1_0_0 < v1_2_3)
    }

    @Order(5)
    @Test
    fun testDbVersion() {
        val session = NakshaSession.get()
        val version = session.postgresVersion()
        assertTrue(version >= XyzVersion(14, 0, 0))
    }

    @Order(6)
    @Test
    fun testPartitionNumbering() {
        var i = 0
        while (i++ < 10_000) {
            val s = env.randomString(12)
            val pnum = Static.partitionNumber(s)
            assertTrue(pnum in 0..<PARTITION_COUNT)
            val pid = Static.partitionNameForId(s)

            @Suppress("KotlinConstantConditions")
            val expectedId: String = when (PARTITION_COUNT) {
                in 0..9 -> {
                    assertEquals(1, pid.length)
                    "$pnum"
                }

                in 10..99 -> {
                    assertEquals(2, pid.length)
                    if (pnum < 10) "0$pnum" else "$pnum"
                }

                in 100..255 -> {
                    assertEquals(3, pid.length)
                    if (pnum < 10) "00$pnum" else if (pnum < 100) "0$pnum" else "$pnum"
                }

                else -> throw AssertionError("Partition count should be between 0 and 255")
            }
            assertEquals(expectedId, pid)
        }
    }

    @Order(7)
    @Test
    fun testTransactionNumber() {
        val session = NakshaSession.get()
        assertNotNull(session.txn())
    }

    @Order(8)
    @Test
    fun dropTestCollectionsIfExists() {
        val session = NakshaSession.get()
        session.sql.execute("DELETE FROM $NKC_TABLE_ESC WHERE id = ANY($1)", arrayOf(arrayOf("foo", "bar")))
        Static.collectionDrop(session.sql, "foo")
        Static.collectionDrop(session.sql, "bar")
        session.sql.execute("COMMIT")
    }

    @Order(9)
    @Disabled("as long as triggers throw an error")
    @Test
    fun testInternalCollectionCreationOfFoo() {
        val session = NakshaSession.get()
        Static.collectionCreate(session.sql, Static.SC_DEFAULT, session.schema, session.schemaOid, "foo", geoIndex = Static.GEO_INDEX_DEFAULT, partition = false)
        // 9 and 10 are next UIDs!
        val pgNew = Jb.map.newMap()
        pgNew[COL_UID] = null // Should be set by trigger
        pgNew[COL_ID] = "foo"
        pgNew[COL_TXN_NEXT] = null // Should be set by trigger
        pgNew[COL_FEATURE] = null
        pgNew[COL_FLAGS] = GEO_TYPE_EWKB
        pgNew[COL_GEOMETRY] = "01010000A0E6100000000000000000144000000000000018400000000000000040".decodeHex()
        pgNew[COL_TAGS] = null
        val pgOld = null
        val t = PgTrigger(
                TG_OP_INSERT,
                "naksha_trigger_before",
                TG_WHEN_BEFORE,
                TG_LEVEL_ROW,
                0,
                "foo",
                session.schema,
                pgNew,
                pgOld
        )
        // Simulate a trigger invocation.
        session.triggerBefore(t)
        assertEquals(0, pgNew[COL_UID])
        assertEquals(null, pgNew[COL_TXN_NEXT])
        //assertNotNull(pgNew[COL_XYZ])
        // Try the XYZ insert directly
//        val xyzBytes = session.xyzInsert("foo", "bar", uid, GEO_TYPE_NULL, null)
//        val xyzNs = XyzNs().mapBytes(xyzBytes)
//        val txn = session.txn()
//        assertEquals(txn, xyzNs.txn())
//        val expectedGrid = session.grid("bar", GEO_TYPE_NULL, null)
//        assertEquals(expectedGrid, xyzNs.grid())
//        val uuid = xyzNs.uuid()
//        assertEquals("${session.storageId}:foo:${txn.year()}:${txn.month()}:${txn.day()}:${txn.seq()}:10", uuid)
        session.sql.execute("COMMIT")
    }

    @Order(10)
    @Test
    fun testCreateBarCollection() {
        val session = NakshaSession.get()
        // TODO: We need a test for this case, that is a general issue with empty local dictionaries!
        //val collectionJson = """{"id":"bar"}"""
        val collectionJson = """{"id":"bar","type":"NakshaCollection","minAge":3560,"unlogged":false,"partition":false,"pointsOnly":false,"properties":{},"disableHistory":false,"partitionCount":-1,"estimatedFeatureCount": -1,"estimatedDeletedFeatures":-1}"""
        val collectionMap = asMap(env.parse(collectionJson))
        val builder = XyzBuilder.create()
        builder.clear()
        builder.startTags()
        builder.writeTag("age:=23")
        builder.writeTag("featureType=NakshaCollection")
        val tagsBytes = builder.buildTags()
        val geoBytes = "01010000A0E6100000000000000000144000000000000018400000000000000040".decodeHex()
        val collectionBytes = builder.buildFeatureFromMap(collectionMap)
        val opBytes = builder.buildXyzOp(XYZ_OP_CREATE, "bar", null, 1)
        val table = session.writeCollections(arrayOf(opBytes), arrayOf(collectionBytes), arrayOf(GEO_TYPE_EWKB), arrayOf(geoBytes), arrayOf(tagsBytes))
        val result = assertInstanceOf(JvmPlv8Table::class.java, table)
        assertEquals(1, result.rows.size)
        val row = result.rows[0]
        assertEquals(XYZ_EXEC_CREATED, row["op"])
        session.sql.execute("COMMIT")
    }

    @Order(11)
    @Test
    fun testCreateAndRestoreNakshaCollection() {
        // given (we expect it to ignore invalid values like "temporary")
        val collectionJson = """{
            "id": "bar",
            "type": "NakshaCollection",
            "maxAge":3560,
            "partition":true,
            "storageClass": "temporary",
            "geoIndex":"sp-gist",
            "properties":{},
            "disableHistory": true,
            "partitionCount":32,
            "estimatedFeatureCount": 50,
            "estimatedDeletedFeatures":100,
            "temporary":true,
            "pointsOnly":true
        }"""
        val collectionMap = asMap(env.parse(collectionJson))
        val collectionBytes = XyzBuilder.create().buildFeatureFromMap(collectionMap)

        // when
        val restoredCollection = NakshaCollection(dictManager)
        restoredCollection.mapBytes(collectionBytes)

        // then
        assertTrue(restoredCollection.partition())
        assertTrue(restoredCollection.disableHistory())
        assertEquals(Static.GEO_INDEX_SP_GIST, restoredCollection.geoIndex())
        assertEquals(Static.SC_TEMPORARY, restoredCollection.storageClass())
        assertEquals("bar", restoredCollection.id())
        assertEquals(3560, restoredCollection.maxAge().toInt())
        assertEquals(50, restoredCollection.estimatedFeatureCount().toInt())
    }

    @Order(12)
    @Disabled("as long as triggers throw an error")
    @Test
    fun triggerAfter() {
        val session = NakshaSession.get()
        val topologyCollectionConfig = newMap()
        topologyCollectionConfig.put(NKC_DISABLE_HISTORY, true)
        session.collectionConfiguration.put("foo", topologyCollectionConfig)

        val builderFeature = XyzBuilder.create(65536)
        val topology = asMap(env.parse(topologyJson))

        val pgNew = Jb.map.newMap()
        pgNew[COL_UID] = null // Should be set by trigger
        pgNew[COL_ID] = "F1"
        pgNew[COL_TXN_NEXT] = null // Should be set by trigger
        pgNew[COL_FEATURE] = builderFeature.buildFeatureFromMap(topology)
        pgNew[COL_FLAGS] = GEO_TYPE_EWKB
        pgNew[COL_GEOMETRY] = "01010000A0E6100000000000000000144000000000000018400000000000000040".decodeHex()
        pgNew[COL_TAGS] = null
        val pgOld = pgNew
        val t = PgTrigger(
                TG_OP_UPDATE,
                "naksha_trigger_before",
                TG_WHEN_BEFORE,
                TG_LEVEL_ROW,
                0,
                "foo",
                session.schema,
                pgNew,
                pgOld
        )
        session.triggerBefore(t)
        session.triggerAfter(t)
        session.sql.execute("commit;")

        val oldTxnNext: BigInt64? = pgOld[COL_TXN]
        val newTxn: BigInt64? = pgNew[COL_TXN_NEXT]
        assertEquals(oldTxnNext, newTxn)

    }
//    @Order(11)
//    @Test
//    fun testUpdateBarCollection() {
//        val session = NakshaSession.get()
//        // TODO: We need a test for this case, that is a general issue with empty local dictionaries!
//        //val collectionJson = """{"id":"bar"}"""
//        val collectionJson = """{"id":"bar","type":"NakshaCollection","minAge":3560,"unlogged":false,"partition":false,"pointsOnly":false,"properties":{},"disableHistory":false,"partitionCount":-1,"estimatedFeatureCount": -1,"estimatedDeletedFeatures":-1}"""
//        val collectionMap = asMap(env.parse(collectionJson))
//        val builder = XyzBuilder.create()
//        builder.clear()
//        builder.startTags()
//        builder.writeTag("age:=23")
//        builder.writeTag("featureType=NakshaCollection")
//        val tagsBytes = builder.buildTags()
//        val geoBytes = "01010000A0E6100000000000000000144000000000000018400000000000000040".decodeHex()
//        val collectionBytes = builder.buildFeatureFromMap(collectionMap)
//        val opBytes = builder.buildXyzOp(XYZ_OP_CREATE, "bar", null)
//        val table = session.writeCollections(arrayOf(opBytes), arrayOf(collectionBytes), arrayOf(GEO_TYPE_EWKB), arrayOf(geoBytes), arrayOf(tagsBytes))
//        val result = assertInstanceOf(JvmPlv8Table::class.java, table)
//        assertEquals(1, result.rows.size)
//        val row = result.rows[0]
//        assertEquals(XYZ_EXECUTED_CREATED, row["op"])
//        session.sql.execute("COMMIT")
//
//    }
}

fun String.decodeHex(): ByteArray {
    check(length % 2 == 0) { "Must have an even length" }
    val bytes = chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    return bytes
}
