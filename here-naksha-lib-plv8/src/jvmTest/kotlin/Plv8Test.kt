import com.here.naksha.lib.jbon.*
import com.here.naksha.lib.plv8.*
import com.here.naksha.lib.plv8.TG_OP_INSERT
import com.here.naksha.lib.plv8.TG_WHEN_BEFORE
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class Plv8Test : Plv8TestContainer() {
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
    fun testGrid() {
        val session = NakshaSession.get()
        val grid = Static.grid(session.sql, "foo", GEO_TYPE_NULL, null)
        assertNotNull(grid)
        assertEquals(14, grid.length)
        assertEquals("6rcpmez33pmdte", grid)
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
            assertTrue(pnum in 0..255)
            val pid = Static.partitionNameForId(s)
            assertEquals(3, pid.length)
            val expectedId = if (pnum < 10) "00$pnum" else if (pnum < 100) "0$pnum" else "$pnum"
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
        Static.collectionDrop(session.sql, "foo")
        Static.collectionDrop(session.sql, "bar")
        session.sql.execute("COMMIT")
    }

    @Order(9)
    @Test
    fun testInternalCollectionCreationOfFoo() {
        val session = NakshaSession.get()
        Static.collectionCreate(session.sql,session.schema, session.schemaOid, "foo", spGist = false, partition = false)
        session.prefetchUids("foo", 1, 10)
        assertEquals(BigInt64(1), session.newUid("foo"))
        assertEquals(BigInt64(2), session.newUid("foo"))
        assertEquals(BigInt64(3), session.newUid("foo"))
        assertEquals(BigInt64(4), session.newUid("foo"))
        assertEquals(BigInt64(5), session.newUid("foo"))
        assertEquals(BigInt64(6), session.newUid("foo"))
        assertEquals(BigInt64(7), session.newUid("foo"))
        assertEquals(BigInt64(8), session.newUid("foo"))
        // 9 and 10 are next UIDs!
        val pgNew = Jb.map.newMap()
        pgNew[COL_UID] = null // Should be set by trigger
        pgNew[COL_ID] = "foo"
        pgNew[COL_TXN_NEXT] =null // Should be set by trigger
        pgNew[COL_FEATURE] = null
        pgNew[COL_GEO_TYPE] = GEO_TYPE_EWKB
        pgNew[COL_GEOMETRY] = "01010000A0E6100000000000000000144000000000000018400000000000000040".decodeHex()
        pgNew[COL_TAGS] = null
        pgNew[COL_XYZ] = null // Should be set by trigger
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
        assertEquals(BigInt64(9), pgNew[COL_UID])
        assertEquals(BigInt64(0), pgNew[COL_TXN_NEXT])
        assertNotNull(pgNew[COL_XYZ])
        // Try the XYZ insert directly
        val uid = session.newUid("foo")
        assertEquals(BigInt64(10), uid)
        val xyzBytes = session.xyzInsert("foo", "bar", uid, GEO_TYPE_NULL, null)
        val xyzNs = XyzNs().mapBytes(xyzBytes)
        val txn = session.txn()
        assertEquals(txn, xyzNs.txn())
        val expectedGrid = Static.grid(session.sql, "bar", GEO_TYPE_NULL, null)
        assertEquals(expectedGrid, xyzNs.grid())
        val uuid = xyzNs.uuid()
        assertEquals("${session.storageId}:foo:${txn.year}:${txn.month}:${txn.day}:10", uuid)
        session.sql.execute("COMMIT")
    }

    @Order(10)
    @Test
    fun testWriteCollectionsOfBar() {
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
        val opBytes = builder.buildXyzOp(XYZ_OP_CREATE, "bar", null)
        val table = session.writeCollections(arrayOf(opBytes), arrayOf(collectionBytes), arrayOf(GEO_TYPE_EWKB), arrayOf(geoBytes), arrayOf(tagsBytes))
        val result = assertInstanceOf(JvmPlv8Table::class.java, table)
        assertEquals(1, result.rows.size)
        val row = result.rows[0]
        assertEquals(XYZ_EXECUTED_CREATED, row["op"])
        session.sql.execute("COMMIT")
    }
}

fun String.decodeHex(): ByteArray {
    check(length % 2 == 0) { "Must have an even length" }
    val bytes = chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    return bytes
}
