package naksha.base

import kotlin.test.Test
import kotlin.test.assertEquals

class MutableTest {
    @Test
    fun addInt342() {
        val i32 = MutableInt(10)
        i32.value += 5
        assertEquals(15, i32.value)
        assertEquals(15, i32.jsonValue)
        assertEquals("15", i32.toString())
    }

    @Test
    fun addFloat64() {
        val f64 = MutableDouble(10.0)
        f64.value += 5.0
        assertEquals(15.0, f64.value)
        assertEquals(15.0, f64.jsonValue)
        assertEquals("15.0", f64.toString())
    }

    @Test
    fun subInt32() {
        val i32 = MutableInt(10)
        i32.value -= 5
        assertEquals(5, i32.value)
        assertEquals(5, i32.jsonValue)
        assertEquals("5", i32.toString())
    }

    @Test
    fun subFloat64() {
        val f64 = MutableDouble(10.0)
        f64.value -= 5.0
        assertEquals(5.0, f64.value)
        assertEquals(5.0, f64.jsonValue)
        assertEquals("5.0", f64.toString())
    }

    @Test
    fun mulInt32() {
        val i32 = MutableInt(10)
        i32.value *= 5
        assertEquals(50, i32.value)
        assertEquals(50, i32.jsonValue)
        assertEquals("50", i32.toString())
    }

    @Test
    fun mulFloat64() {
        val f64 = MutableDouble(10.0)
        f64.value *= 5.0
        assertEquals(50.0, f64.value)
        assertEquals(50.0, f64.jsonValue)
        assertEquals("50.0", f64.toString())
    }

    @Test
    fun divInt32() {
        val i32 = MutableInt(10)
        i32.value /= 5
        assertEquals(2, i32.value)
        assertEquals(2, i32.jsonValue)
        assertEquals("2", i32.toString())
    }

    @Test
    fun divFloat64() {
        val f64 = MutableDouble(10.0)
        f64.value /= 5.0
        assertEquals(2.0, f64.value)
        assertEquals(2.0, f64.jsonValue)
        assertEquals("2.0", f64.toString())
    }

    @Test
    fun modInt32() {
        val i32 = MutableInt(10)
        i32.value %= 5
        assertEquals(0, i32.value)
        assertEquals(0, i32.jsonValue)
        assertEquals("0", i32.toString())
    }

    @Test
    fun modFloat64() {
        val f64 = MutableDouble(10.0)
        f64.value %= 5.0
        assertEquals(0.0, f64.value)
        assertEquals(0.0, f64.jsonValue)
        assertEquals("0.0", f64.toString())
    }
}