import com.here.naksha.lib.nak.symbol
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import com.here.naksha.lib.nak.*
import com.here.naksha.lib.nak.Nak.Companion.assign
import com.here.naksha.lib.nak.Nak.Companion.initNak
import com.here.naksha.lib.nak.Nak.Companion.newArray
import com.here.naksha.lib.nak.Nak.Companion.newByteArray
import com.here.naksha.lib.nak.Nak.Companion.newDataView
import com.here.naksha.lib.nak.Nak.Companion.newObject
import com.here.naksha.lib.nak.Nak.Companion.undefined
import kotlin.test.assertEquals
import kotlin.test.assertSame

class JvmBasicTest {

    companion object {
        @BeforeAll
        @JvmStatic
        fun init() {
            initNak()
        }
    }

    @Test
    fun testObject() {
        val TEST = symbol("test")
        val o = newObject()
        o["test"] = 5
        o[TEST] = 6
        assertEquals(5, o["test"])
        assertEquals(6, o[TEST])
        for (entry in o) {
            assertEquals("test", entry.key)
            assertEquals(5, entry.value)
        }

        val TEST2 = newObject("hello", "world", "beta", 10)
        assertEquals(2, Nak.size(TEST2))
        assertEquals("world", TEST2["hello"])
        assertEquals(10, TEST2["beta"])
    }

    @Test
    fun testArray() {
        val TEST = symbol("test")
        val a = newArray()
        a[TEST] = 6
        assertEquals(6, a[TEST])
        a[0] = 2
        for (entry in a) {
            assertEquals(0, entry.key)
            assertEquals(2, entry.value)
        }
    }

    @Test
    fun testXyzNs() {
        val o = newObject()
        assertEquals(undefined, o[XyzNs.klass.symbol()])
        val xyz = assign(o, XyzNs.klass)
        assertEquals("CREATE", xyz.getAction())
        assertEquals("CREATE", xyz.setAction("UPDATE"))
        assertEquals("UPDATE", xyz.getAction())
        assertSame(xyz, o[XyzNs.klass.symbol()])
    }

    @Test
    fun testByteArray() {
        val bytes = newByteArray(100)
        assertEquals(100, bytes.size)
        val view = newDataView(bytes)
    }
}