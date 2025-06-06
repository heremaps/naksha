// This will be exposed
// - in JavaScript at the namespace: naksha.geo.{name}
// - jn Java at the class naksha.geo.NakshaGeoKt.{name}
package naksha.geo

import naksha.base.Int64
import naksha.base.Platform.PlatformCompanion.forKClass
import naksha.base.illegalArg
import kotlin.jvm.JvmField
import kotlin.math.round

/**
 * The package name `naksha.geo`
 * @since 3.0
 */
const val PACKAGE_NAME = "naksha.geo"

/**
 * The [PlatformType][naksha.base.PlatformType] of the [ICoordinates] interface.
 * @since 3.0
 */
@JvmField
val ICoordinates_TYPE = forKClass(ICoordinates::class).withPackageName(PACKAGE_NAME)

/**
 * The multiplier/divisor used when converting doubles into spatial components _(`10_000_000.0` aka `1e7`)_.
 * @since 3.0
 */
const val SP_COMPONENT_MULTIPLIER_DOUBLE: Double = 10_000_000.0 // 1e7

/**
 * The multiplier/divisor used when converting doubles into a spatial fixed integer _(`10_000_000` aka 1e7)_.
 * @since 3.0
 */
const val SP_COMPONENT_MULTIPLIER_INT: Int = 10_000_000

/**
 * The maximum double value of a spatial component.
 * @since 3.0
 */
const val SP_COMPONENT_MAX_DOUBLE: Double = 1_800_000_000.0

/**
 * The maximum 32-bit integer value of a spatial fixed integer.
 * @since 3.0
 */
const val SP_COMPONENT_MAX_INT: Int = 1_800_000_000

/**
 * The maximum 64-bit integer value of a spatial fixed integer.
 * @since 3.0
 */
@JvmField
val SP_COMPONENT_MAX_INT64: Int64 = Int64(1_800_000_000)

/**
 * The minimum double value of a spatial fixed component.
 * @since 3.0
 */
const val SP_COMPONENT_MIN_DOUBLE: Double = -1_800_000_000.0

/**
 * The minimum 32-bit integer value of a spatial fixed integer.
 * @since 3.0
 */
const val SP_COMPONENT_MIN_INT: Int = -1_800_000_000

/**
 * The minimum 64-bit integer value of a spatial fixed integer.
 * @since 3.0
 */
@JvmField
val SP_COMPONENT_MIN_INT64: Int64 = Int64(-1_800_000_000)

/**
 * Index of longitude: `0`
 */
internal const val LON = 0

/**
 * Index of latitude: `1`
 */
internal const val LAT = 1

/**
 * Index of Z: `2`
 */
internal const val Z = 2

/**
 * Index of M: `3`
 */
internal const val M = 3

/**
 * Round the given double using [round half to even](https://en.wikipedia.org/wiki/Rounding#Rounding_half_to_even) for seventh decimal digit.
 *
 * # Important
 * It is important, that after every calculation a rounding happens to guarantee that we always keep spatial coordinates in the optimal _(and unique)_ representation. Technically, when we only want 7 decimal digits, there are plenty of possible representations for `0.1234567` like `0.12345671`, `0.12345672`, `0.12345674`, all of them represent `0.1234567`, but as more calculations we do, as more errors we introduce. Therefore, we need to round after every single calculation.
 *
 * It is not obvious, but by rounding after every single calculate, we guarantee that:
 *
 * `a + b + c` == `(a + b) + c` == `a + (b + c)`
 *
 * This is not the case without rounding, let's show this by using an example _(execute in any browser console)_:
 * ```js
 * var a = 0.1234567;
 * var b = -100.0;
 * var c = +100.0;
 * var r1 = a + (b + c);
 * var r2 = (a + b) + c
 * console.log( r1, " != ", r2 );
 * // 0.1234567  !=  0.12345670000000553
 * ```
 * Now, with the rounding implemented like here, we get:
 * ```js
 * function sp_round(value) {
 *   return Math.round(value * 10_000_000.0)
 *          / 10_000_000.0;
 * }
 * var a = 0.1234567;
 * var b = -100.0;
 * var c = +100.0;
 * var r1 = sp_round( a + sp_round(b + c) );
 * var r2 = sp_round( sp_round(a + b) + c );
 * console.log( r1, " == ", r2 );
 * // 0.1234567  ==  0.1234567
 * ```
 * @param component The spatial component.
 * @return the spatial component rounded to 7 decimal digits.
 * @since 3.0
 * @see sp_double
 * @see sp_lon
 * @see sp_lat
 * @see sp_double_to_int
 * @see sp_int_to_double
 */
