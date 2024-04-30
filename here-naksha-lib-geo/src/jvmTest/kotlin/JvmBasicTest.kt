import com.here.naksha.lib.base.symbol
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import com.here.naksha.lib.base.*
import com.here.naksha.lib.base.Base.Companion.initNak
import com.here.naksha.lib.base.Base.Companion.newObject
import kotlin.test.assertEquals

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
        assertEquals(2, Base.size(TEST2))
        assertEquals("world", TEST2["hello"])
        assertEquals(10, TEST2["beta"])
    }

}