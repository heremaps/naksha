package naksha.jbon//import com.here.naksha.lib.jbon.*
//import org.junit.jupiter.api.Assertions.*
//import org.junit.jupiter.api.Order
//import org.junit.jupiter.api.Test
//
//class XyzTest : JbAbstractTest() {
//
//    private fun createTags() : ByteArray {
//        val view = env.newDataView(ByteArray(1024))
//        val builder = XyzBuilder(view)
//        builder.startTags()
//        builder.writeTag("restaurant")
//        builder.writeTag("isNoBool=true")
//        builder.writeTag("isOpen:=true")
//        builder.writeTag("foo=12")
//        builder.writeTag("bar:=14")
//        builder.writeTag("x:=1.56")
//        builder.writeTag("y:=-1.99")
//        return builder.buildTags()
//    }
//
//    @Order(1)
//    @Test
//    fun testXyzTags() {
//        val tagBytes = createTags()
//        val tagReader = XyzTags(dictManager).mapBytes(tagBytes)
//        val tags = tagReader.tagsMap()
//        assertEquals(7, tags.size())
//        assertTrue(tags.containsKey("restaurant"))
//        assertNull(tags["restaurant"])
//        assertTrue(tags.containsKey("isNoBool"))
//        assertEquals("true", tags["isNoBool"])
//        assertTrue(tags.containsKey("isOpen"))
//        assertEquals(true, tags["isOpen"])
//        assertTrue(tags.containsKey("foo"))
//        assertEquals("12", tags["foo"])
//        assertTrue(tags.containsKey("bar"))
//        assertEquals(14.0, tags["bar"])
//        assertTrue(tags.containsKey("x"))
//        assertEquals(1.56, tags["x"])
//        assertTrue(tags.containsKey("y"))
//        assertEquals(-1.99, tags["y"])
//
//        val array = tagReader.tagsArray()
//        assertEquals(7, tags.size())
//        assertEquals("restaurant", array[0])
//        assertEquals("isNoBool=true", array[1])
//        assertEquals("isOpen:=true", array[2])
//        assertEquals("foo=12", array[3])
//        assertEquals("bar:=14", array[4])
//        assertEquals("x:=1.56", array[5])
//        assertEquals("y:=-1.99", array[6])
//    }
//
//    @Order(2)
//    @Test
//    fun testXyzOp() {
//        val view = env.newDataView(ByteArray(1024))
//        val builder = XyzBuilder(view)
//        val xyzOp = builder.buildXyzOp(XYZ_OP_CREATE, "foo", "uuid", null)
//        val reader = XyzOp().mapBytes(xyzOp)
//        assertEquals(XYZ_OP_CREATE, reader.op())
//        assertEquals("foo", reader.id())
//        assertEquals("uuid", reader.uuid())
//    }
//
//    @Order(3)
//    @Test
//    fun testXyzNs() {
//        val view = env.newDataView(ByteArray(1024))
//        val builder = XyzBuilder(view)
//        val createdTs = Jb.env.currentMillis()
//        val txn = createdTs + 10
//        val xyz = builder.buildXyzNs(
//                createdTs, createdTs, txn,
//                ACTION_CREATE.toShort(), 1, createdTs + 20,
//                null, "test-uuid",
//                "test-app", "test-author",
//                1)
//        val featureView = JbSession.get().newDataView(xyz)
//        val reader = XyzNs()
//        reader.mapView(featureView)
//        assertEquals(createdTs, reader.createdAt())
//        assertEquals(createdTs, reader.updatedAt())
//        assertEquals(txn, reader.txn().value)
//        assertEquals(ACTION_CREATE, reader.action())
//        assertEquals(1, reader.version())
//        assertEquals(createdTs + 20, reader.authorTs())
//
//        assertNull(reader.puuid())
//        assertEquals("test-uuid", reader.uuid())
//        assertEquals("test-app", reader.appId())
//        assertEquals("test-author", reader.author())
//        assertEquals(1, reader.grid()) // FIXME
//
//        // Convert to namespace.
//        val tagBytes = createTags()
//        val tagReader = XyzTags(dictManager).mapBytes(tagBytes)
//        val ns = reader.toIMap("test_storage", tagReader.tagsArray())
//        assertEquals(11, ns.size())
//        assertEquals(createdTs.toDouble(), ns["createdAt"])
//        assertEquals(createdTs.toDouble(), ns["updatedAt"])
//        assertFalse(ns.containsKey("puuid"))
//        assertEquals("test-uuid", ns["uuid"])
//        assertEquals("test-app", ns["app_id"])
//        assertEquals("test-author", ns["author"])
//        assertEquals(1, ns["grid"]) // FIXME
//        assertEquals("test_storage:txn:0:0:0:$txn:0", ns["txn"])
//    }
//
//    @Test
//    fun testXyzNsOverflow() {
//        val uuid = "11222222222222222222"
//        val view = env.newDataView(ByteArray(700))
//        val builder = XyzBuilder(view)
//
//        val nsBytes = builder.buildXyzNs(
//                BigInt64(1709296198085L),
//                BigInt64(1709296198085L),
//                BigInt64(4558069433410519040L),
//                3,
//                1,
//                BigInt64(1709296198085L),
//                uuid,
//                uuid,
//                "zcxvzxcvzcvzxc",
//                "11222222222222222211",
//                1
//        )
//
//        val xyzNs = XyzNs().mapBytes(nsBytes, 0, nsBytes.size)
//
//        assertEquals(3, xyzNs.action())
//        assertEquals(uuid, xyzNs.uuid())
//        assertEquals(uuid, xyzNs.puuid())
//    }
//
//}