fun sp_round(component: Double): Double = round(component * SP_COMPONENT_MULTIPLIER_DOUBLE) / SP_COMPONENT_MULTIPLIER_DOUBLE

/**
 * Convert the given value into a spatial component with 7 decimal digits _(does round)_.
 * @param value The value to convert and round.
 * @return the given `value`, rounded to 7 decimal digits, or `null`, if the value can't be converted.
 * @see sp_round
 */
fun sp_double(value: Any?): Double? = when(value) {
    is Double -> if (value.isNaN()) null else sp_round(value)
    is Float -> if (value.isNaN()) null else sp_round(value.toDouble())
    is Int64 -> if (value < SP_COMPONENT_MIN_INT || value > SP_COMPONENT_MAX_INT) null else sp_round(value.toDouble())
    is Long -> {
        val i64 = Int64(value)
        if (i64 < SP_COMPONENT_MIN_INT || i64 > SP_COMPONENT_MAX_INT) null else sp_round(value.toDouble())
    }
    is Number -> sp_round(value.toDouble())
    is String -> {
        val f64 = value.toDoubleOrNull()
        if (f64 == null || f64.isNaN()) null else sp_round(f64)
    }
    else -> null
}

/**
 * Round and validate the given component to longitude _(does not round)_.
 * @param component The component to validate.
 * @return the longitude component or `null`, if the given component is out of range.
 * @see sp_round
 */
fun sp_lon(component: Double?): Double? = if (component == null || component < -180.0 || component > 180.0) null else component

/**
 * Round and validate the given component to latitude _(does not round)_.
 * @param component The component to validate.
 * @return the latitude component or `null`, if the given component is out of range.
 * @see sp_round
 */
fun sp_lat(component: Double?): Double? = if (component == null || component < -90.0 || component > 90.0) null else component

/**
 * Convert the given spatial component into a spatial fixed integer _(does round)_.
 *
 * - Throws [NakshaError.ILLEGAL_ARGUMENT][naksha.base.NakshaError.ILLEGAL_ARGUMENT] if the given value is out of range.
 * @param component The spatial component.
 * @return the spatial fixed integer.
 * @see sp_round
 */
fun sp_double_to_int(component: Double): Int {
    if (component < -180.0 || component > 180.0) throw illegalArg("The component must be between -180 and 180")
    return round(component * SP_COMPONENT_MULTIPLIER_DOUBLE).toInt()
}

/**
 * Convert the given spatial fixed integer into a spatial component _(does round)_.
 *
 * - Throws [NakshaError.ILLEGAL_ARGUMENT][naksha.base.NakshaError.ILLEGAL_ARGUMENT] if the given value is out of range.
 * @param fixed The spatial fixed integer.
 * @return the spatial component.
 * @see sp_round
 */
fun sp_int_to_double(fixed: Int): Double = sp_round(fixed.toDouble() / SP_COMPONENT_MULTIPLIER_DOUBLE)

/**
 * Tests if the given value is a double and not NaN.
 * @param value the value to test.
 * @return _true_ if the value is a double, not NaN; _false_ otherwise.
 */
@Suppress("NOTHING_TO_INLINE")
internal inline fun is_double(value: Any?): Boolean = value is Double && !value.isNaN()

/**
 * Returns either the given value as double or `0.0`, if the value is no double or NaN _(does not round)_.
 * @param value The value that should be a valid double.
 * @return the given value or `0.0`, if the value is no double or NaN.
 * @see sp_round
 */
@Suppress("NOTHING_TO_INLINE")
internal inline fun as_double_or_zero(value: Any?): Double = if (value !is Double || value.isNaN()) 0.0 else value

/**
 * Returns the given value as double or `null`, if the value is no double or NaN _(does not round)_.
 * @param value The value that should be a valid double.
 * @return the given value as double or `null`.
 * @see sp_round
 */
@Suppress("NOTHING_TO_INLINE")
internal inline fun as_double_or_null(value: Any?): Double? = if (value !is Double || value.isNaN()) null else value

/**
 * Will always be true, used as initialization block.
 * @since 3.0
 */
@JvmField
val initialized = run {
    PointCoord.TYPE.initialize()
    MultiPointCoord.TYPE.initialize()
    LineStringCoord.TYPE.initialize()
    LinearRingCoord.TYPE.initialize()
    MultiLineStringCoord.TYPE.initialize()
    PolygonCoord.TYPE.initialize()
    MultiPolygonCoord.TYPE.initialize()
    // TODO: Initialize others ...
    true
}
