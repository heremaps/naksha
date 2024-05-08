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

        val feature = Base.assign(o, NakFeature.klass)
        feature.setId("test")
        assertEquals("test", o["id"])
    }

    @Test
    fun testArray() {
        // BaseList.of(Klass.stringKlass)
        val array = BaseList<String>()
        array.componentKlass = Klass.stringKlass
        array[0] = "hello"
        for (x in KtIterator<Int, String>(Base.arrayIterator(array.data()) as PIterator<Int,String>)) {
            assertEquals(0, x.key)
            assertEquals("hello", x.value)
        }
    }

}