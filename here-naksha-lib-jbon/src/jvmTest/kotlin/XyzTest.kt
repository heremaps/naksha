import com.here.naksha.lib.jbon.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test

class XyzTest : JbAbstractTest() {

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
        val tagReader = XyzTags(dictManager).mapBytes(tagBytes)
        val tags = tagReader.tagsMap()
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

        val array = tagReader.tagsArray()
        assertEquals(7, tags.size())
        assertEquals("restaurant", array[0])
        assertEquals("isNoBool=true", array[1])
        assertEquals("isOpen:=true", array[2])
        assertEquals("foo=12", array[3])
        assertEquals("bar:=14", array[4])
        assertEquals("x:=1.56", array[5])
        assertEquals("y:=-1.99", array[6])
    }

}