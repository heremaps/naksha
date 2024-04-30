import com.here.naksha.lib.nak.HereTile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrowsExactly
import org.junit.jupiter.api.Test

class HereTileTest {
    @Test
    fun testConstructFromInt() {
        val tile = HereTile(1)

        assertEquals(1, tile.intKey)
    }

    @Test
    fun testConstructFromQuad() {
        val tile = HereTile("")

        assertEquals(1, tile.intKey)
    }

    @Test
    fun testInvalid() {
        assertThrowsExactly(IllegalArgumentException::class.java) { HereTile(0) } // below minimum
        assertThrowsExactly(IllegalArgumentException::class.java) { HereTile(Int.MAX_VALUE) } // too large
        assertThrowsExactly(IllegalArgumentException::class.java) { HereTile("10".toInt()) } // level indicator missing or in wrong position
    }

    @Test
    fun testConvertHereTile() {
        /**
         * Test conversion between quad key and integer key. The integer is computed from the given binary string.
         */
        fun assertConvertHereTile(quadKey: String, binary: String) {
            val intFromQuad = HereTile(quadKey)
            val roundTrippedQuadKey = intFromQuad.quadKey()
            assertEquals(quadKey, roundTrippedQuadKey)

            val intFromBinary = binary.toInt(2)
            val quadFromInt = HereTile(intFromBinary).quadKey()
            val roundTrippedBinary = HereTile(quadFromInt).intKey
            assertEquals(intFromBinary, roundTrippedBinary)
        }

        assertConvertHereTile("", "1")
        assertConvertHereTile("1", "101")
        assertConvertHereTile("133333", "1011111111111")
        assertConvertHereTile("113333", "1010111111111")
        assertConvertHereTile("000000000000000", "01000000000000000000000000000000")
        assertConvertHereTile("133333333333333", "01011111111111111111111111111111")
    }

    @Test
    fun testMaxLevelLowerBound() {
        fun assertLowerBound(expected: String, quadKey: String) {
            val tile = HereTile(quadKey)
            val lowerBound = tile.maxLevelLowerBound()
            val expectedLowerBound = HereTile(expected)
            assertEquals(expectedLowerBound, lowerBound)
        }

        assertLowerBound("000000000000000", "")
        assertLowerBound("000000000000000", "0")
        assertLowerBound("123000000000000", "123")
        assertLowerBound("111111111111111", "111111111111111")
        assertLowerBound("133333333333333", "133333333333333")
    }

    @Test
    fun testMaxLevelUpperBound() {
        fun assertUpperBound(expected: String, quadKey: String) {
            val tile = HereTile(quadKey)
            val upperBound = tile.maxLevelUpperBound()
            val expectedUpperBound = HereTile(expected)
            assertEquals(expectedUpperBound, upperBound)
        }

        assertUpperBound("133333333333333", "")
        assertUpperBound("033333333333333", "0")
        assertUpperBound("123333333333333", "123")
        assertUpperBound("111111111111111", "111111111111111")
        assertUpperBound("133333333333333", "133333333333333")
    }

    @Test
    fun testHereTileTruncate() {
        fun assertHereTileTruncate(expected: String, quadKey: String) {
            val tile = HereTile(quadKey)
            val level = expected.length
            val newHereTile = tile.hereTileTruncate(level)
            assertEquals(expected, newHereTile.quadKey())
            val expectedNewHereTile = HereTile(expected)
            assertEquals(expectedNewHereTile, newHereTile)
        }

        assertHereTileTruncate("0", "000000000000000")
        assertHereTileTruncate("1", "133333333333333")
        assertHereTileTruncate("13", "133333333333333")
        assertHereTileTruncate("133333", "133333333333333")
    }

    @Test
    fun testHereTile() {
        assertThrowsExactly(IllegalArgumentException::class.java) {
            HereTile(0.0, 0.0, 16)
        }
        assertThrowsExactly(IllegalArgumentException::class.java) {
            HereTile(-91.0, 0.0, 0)
        }
        assertThrowsExactly(IllegalArgumentException::class.java) {
            HereTile(0.0, 181.0, 0)
        }

        fun assertHereTile(expectedQuadKey: String, latitude: Double, longitude: Double, level: Int) {
            val actualIntKey = HereTile(latitude, longitude, level)
            // prefix with "1" for level indicator and then convert base 4 string to integer
            val expected = HereTile(expectedQuadKey)
            assertEquals(expected, actualIntKey)
        }

        assertHereTile("", 0.0, 0.0, 0)
        assertHereTile("1", 0.0, 0.0, 1)
        assertHereTile("12", 0.0, 0.0, 2)
        assertHereTile("13", 45.0, 90.0, 2)
        assertHereTile("000", -90.0, -180.0, 3)
        assertHereTile("100", -90.0, 0.0, 3)
        assertHereTile("000", -90.0, 180.0, 3)
        assertHereTile("022222222222", 90.0, -180.0, 12)
        assertHereTile("122", 90.0, 0.0, 3)
        assertHereTile("022222222222", 90.0, 180.0, 12)
        assertHereTile("02", 0.0, -180.0, 2)
        assertHereTile("02", 0.0, 180.0, 2)
        assertHereTile("132", 45.0, 90.0, 3)
        assertHereTile("101000000000000", -90.0, 45.0, 15)
        assertHereTile("1010000", -90.0, 45.0, 7)
        assertHereTile("023000000000000", 45.0, -135.0, 15)
        assertHereTile("0230000", 45.0, -135.0, 7)
    }

    @Test
    fun testFilterByPrefix() {
        fun filterByPrefix(quadKey: String, grids: List<HereTile>): List<HereTile> {
            val tile = HereTile(quadKey)
            val lowerBound = tile.maxLevelLowerBound()
            val upperBound = tile.maxLevelUpperBound()
            return grids.filter { it.intKey in (lowerBound.intKey)..(upperBound.intKey) }
        }

        val grids = listOf("021001030313131", "021001030313132", "121001030313132", "121001000000000").map(::HereTile)
        val expected0 = listOf("021001030313131", "021001030313132").map(::HereTile)
        val expected1 = listOf("121001030313132", "121001000000000").map(::HereTile)
        val expected1210010 = listOf("121001030313132", "121001000000000").map(::HereTile)
        val expected021001030313131 = listOf("021001030313131").map(::HereTile)
        assertEquals(grids, filterByPrefix("", grids))
        assertEquals(expected0, filterByPrefix("0", grids))
        assertEquals(expected1, filterByPrefix("1", grids))
        assertEquals(expected1210010, filterByPrefix("1210010", grids))
        assertEquals(expected021001030313131, filterByPrefix("021001030313131", grids))
    }

    @Test
    fun testDistinctPrefix() {
        fun distinctPrefix(level: Int, grids: List<HereTile>): List<HereTile> {
            return grids.map { it.hereTileTruncate(level) }.distinct()
        }

        val grids = listOf("021001030313131", "021001030313132", "121001030313132", "121001000000000").map(::HereTile)
        val expected0 = listOf("").map(::HereTile)
        val expected1 = listOf("0", "1").map(::HereTile)
        val expected2 = listOf("02", "12").map(::HereTile)
        val expected8 = listOf("02100103", "12100103", "12100100").map(::HereTile)
        assertEquals(expected0, distinctPrefix(0, grids))
        assertEquals(expected1, distinctPrefix(1, grids))
        assertEquals(expected2, distinctPrefix(2, grids))
        assertEquals(expected8, distinctPrefix(8, grids))
        assertEquals(grids, distinctPrefix(15, grids))
    }
}