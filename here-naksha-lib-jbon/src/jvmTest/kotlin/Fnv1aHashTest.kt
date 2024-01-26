import com.here.naksha.lib.jbon.Fnv1a
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class Fnv1aHashTest {
    @Test
    fun testFnv1a() {
        // See: https://md5calc.com/hash/fnv1a32/
        val testString = "test"
        val expectedHash : Int = 0xafd071e5.toInt()
        val fnv1b = Fnv1a()
        var i = 0
        while (i < testString.length) {
            val c = testString[i++]
            fnv1b.int8((c.code and 0xff).toByte())
        }
        assertEquals(expectedHash, fnv1b.hash)
    }
}