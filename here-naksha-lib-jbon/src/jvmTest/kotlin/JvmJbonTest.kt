import com.here.naksha.lib.jbon.JbonBuilder
import com.here.naksha.lib.jbon.TYPE_INT4
import com.here.naksha.lib.jbon.TYPE_SIZE32
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull

class JvmJbonTest {
    @Test
    fun basicTest() {
        val view = JvmPlatform.dataViewOf(ByteArray(256))
        assertNotNull(view)
        view.setInt32(0, 12345678)
        assertEquals(12345678, view.getInt32(0))
        assertNotEquals(12345678, view.getInt32(0, true))
    }

    @Test
    fun pureTest() {
        val expected : Byte = -1
        val intValue : Int = 255
        assertEquals(expected, (intValue and 0xff).toByte())
    }

    @Test
    fun testIntEncoding() {
        val view = JvmPlatform.dataViewOf(ByteArray(256))
        val builder = JbonBuilder(view)
        // the values -8 to 7 should be encoded in one byte
        builder.writeInt(-8);
        assertEquals(builder.clear(), 1)
        builder.writeInt(7);
        assertEquals(builder.clear(), 1)
        // the values below -8 and above 7 should be encoded in two byte
        builder.writeInt(-9);
        assertEquals(-9, view.getInt8(1))
        assertEquals(builder.clear(), 2)
        builder.writeInt(8);
        assertEquals(8, view.getInt8(1))
        assertEquals(builder.clear(), 2)
        // a value less than -128 must be stored in three byte
        builder.writeInt(-129)
        assertEquals(-129, view.getInt16(1))
        assertEquals(builder.clear(), 3)
        // a value bigger than 127 must be stored in three byte
        builder.writeInt(128)
        assertEquals(128, view.getInt16(1))
        assertEquals(builder.clear(), 3)
        // a value less than -32768 must be stored in five byte
        builder.writeInt(-32769)
        assertEquals(-32769, view.getInt32(1))
        assertEquals(builder.clear(), 5)
        // a value bigger than 32767 must be stored in three byte
        builder.writeInt(32768)
        assertEquals(32768, view.getInt32(1))
        assertEquals(builder.clear(), 5)
    }

    @Test
    fun testSize32Encoding() {
        val view = JvmPlatform.dataViewOf(ByteArray(256))
        val builder = JbonBuilder(view)
        // a value between 0 and (including) 10 must result in one byte
        builder.writeSize32(0);
        assertEquals(TYPE_SIZE32, view.getInt8(0).toInt())
        assertEquals(builder.clear(), 1)
        builder.writeSize32(11);
        assertEquals(11, view.getInt8(0).toInt() and 0xf)
        assertEquals(builder.clear(), 1)
        // a value between 12 and 267 must be encoded in two byte
        builder.writeSize32(12);
        assertEquals(TYPE_SIZE32 xor 12, view.getInt8(0).toInt())
        assertEquals(0, view.getInt8(1))
        assertEquals(builder.clear(), 2)
        builder.writeSize32(267);
        assertEquals(TYPE_SIZE32 xor 12, view.getInt8(0).toInt())
        assertEquals((267-12).toByte(), view.getInt8(1))
        assertEquals(builder.clear(), 2)
        // a value between 268 and 65535 must be encoded in three byte
        builder.writeSize32(268);
        assertEquals(TYPE_SIZE32 xor 13, view.getInt8(0).toInt())
        assertEquals(268, view.getInt16(1))
        assertEquals(builder.clear(), 3)
        builder.writeSize32(65535);
        assertEquals(TYPE_SIZE32 xor 13, view.getInt8(0).toInt())
        assertEquals(65535.toShort(), view.getInt16(1))
        assertEquals(builder.clear(), 3)
        // a value above 65535 must be encoded in five byte
        builder.writeSize32(65536);
        assertEquals(TYPE_SIZE32 xor 15, view.getInt8(0).toInt())
        assertEquals(65536, view.getInt32(1))
        assertEquals(builder.clear(), 5)
        builder.writeSize32(Integer.MAX_VALUE);
        assertEquals(TYPE_SIZE32 xor 15, view.getInt8(0).toInt())
        assertEquals(Integer.MAX_VALUE, view.getInt32(1))
        assertEquals(builder.clear(), 5)
    }
}