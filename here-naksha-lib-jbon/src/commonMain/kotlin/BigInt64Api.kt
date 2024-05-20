@file:OptIn(ExperimentalJsExport::class)

package com.here.naksha.lib.jbon

import com.here.naksha.lib.base.Int64
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/**
 * The native API to access 64-bit integers.
 */
@Suppress("FunctionName", "unused", "NON_EXPORTABLE_TYPE")
@JsExport
interface BigInt64Api {
    /**
     * Returns the maximum value of a 64-bit integer.
     * @return The maximum value of a 64-bit integer.
     */
    fun MAX_VALUE(): BigInt64

    /**
     * Returns the minimum value of a 64-bit integer.
     * @return The minimum value of a 64-bit integer.
     */
    fun MIN_VALUE(): BigInt64

    /**
     * Returns the value zero as 64-bit integer.
     * @return The value zero as 64-bit integer.
     */
    fun ZERO(): BigInt64

    /**
     * Returns the value _-1_ as 64-bit integer.
     * @return The value _-1_ as 64-bit integer.
     */
    fun MINUS_ONE(): BigInt64

    fun eq(t: BigInt64, o: BigInt64): Boolean
    fun eqi(t: BigInt64, o: Int): Boolean
    fun lt(t: BigInt64, o: BigInt64): Boolean
    fun lti(t: BigInt64, o: Int): Boolean
    fun lte(t: BigInt64, o: BigInt64): Boolean
    fun ltei(t: BigInt64, o: Int): Boolean
    fun gt(t: BigInt64, o: BigInt64): Boolean
    fun gti(t: BigInt64, o: Int): Boolean
    fun gte(t: BigInt64, o: BigInt64): Boolean
    fun gtei(t: BigInt64, o: Int): Boolean
    fun shr(t: BigInt64, bits: Int): BigInt64
    fun ushr(t: BigInt64, bits: Int): BigInt64
    fun shl(t: BigInt64, bits: Int): BigInt64
    fun add(t: BigInt64, o: BigInt64): BigInt64
    fun addi(t: BigInt64, o: Int): BigInt64
    fun addf(t: BigInt64, o: Double): BigInt64
    fun sub(t: BigInt64, o: BigInt64): BigInt64
    fun subi(t: BigInt64, o: Int): BigInt64
    fun subf(t: BigInt64, o: Double): BigInt64
    fun mul(t: BigInt64, o: BigInt64): BigInt64
    fun muli(t: BigInt64, o: Int): BigInt64
    fun mulf(t: BigInt64, o: Double): BigInt64
    fun mod(t: BigInt64, o: BigInt64): BigInt64
    fun modi(t: BigInt64, o: Int): BigInt64
    fun div(t: BigInt64, o: BigInt64): BigInt64
    fun divi(t: BigInt64, o: Int): BigInt64
    fun divf(t: BigInt64, o: Double): BigInt64
    fun and(t: BigInt64, o: BigInt64): BigInt64
    fun or(t: BigInt64, o: BigInt64): BigInt64
    fun xor(t: BigInt64, o: BigInt64): BigInt64
    fun inv(t: BigInt64): BigInt64
    fun toInt(t: BigInt64): Int = bigInt64ToInt(t)
    fun toLong(t: BigInt64): Long = bigInt64ToLong(t)
    fun toDouble(t: BigInt64): Double = bigInt64ToDouble(t, false)
    fun toInt64(t: BigInt64): Int64 = Int64(bigInt64ToLong(t))
    fun toDoubleRawBits(t: BigInt64): Double = bigInt64ToDouble(t, true)

    /**
     * Convert the given 32-bit integer to a 64-bit integer.
     * @param value The 32-bit integer to widen.
     * @return The given 32-bit integer as 64-bit integer.
     */
    fun intToBigInt64(value: Int): BigInt64

    /**
     * Convert the given 64-bit integer to a 32-bit integer.
     * @param value The 64-bit integer to shorten.
     * @return The given 64-bit integer as 32-bit integer.
     */
    fun bigInt64ToInt(value: BigInt64): Int

    /**
     * Converts an internal 64-bit integer into a platform specific.
     * @param value The internal 64-bit.
     * @return The platform specific 64-bit.
     */
    fun longToBigInt64(value: Long): BigInt64

    /**
     * Converts a platform specific 64-bit integer into an internal one to be used for example with the [IDataView].
     * @param value The platform specific 64-bit integer.
     * @return The internal 64-bit integer.
     */
    fun bigInt64ToLong(value: BigInt64): Long

    /**
     * Converts the given double value into a 64-bit integer.
     * @param value The double to convert.
     * @param raw If _true_, then the raw bits of the double are converted, otherwise a floor is done.
     * @return The 64-bit integer representation of the double.
     */
    fun doubleToBigInt64(value: Double, raw: Boolean): BigInt64

    /**
     * Converts the given 64-bit integer value into a double.
     * @param value The 64-bit integer to convert.
     * @param raw If _true_, then the raw bits of the integer are converted, otherwise a value cast.
     * @return The double representation of the 64-bit integer.
     */
    fun bigInt64ToDouble(value: BigInt64, raw: Boolean): Double

    /**
     * Calculate a hash-code and return it.
     * @param value The big-int.
     * @return The 32-bit hash code.
     */
    fun hashCodeOf(value: BigInt64): Int
}