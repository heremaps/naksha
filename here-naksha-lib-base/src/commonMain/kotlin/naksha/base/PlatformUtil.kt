package naksha.base

import naksha.base.Platform.PlatformCompanion.MAX_SAFE_INT
import naksha.base.Platform.PlatformCompanion.MAX_SAFE_INT64
import naksha.base.Platform.PlatformCompanion.MIN_SAFE_INT
import naksha.base.Platform.PlatformCompanion.MIN_SAFE_INT64
import naksha.base.Platform.PlatformCompanion.random
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmField
import kotlin.jvm.JvmStatic
import kotlin.math.abs
import kotlin.math.round

/**
 * All utility functions that work cross-platform.
 */
@Suppress("OPT_IN_USAGE", "unused")
@JsExport
class PlatformUtil private constructor() {
    companion object PlatformUtilCompanion {
        /**
         * A switch to toggle debug logs (disabled by default).
         *
         * Sometimes exceptions are caught internally in `lib-psql`, sometimes even suppressed, this normally no problem, but when debugging this can become a problem, therefore this switch enables to print suppressed stack traces as INFO logs and other debug information. It may as well send
         */
        @JvmField
        @JsStatic
        var ENABLE_DEBUG: Boolean = false

        /**
         * A switch to toggle info logs (disabled by default).
         */
        @JvmField
        @JsStatic
        var ENABLE_INFO: Boolean = false

        /**
         * A switch to toggle warning logs (enabled by default).
         */
        @JvmField
        @JsStatic
        var ENABLE_WARN: Boolean = true

        /**
         * A switch to toggle error logs (enabled by default).
         */
        @JvmField
        @JsStatic
        var ENABLE_ERROR: Boolean = true

        /**
         * The maximal 32-bit floating point number that can be encoded without losing precision.
         */
        @JvmField
        @JsStatic
        val FLOAT_MAX: Double = Platform.toDoubleRawBits(Int64(0x47efffffe0000000L))

        /**
         * The minimal 32-bit floating point number that can be encoded without losing precision.
         */
        @JvmField
        @JsStatic
        val FLOAT_MIN: Double = Platform.toDoubleRawBits(Int64(0x36a0000000000000L))

        /**
         * A single milliseconds.
         */
        @JvmField
        @JsStatic
        val MILLISECOND = Int64(1)

        /**
         * A second in milliseconds.
         */
        @JvmField
        @JsStatic
        val SECOND = Int64(1000)

        /**
         * A minute in milliseconds.
         */
        @JvmField
        @JsStatic
        val MINUTE = Int64(60 * 1000)

        /**
         * An hour in milliseconds.
         */
        @JvmField
        @JsStatic
        val HOUR = Int64(60 * 60 * 1000)

        /**
         * A day in milliseconds.
         */
        @JvmField
        @JsStatic
        val DAY = Int64(24 * 60 * 60 * 1000)

        /**
         * A multiplier to convert milliseconds to microseconds or a divider, to turn microseconds into millis.
         */
        @JvmField
        @JsStatic
        val MILLIS_TO_MICROS = Int64(1000)

        /**
         * The default size of a view. This is used at various placed.
         * @since 3.0
         */
        @JvmField
        @JsStatic
        var defaultDataViewSize = 128

        /**
         * An array with the "safe" characters to random strings, so `0..9a..z`, being `36` characters.
         * @since 3.0
         */
        private val randomCharacters = CharArray(36) {
            when (it) {
                in 0..9 -> ('0'.code + it).toChar()
                in 10..35 -> ('a'.code + (it - 10)).toChar()
                else -> throw IllegalStateException()
            }
        }

        /**
         * Generates a random "safe" string, persisting only out of `0` to `9`, and `a` to `z` _(base 36)_ characters, never starting with a digits _(`0..9`)_. This string normally can be used without escaping at all places where unique identifiers are needed, and is quite safe for humans to read and remember.
         *
         * The default length of 12 characters results in `26*36^11` possible random values, aka 3.4 [quintillion](https://en.wikipedia.org/wiki/Names_of_large_numbers).
         *
         * @param length The amount of characters to return, if less than or equal zero, 12 characters are used.
         * @return The random string.
         * @since 3.0
         */
        @JvmStatic
        @JsStatic
        fun randomString(length: Int = 0): String {
            // This way, in Javascript, we catch undefined.
            val end = if (length >= 1) length else 12
            val chars = randomCharacters
            val sb = StringBuilder()
            var pos = 0
            // The first character should not be 0 to 9
            var i: Int = round(random() * 25.0).toInt() + 10 // `10..35`
            sb.append(chars[i])
            while (++pos < end) {
                i = round(random() * 35.0).toInt() // `0..35`
                sb.append(chars[i])
            }
            return sb.toString()
        }

        /**
         * Calculates a hash code above all given values.
         * @param values the value above which to calculate a hash-code.
         * @return the calculated hash-code.
         */
        @JvmStatic
        @JsStatic
        fun hashCodeOf(vararg values: Any?): Int = hashCodeOf(0, *values)

        private fun hashCodeOf(hashInput: Int, vararg values: Any?): Int {
            var hash = hashInput
            for (v in values) {
                when (v) {
                    null -> hash *= 31
                    is ByteArray -> hash = hash * 31 + v.contentHashCode()
                    is Array<*> -> for (e in v) hash = hashCodeOf(hash, e)
                    is List<*> -> for (e in v) hash = hashCodeOf(hash, e)
                    is Map<*,*> -> for (e in v) hash = hashCodeOf(hash, e.value)
                    else -> hash = hash * 31 + v.hashCode()
                }
            }
            return hash
        }

        /**
         * The multiplier/divisor used when converting doubles into spatial components _(`10_000_000.0` aka `1e7`)_.
         * @since 3.0
         */
        const val ROUND_MULTIPLIER_DOUBLE: Double = 10_000_000.0 // 1e7

        /**
         * The multiplier/divisor used when converting doubles into a spatial fixed integer _(`10_000_000` aka 1e7)_.
         * @since 3.0
         */
        const val ROUND_MULTIPLIER_INT: Int = 10_000_000

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
         * function round_double(value) {
         *   return Math.round(value * 10_000_000.0)
         *          / 10_000_000.0;
         * }
         * var a = 0.1234567;
         * var b = -100.0;
         * var c = +100.0;
         * var r1 = round_double( a + round_double(b + c) );
         * var r2 = round_double( round_double(a + b) + c );
         * console.log( r1, " == ", r2 );
         * // 0.1234567  ==  0.1234567
         * ```
         * @param value The double value.
         * @return the double value rounded to 7 decimal digits.
         * @since 3.0
         */
        @JvmStatic
        @JsStatic
        fun round_double(value: Double): Double = round(value * ROUND_MULTIPLIER_DOUBLE) / ROUND_MULTIPLIER_DOUBLE

        /**
         * Tests if the given value is logically a floating point number _(12.0 is not, 12.5 is)_.
         * @param value The value to test.
         * @return _true_ if the given `value` is logically a floating point number; _false_ otherwise.
         * @see isLogicalDouble
         * @see isLogicalInt
         * @see isLogicalInt64
         * @see asSafeInt
         * @see asSafeInt64
         * @see asSafeDouble
         */
        @JvmStatic
        @JsStatic
        fun isLogicalDouble(value: Any?): Boolean {
            if (value is Float) return value != round(value)
            if (value is Double) return value != round(value)
            if (value is Long) {
                val i64 = Int64(value)
                if (i64 >= MIN_SAFE_INT64 && i64 <= MAX_SAFE_INT64) value.toDouble() else null
            }
            if (value is Int64) {
                if (value >= MIN_SAFE_INT64 && value <= MAX_SAFE_INT64) value.toDouble() else null
            }
            return value is Byte || value is Short || value is Int
        }

        /**
         * Convert the given value into a 64-bit floating point number.
         *
         * **Warning: Does not fail for 12.0, only limited by bounds, so too big integers will fail!**
         * @param value The value to convert.
         * @return the given `value` as 64-bit floating point number; or `null`, if the value is no number or out of bounds.
         * @see isLogicalDouble
         * @see isLogicalInt
         * @see isLogicalInt64
         * @see asSafeInt
         * @see asSafeInt64
         * @see asSafeDouble
         */
        @JvmStatic
        @JsStatic
        fun asSafeDouble(value: Any?): Double? = when(value) {
            is Float -> round(value.toDouble() * 1e6) / 1e6
            is Double -> value
            is Byte -> abs(value.toDouble())
            is Short -> value.toDouble()
            is Int -> value.toDouble()
            is Long -> {
                val i64 = Int64(value)
                if (i64 >= MIN_SAFE_INT64 && i64 <= MAX_SAFE_INT64) value.toDouble() else null
            }
            is Int64 -> {
                if (value >= MIN_SAFE_INT64 && value <= MAX_SAFE_INT64) value.toDouble() else null
            }
            else -> null
        }

        /**
         * Tests if the given value is logically an 32-bit integer _(12.0 is, 12.5 is not)_.
         * @param value The value to test.
         * @return _true_ if the given `value` is logically an 32-bit integer; _false_ otherwise.
         * @see isLogicalDouble
         * @see isLogicalInt
         * @see isLogicalInt64
         * @see asSafeInt
         * @see asSafeInt64
         * @see asSafeDouble
         */
        @JvmStatic
        @JsStatic
        fun isLogicalInt(value: Any?): Boolean {
            if (value is Float) return value == round(value) && value >= -2147483648.0 && value < 2147483648.0
            if (value is Double) return value == round(value) && value >= -2147483648.0 && value < 2147483648.0
            if (value is Long) return value >= -2147483648L && value < 2147483648L
            if (value is Int64) return value >= -2147483648L && value < 2147483648L
            return value is Byte || value is Short || value is Int
        }

        /**
         * Convert the given value into a 32-bit integer _(will fail for 12.5, but works for 12.0)_.
         * @param value The value to convert.
         * @return the given `value` as 32-bit integer; or `null`, if the value is no number or out of bounds.
         * @see isLogicalDouble
         * @see isLogicalInt
         * @see isLogicalInt64
         * @see asSafeInt
         * @see asSafeInt64
         * @see asSafeDouble
         */
        @JvmStatic
        @JsStatic
        fun asSafeInt(value: Any?): Int? = when(value) {
            is Float -> if (value == round(value) && value >= -2147483648.0 && value < 2147483648.0) round(value).toInt() else null
            is Double -> if (value == round(value) && value >= -2147483648.0 && value < 2147483648.0) round(value).toInt() else null
            is Byte -> abs(value.toInt())
            is Short -> value.toInt()
            is Int -> value
            is Long -> {
                val i64 = Int64(value)
                if (i64 >= -2147483648L && i64 < 2147483648L) value.toInt() else null
            }
            is Int64 -> if (value >= -2147483648L && value < 2147483648L) value.toInt() else null
            else -> null
        }

        /**
         * Tests if the given value is logically an 64-bit integer _(12.0 is, 12.5 is not)_.
         * @param value The value to test.
         * @return _true_ if the given `value` is logically an 64-bit integer; _false_ otherwise.
         * @see isLogicalDouble
         * @see isLogicalInt
         * @see isLogicalInt64
         * @see asSafeInt
         * @see asSafeInt64
         * @see asSafeDouble
         */
        @JvmStatic
        @JsStatic
        fun isLogicalInt64(value: Any?): Boolean {
            if (value is Float) return value == round(value) && value >= MIN_SAFE_INT && value <= MAX_SAFE_INT
            if (value is Double) return value == round(value) && value >= MIN_SAFE_INT && value <= MAX_SAFE_INT
            return value is Byte || value is Short || value is Int || value is Long || value is Int64
        }

        /**
         * Convert the given value into a 64-bit floating point number _(will fail for 12.5, but works for 12.0)_.
         * @param value The value to convert.
         * @return the given `value` as 64-bit floating point number; or `null`, if the value is no number or out of bounds.
         * @see isLogicalDouble
         * @see isLogicalInt
         * @see isLogicalInt64
         * @see asSafeInt
         * @see asSafeInt64
         * @see asSafeDouble
         */
        @JvmStatic
        @JsStatic
        fun asSafeInt64(value: Any?): Int64? {
            if (value is Float) {
                val f32 = round(value)
                if (value != f32) return null
                val f64 = f32.toDouble()
                if (f64 < MIN_SAFE_INT || f64 > MAX_SAFE_INT) return null
                return Int64(f64)
            }
            if (value is Double) {
                val f64 = round(value)
                if (value != f64 || f64 < MIN_SAFE_INT || f64 > MAX_SAFE_INT) return null
                return Int64(f64)
            }
            return when(value) {
                is Byte -> Int64(abs(value.toInt()))
                is Short -> Int64(value.toInt())
                is Int -> Int64(value)
                is Long -> Int64(value)
                is Int64 -> value
                else -> null
            }
        }

        /**
         * Recursively compare this object with another, checking for values instead of just referential.
         * This is needed because for arrays, the == operation compares whether the arrays are the same object.
         * This will work for any nested structures of maps, lists, and arrays.
         */
        @JvmStatic
        @JsStatic
        fun deepEquals(obj1: Any?, obj2: Any?): Boolean {
            if (obj1 === obj2) return true  // Same reference, or both null
            if (obj1 == null || obj2 == null) return false  // One is null, the other is not
            if (obj1::class != obj2::class) return false  // Different types

            return when (obj1) {
                is Array<*> -> obj1.contentDeepEquals(obj2 as Array<*>)
                is List<*> -> {
                    if (obj1.size != (obj2 as List<*>).size) return false
                    for (i in 0 until obj1.size) {
                        val v1 = obj1[i]
                        val v2 = obj2[i]
                        if (!deepEquals(v1, v2)) return false
                    }
                    return true
                }
                is Map<*, *> -> {
                    if (obj1.size != (obj2 as Map<*,*>).size) return false
                    val it = obj1.keys.iterator()
                    while (it.hasNext()) {
                        val key = it.next()
                        val v1 = obj1[key]
                        val v2 = obj2[key]
                        if (!deepEquals(v1, v2)) return false
                    }
                    return true
                }
                else -> obj1 == obj2  // Primitive types, or any other objects
            }
        }
    }
}