package naksha.base

import kotlin.test.*

class AtomicSetTest {
    private fun setOfFive_0_1_2_3_4() = AtomicSet(arrayOf(0, 1, 2, 3, 4))

    @Test
    fun addAll() {
        val set = setOfFive_0_1_2_3_4()
        set.addAll(arrayOf(5, 6))
        assertEquals(7, set.size)
        assertContentEquals(arrayOf(0,1,2,3,4,5,6), set.content)
    }

    @Test
    fun removeZero() {
        var set = setOfFive_0_1_2_3_4()
        set -= 0
        assertEquals(4, set.size)
        assertContentEquals(arrayOf(1,2,3,4), set.content)
    }

    @Test
    fun removeOne() {
        var set = setOfFive_0_1_2_3_4()
        set -= 1
        assertEquals(4, set.size)
        assertContentEquals(arrayOf(0,2,3,4), set.content)
    }

    @Test
    fun removeTwo() {
        var set = setOfFive_0_1_2_3_4()
        set -= 2
        assertEquals(4, set.size)
        assertContentEquals(arrayOf(0,1,3,4), set.content)
    }

    @Test
    fun removeThree() {
        var set = setOfFive_0_1_2_3_4()
        set -= 3
        assertEquals(4, set.size)
        assertContentEquals(arrayOf(0,1,2,4), set.content)
    }

    @Test
    fun removeFour() {
        var set = setOfFive_0_1_2_3_4()
        set -= 4
        assertEquals(4, set.size)
        assertContentEquals(arrayOf(0,1,2,3), set.content)
    }

    @Test
    fun addFive() {
        var set = setOfFive_0_1_2_3_4()
        set += 5
        assertEquals(6, set.size)
        assertContentEquals(arrayOf(0,1,2,3,4,5), set.content)
    }

    @Test
    fun addSixThenFive() {
        var set = setOfFive_0_1_2_3_4()
        set += 6
        assertEquals(6, set.size)
        assertContentEquals(arrayOf(0,1,2,3,4,6), set.content)

        set += 5
        assertEquals(7, set.size)
        assertContentEquals(arrayOf(0,1,2,3,4,6,5), set.content)
    }

    @Test
    fun forEach() {
        val set = setOfFive_0_1_2_3_4()
        val result = set.dot(0 ) { v, r -> (v ?: 0) + r }
        assertEquals(0+1+2+3+4, result)
    }

    @Test
    fun shift() {
        val set = setOfFive_0_1_2_3_4()
        assertEquals(0, set.shift())
        assertEquals(1, set.shift())
        assertEquals(2, set.shift())
        assertEquals(3, set.shift())
        assertEquals(4, set.shift())
        assertEquals(null, set.shift())
    }

    @Test
    fun unshift() {
        val set = setOfFive_0_1_2_3_4()
        assertTrue(set.unshift(9))
        assertEquals(6, set.size)
        assertContentEquals(arrayOf(9,0,1,2,3,4), set.content)

        // Unshift again the same number, should do nothing and return false.
        assertFalse(set.unshift(9))
        assertEquals(6, set.size)
        assertContentEquals(arrayOf(9,0,1,2,3,4), set.content)

        // Tying to unshift existing value, should by default fail.
        assertFailsWith<NakshaException> { set.unshift(1) }

        // Allow relocation, should move 0
        assertTrue(set.unshift(0, true))
        assertEquals(6, set.size)
        assertContentEquals(arrayOf(0,9,1,2,3,4), set.content)
    }
}