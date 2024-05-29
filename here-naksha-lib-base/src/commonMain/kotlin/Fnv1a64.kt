@file:OptIn(ExperimentalJsExport::class)

package com.here.naksha.lib.base

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/**
 * A helper class to perform an FNV-1a hashing.
 */
@Suppress("DuplicatedCode")
@JsExport
object Fnv1a64 {
    /**
     * The multiplier used.
     */
    private val MUL = N.toInt64(1099511628211L)

    /**
     * The initial value.
     */
    private val INITIAL_VALUE = N.toInt64(0xCBF29CE484222325uL.toLong())

    /**
     * Reset the hash to the default initial value.
     * @return the initial hash.
     */
    fun start(): Int64 = INITIAL_VALUE

    /**
     * Hash the given string. Internally all code-points are hashed by their width, so 8-bit, 16-bit or 24-bit
     * (unicode is never more than 21-bit) produce 1 to 3 hash calculations.
     * @param hashCode The current hash code.
     * @param string The string to hash.
     * @return the updated hash.
     */
    fun string(hashCode:Int64, string: String): Int64 {
        var hash = hashCode
        var i = 0
        while (i < string.length) {
            val hi = string[i++]
            var unicode: Int
            if (i < string.length && hi.isHighSurrogate()) {
                val lo = string[i++]
                require(lo.isLowSurrogate())
                unicode = CodePoints.toCodePoint(hi, lo)
            } else {
                unicode = hi.code
            }
            when (unicode) {
                in 0..255 -> hash = int8(hash, unicode.toByte())
                in 256..65535 -> hash = int16BE(hash, unicode.toShort())
                in 65536..2_097_151 -> {
                    hash = int8(hash, (unicode ushr 16).toByte())
                    hash = int16BE(hash, unicode.toShort())
                }
                else -> throw IllegalArgumentException("Invalid unicode found: $unicode")
            }
        }
        return hash
    }

    /**
     * Hash a single byte.
     * @param hashCode The current hash code.
     * @param v The value to hash.
     * @return the updated hash.
     */
    fun int8(hashCode:Int64, v: Byte): Int64 {
        var hash = hashCode xor N.toInt64(v.toInt() and 0xff)
        hash *= MUL
        return hash
    }

    /**
     * Hash a short that was read in big-endian encoding. This is the default encoding used by [IDataView].
     * @param hashCode The current hash code.
     * @param v The value to hash.
     * @return the updated hash.
     */
    fun int16BE(hashCode:Int64, v: Short): Int64 {
        var hash = hashCode xor Int64((v.toInt() and 0xffff) ushr 8)
        hash *= MUL
        hash = hash xor Int64(v.toInt() and 0xff)
        hash *= MUL
        return hash
    }

    /**
     * Hash a short that was read in little-endian encoding.
     * @param hashCode The current hash code.
     * @param v The value to hash.
     * @return the updated hash.
     */
    fun int16LE(hashCode:Int64, v: Short): Int64 {
        var hash = hashCode xor Int64(v.toInt() and 0xff)
        hash *= MUL
        hash = hash xor Int64((v.toInt() and 0xffff) ushr 8)
        hash *= MUL
        return hash
    }

    /**
     * Hash an integer that was read in big-endian encoding. This is the default encoding used by [IDataView].
     * @param hashCode The current hash code.
     * @param v The value to hash.
     * @return the updated hash.
     */
    fun int32BE(hashCode:Int64, v: Int): Int64 {
        var hash = hashCode xor Int64(v ushr 24)
        hash *= MUL
        hash = hash xor Int64((v ushr 16) and 0xff)
        hash *= MUL
        hash = hash xor Int64((v ushr 8) and 0xff)
        hash *= MUL
        hash = hash xor Int64(v and 0xff)
        hash *= MUL
        return hash
    }

    /**
     * Hash an integer that was read in little-endian encoding.
     * @param hashCode The current hash code.
     * @param v The value to hash.
     * @return the updated hash.
     */
    fun int32LE(hashCode:Int64, v: Int): Int64 {
        var hash = hashCode xor Int64(v and 0xff)
        hash *= MUL
        hash = hash xor Int64((v ushr 8) and 0xff)
        hash *= MUL
        hash = hash xor Int64((v ushr 16) and 0xff)
        hash *= MUL
        hash = hash xor Int64(v ushr 24)
        hash *= MUL
        return hash
    }
}