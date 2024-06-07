import com.here.naksha.lib.base.Int64
import kotlin.test.Test
import kotlin.test.assertEquals

class Int64Test {
    @Test
    fun add() {
        val a = Int64(10)
        val b = Int64(5)
        val c = a + b
        assertEquals(15L, c.toLong())
        assertEquals(15, c.toInt())
    }

    @Test
    fun sub() {
        val a = Int64(10)
        val b = Int64(5)
        val c = a - b
        assertEquals(5L, c.toLong())
        assertEquals(5, c.toInt())

    }

    @Test
    fun mul() {
        val a = Int64(10)
        val b = Int64(5)
        val c = a * b
        assertEquals(50L, c.toLong())
        assertEquals(50, c.toInt())
    }

    @Test
    fun div() {
        val a = Int64(10)
        val b = Int64(5)
        val c = a / b
        assertEquals(2L, c.toLong())
        assertEquals(2, c.toInt())
    }

    @Test
    fun mod() {
        val a = Int64(10)
        val b = Int64(5)
        val c = a % b
        assertEquals(0L, c.toLong())
        assertEquals(0, c.toInt())
    }
}