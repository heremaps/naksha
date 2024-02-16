import com.here.naksha.lib.jbon.*
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class JbFnv1aHashTest : JbAbstractTest() {
    @Test
    fun testFnv1a32() {
        // See: https://md5calc.com/hash/fnv1a32/
        val testString = "test"
        val expectedHash : Int = 0xafd071e5.toInt()
        val fnv1a = Fnv1a32()
        var i = 0
        while (i < testString.length) {
            val c = testString[i++]
            fnv1a.int8((c.code and 0xff).toByte())
        }
        assertEquals(expectedHash, fnv1a.hash)
    }

    @Test
    fun testFnv1a64() {
        // See: https://toolkitbay.com/tkb/tool/FNV-1
        val testString = "test"
        val expectedHash = BigInt64(0xf9e6e6ef197c2b25uL.toLong())
        val fnv1a = Fnv1a64()
        var i = 0
        while (i < testString.length) {
            val c = testString[i++]
            fnv1a.int8((c.code and 0xff).toByte())
        }
        assertTrue(expectedHash eq fnv1a.hash)
        assertEquals(expectedHash, fnv1a.hash)
    }
}