import com.here.naksha.lib.jbon.*
import com.here.naksha.lib.plv8.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test

class XyzTest : Plv8TestContainer() {

    private fun createTags() : ByteArray {
        val view = env.newDataView(ByteArray(1024))
        val builder = XyzBuilder(view)
        builder.startTags()
        builder.writeTag("restaurant")
        builder.writeTag("isNoBool=true")
        builder.writeTag("isOpen:=true")
        builder.writeTag("foo=12")
        builder.writeTag("bar:=14")
        builder.writeTag("x:=1.56")
        builder.writeTag("y:=-1.99")
        return builder.buildTags()
    }

    @Order(1)
    @Test
    fun testXyzTags() {
        val tagBytes = createTags()
        val tagReader = XyzTags().mapBytes(tagBytes)
        val tags = tagReader.tags()
        assertEquals(7, tags.size())
        assertTrue(tags.containsKey("restaurant"))
        assertNull(tags["restaurant"])
        assertTrue(tags.containsKey("isNoBool"))
        assertEquals("true", tags["isNoBool"])
        assertTrue(tags.containsKey("isOpen"))
        assertEquals(true, tags["isOpen"])
        assertTrue(tags.containsKey("foo"))
        assertEquals("12", tags["foo"])
        assertTrue(tags.containsKey("bar"))
        assertEquals(14.0, tags["bar"])
        assertTrue(tags.containsKey("x"))
        assertEquals(1.56, tags["x"])
        assertTrue(tags.containsKey("y"))
        assertEquals(-1.99, tags["y"])
    }

    @Order(2)
    @Test
    fun testXyzOp() {
        val view = env.newDataView(ByteArray(1024))
        val builder = XyzBuilder(view)
        val xyzOp = builder.buildXyzOp(XYZ_OP_CREATE, "foo", "uuid", "crid")
        val reader = XyzOp().mapBytes(xyzOp)
        assertEquals(XYZ_OP_CREATE, reader.op())
        assertEquals("foo", reader.id())
        assertEquals("uuid", reader.uuid())
        assertEquals("crid", reader.crid())
    }

    @Order(3)
    @Test
    fun testXyzNs() {
        val view = env.newDataView(ByteArray(1024))
        val builder = XyzBuilder(view)
        val createdTs = Jb.env.currentMillis()
        val txn = createdTs + 10
        val xyz = builder.buildXyzNs(
                createdTs, createdTs, txn,
                ACTION_CREATE, 1, createdTs + 20,
                BigInt64(0), null, "test-uuid",
                "test-app", "test-author",
                null, "1234567")
        val featureView = JbSession.get().newDataView(xyz)
        val reader = XyzNs()
        reader.mapView(featureView)
        assertEquals(createdTs, reader.createdAt())
        assertEquals(createdTs, reader.updatedAt())
        assertEquals(txn, reader.txn().value)
        assertEquals(ACTION_CREATE, reader.action())
        assertEquals(1, reader.version())
        assertEquals(createdTs + 20, reader.authorTs())
        assertEquals(BigInt64(0), reader.extend())

        assertNull(reader.puuid())
        assertEquals("test-uuid", reader.uuid())
        assertEquals("test-app", reader.appId())
        assertEquals("test-author", reader.author())
        assertNull(reader.crid())
        assertEquals("1234567", reader.grid())

        // Convert to namespace.
        val tagBytes = createTags()
        val tagReader = XyzTags().mapBytes(tagBytes)
        val ns = reader.toIMap("test_storage", tagReader.tags())
        assertEquals(12, ns.size())
        assertEquals(createdTs.toDouble(), ns["createdAt"])
        assertEquals(createdTs.toDouble(), ns["updatedAt"])
        assertFalse(ns.containsKey("puuid"))
        assertEquals("test-uuid", ns["uuid"])
        assertEquals("test-app", ns["app_id"])
        assertEquals("test-author", ns["author"])
        assertEquals("1234567", ns["grid"])
        assertEquals("test_storage:txn:0:0:0:$txn", ns["txn"])
    }

    @Order(4)
    @Suppress("LocalVariableName")
    @Test
    fun testVersion() {
        val v1_0_0 = XyzVersion(1,0,0)
        assertEquals(XyzVersion(1,0,0), XyzVersion.fromString("1.0.0"))
        assertEquals(XyzVersion(1,0,0), XyzVersion.fromString("1.0"))
        assertEquals(XyzVersion(1,0,0), XyzVersion.fromString("1"))
        assertEquals(v1_0_0, XyzVersion.fromBigInt(BigInt64(1) shl 32))
        assertEquals(v1_0_0.toBigInt(), BigInt64(1) shl 32)
        val v1_2_3 = XyzVersion(1,2,3)
        assertEquals("1.2.3", v1_2_3.toString())
        assertEquals(v1_2_3, XyzVersion.fromString("1.2.3"))
        assertTrue(v1_0_0 < v1_2_3)
    }

    @Order(5)
    @Test
    fun testDbVersion() {
        val session = NakshaSession.get()
        val version = session.postgresVersion()
        assertTrue(version >= XyzVersion(14,0,0))
    }

    @Order(6)
    @Test
    fun testPartitionNumbering() {
        var i = 0
        while (i++ < 10_000) {
            val s = env.randomString(12)
            val pnum = Naksha.partitionNumber(s)
            assertTrue(pnum in 0..255)
            val pid = Naksha.partitionNameForId(s)
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
    fun dropTestCollectionIfExists() {
        val session = NakshaSession.get()
        NakshaSession.collectionDrop(session.sql, "foo")
        session.sql.execute("COMMIT")
    }

    @Order(9)
    @Test
    fun createTestCollection() {
        val session = NakshaSession.get()
        NakshaSession.collectionCreate(session.sql,"foo", spGist = false, partition = false)
        session.sql.execute("COMMIT")
    }
}