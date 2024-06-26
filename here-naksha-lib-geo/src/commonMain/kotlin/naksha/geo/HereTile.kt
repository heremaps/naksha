@file:OptIn(ExperimentalJsExport::class)

package naksha.geo

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.jvm.JvmOverloads
import kotlin.math.abs
import kotlin.math.min

/**
 * A partial, 32-bit HERE Tile implementation supporting level 0 up to (including) level 15.
 *
 * For more information, see the [HEREtile Tiling Scheme](https://www.here.com/docs/bundle/introduction-to-mapping-concepts-user-guide/page/topics/here-tiling-scheme.html).
 *
 * @property intKey The 4-byte integer encoding the HERE Tile ID. The left most *set* bit encodes the level. The rest of the bits (to the right) encode the quad key. The most significant bit is always 0.
 * @constructor Create a HERE Tile from its integer form.
 */
@JsExport
data class HereTile(val intKey: Int) {

    init {
        assertIntKey(intKey)
    }

    /**
     * Create a HERE Tile from a quad key.
     *
     * @param quadKey The quad key.
     */
    @JsName("HereTileFromQuadKey")
    constructor(quadKey: String) : this(convertQuadKeyToIntKey(quadKey))

    /**
     * Create a HERE Tile from a latitude, longitude, and level.
     *
     * @param latitude The latitude.
     * @param longitude The longitude.
     * @param level The quad level, must be between 0 and 15.
     */
    @JsName("HereTileFromLatLng")
    @JvmOverloads
    constructor(latitude: Double, longitude: Double, level: Int = 15) : this(convertLatLngToIntKey(latitude, longitude, level))

    companion object {
        private fun assertIntKey(intKey: Int) {
            if (intKey < 1 || intKey > 1610612735) throw IllegalArgumentException("not a valid HERE Tile intKey: $intKey")

            if ((31 - intKey.countLeadingZeroBits()) % 2 == 1) throw IllegalArgumentException("does not a valid HERE Tile level indicator: $intKey")
        }

        private fun convertQuadKeyToIntKey(quadKey: String): Int {
            return ("1$quadKey").toInt(4)
        }

        private fun convertLatLngToIntKey(latitude: Double, longitude: Double, level: Int): Int {
            assertLevel(level)
            if (longitude < -180.0 || longitude > 180.0) throw IllegalArgumentException("longitude should be between -180.0 and 180.0: $longitude")
            if (latitude < -90.0 || latitude > 90.0) throw IllegalArgumentException("latitude should be between -90.0 and 90.0: $latitude")

            var x = 0
            if (abs(longitude) != 180.0) {
                val angularWidth: Double = getQuadAngularWidth(level)
                val column = (longitude + 180) / angularWidth

                // In rare occasions, precision issues can cause off-by-one errors, when `column` is rounded to an integer.
                // To prevent this we verify that the coordinate is not outside the quad boundaries.
                x = if (column % 1 == 0.0 && longitude < angularWidth * column - 180.0) {
                    column.toInt() - 1
                } else {
                    column.toInt()
                }
                x = min(getXMax(level), x)
            }

            val angularHeight: Double = getQuadAngularHeight(level)
            val row = (latitude + 90) / angularHeight
            // In rare occasions, precision issues can cause off-by-one errors, when `row` is rounded to an integer.
            // To prevent this we verify that the coordinate is not outside the quad boundaries.
            var y = if (row % 1 == 0.0 && latitude < angularHeight * row - 90.0) {
                row.toInt() - 1
            } else {
                row.toInt()
            }
            y = min(getYMax(level).toDouble(), y.toDouble()).toInt()

            return convertXYLevelToIntKey(x.toShort(), y.toShort(), level)
        }

        private fun assertLevel(level: Int) {
            if (level < 0 || level > 15) throw IllegalArgumentException("level should be between 0 and 15: $level")
        }

        private fun getQuadAngularWidth(zoomLevel: Int): Double {
            return 360.0 / (1 shl zoomLevel)
        }

        private fun getXMax(zoomLevel: Int): Int {
            return ((1L shl zoomLevel) - 1).toInt()
        }

        private fun getQuadAngularHeight(level: Int): Double {
            if (level == 0) {
                return 180.0
            }
            return 360.0 / (1L shl level)
        }

        private fun getYMax(zoomLevel: Int): Int {
            return (((1L shl zoomLevel) - 1) / 2).toInt()
        }

        private fun convertXYLevelToIntKey(x: Short, y: Short, level: Int): Int {
            val intKey: Int = convertXYToIntKey(x, y)
            return intKey or (1 shl (level * 2))
        }

        private fun convertXYToIntKey(x: Short, y: Short): Int {
            val xInt: Int = interleaveToEvenBits(x.toInt())
            val yInt: Int = interleaveToEvenBits(y.toInt())
            return xInt or (yInt shl 1)
        }

        private fun interleaveToEvenBits(i: Int): Int {
            var x = i
            x = (x or (x shl 8)) and 0x00FF00FF
            x = (x or (x shl 4)) and 0x0F0F0F0F
            x = (x or (x shl 2)) and 0x33333333
            x = (x or (x shl 1)) and 0x55555555
            return x
        }
    }

    /**
     * Get the quad level of this HERE Tile.
     *
     * @return The quad level.
     */
    fun level(): Int {
        return (31 - intKey.countLeadingZeroBits()) / 2
    }

    /**
     * Get the quad key for this tile.
     *
     * @return The quad key.
     */
    fun quadKey(): String {
        val level = level()
        val quadKey = StringBuilder()
        for (i in level - 1 downTo 0) {
            val digit = (intKey shr i * 2) and 3
            quadKey.append(digit)
        }
        return quadKey.toString()
    }

    /**
     * Get the HERE Tile whose [intKey] is a lower bound on all level 15 descendants of this HERE Tile's [intKey].
     *
     * For example, take the HERE Tile with [intKey]=91 ([quadKey]="123"). Its level 15 lower bound is
     * [intKey]=1526726656 ([quadKey]="123000000000000"), and 1526726656 is ≤ the [intKey] of all of its level 15
     * descendants.
     *
     * @return The [HereTile] that is the lower bound.
     */
    fun maxLevelLowerBound(): HereTile {
        val level = level()
        return HereTile(intKey shl (30 - (2 * level)))
    }

    /**
     * Get the HERE Tile whose [intKey] is an upper bound on all level 15 descendants of this HERE Tile's [intKey].
     *
     * For example, take the HERE Tile with [intKey]=91 ([quadKey]="123"). Its level 15 lower bound is
     * [intKey]=1543503871 ([quadKey]="123333333333333"), and 1543503871 is ≥ the [intKey] of all of its level 15
     * descendants.
     *
     * @return The [HereTile] that is the upper bound.
     */
    fun maxLevelUpperBound(): HereTile {
        val level = level()
        return HereTile((intKey shl (30 - (2 * level))) or min(0x1fffffff, (0xffffffff shr (2 + (2 * level)))).toInt())
    }

    /**
     * Truncate a HERE Tile ID to the given level.
     *
     * For example, take the HERE Tile with [intKey]=1610612735 ([quadKey]="133333333333333"). Truncated to [level]=6,
     * it is [intKey]=6143 ([quadKey]="133333").
     *
     * @param level The quad level, must be between 0 and 15.
     * @return The truncated [HereTile].
     */
    fun hereTileTruncate(level: Int): HereTile {
        assertLevel(level)
        return HereTile(intKey shr (2 * (level() - level)))
    }
}