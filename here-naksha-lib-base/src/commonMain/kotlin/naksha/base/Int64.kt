@file:Suppress("OPT_IN_USAGE")

package naksha.base

import kotlin.js.JsExport
import kotlin.js.JsName

/**
 * An immutable representation of a platform specific 64-bit integer.
 */
@JsExport
@JsName("BigInt")
interface Int64 {
    // https://kotlinlang.org/docs/operator-overloading.html#infix-calls-for-named-functions

    /**
     * Returns this.
     */
    operator fun unaryPlus(): Int64

    /**
     * Negate the value, -1 becomes 1 and 1 becomes -1.
     */
    operator fun unaryMinus(): Int64

    operator fun inc(): Int64
    operator fun dec(): Int64

    operator fun plus(other: Any): Int64
    operator fun minus(other: Any): Int64
    operator fun times(other: Any): Int64
    operator fun div(other: Any): Int64
    operator fun rem(other: Any): Int64

    /**
     * Compares this integer with the specified number. Returns zero if this integer is equal to the specified other number,
     * a negative number if it's less than other, or a positive number if it's greater than other.
     */
    operator fun compareTo(other: Any?): Int
    override operator fun equals(other: Any?): Boolean

    infix fun eq(other: Any?): Boolean

    // https://kotlinlang.org/docs/functions.html#infix-notation

    /**
     * Signed shift right (copies the sign bit right).
     */
    infix fun shr(bits: Int): Int64

    /**
     * Unsigned shift rights (move the sign bit right).
     */
    infix fun ushr(bits: Int): Int64

    /**
     * Shift left.
     */
    infix fun shl(bits: Int): Int64

    /**
     * Bitwise AND.
     */
    infix fun and(other: Int64): Int64

    /**
     * Bitwise OR.
     */
    infix fun or(other: Int64): Int64

    /**
     * Bitwise XOR.
     */
    infix fun xor(other: Int64): Int64

    /**
     * Bitwise NOT.
     */
    fun inv(): Int64

    /**
     * Returns the value of the specified number as a `byte`.
     *
     * @return  the numeric value represented by this object after conversion to type `byte`.
     */
    fun toByte(): Byte

    /**
     * Returns the value of the specified number as a `short`.
     *
     * @return  the numeric value represented by this object after conversion to type `short`.
     */
    fun toShort(): Short

    /**
     * Returns the value of the specified number as a `int`.
     *
     * @return  the numeric value represented by this object after conversion to type `int`.
     */
    fun toInt(): Int

    /**
     * Returns the value of the specified number as a `long`.
     *
     * @return  the numeric value represented by this object after conversion to type `long`.
     */
    @Suppress("NON_EXPORTABLE_TYPE")
    fun toLong(): Long

    /**
     * Returns the value of the specified number as a `float`.
     *
     * @return  the numeric value represented by this object after conversion to type `float`.
     */
    fun toFloat(): Float

    /**
     * Returns the value of the specified number as a `double`.
     *
     * @return  the numeric value represented by this object after conversion to type `double`.
     */
    fun toDouble(): Double

    /**
     * Cast the 64-bit integer into a 64-bit floating point number using only raw bits. That means, the 64-bits of the
     * integer are treated as if they store an [IEEE-754](https://en.wikipedia.org/wiki/IEEE_754) 64-bit floating point number,
     * so for example 0xffff_ffff_ffff_ffff becomes [Double.NaN].
     * @return The integer converted into a double.
     */
    fun toDoubleRawBits(): Double
}

fun asInt64(any: Any?): Int64 = any as Int64