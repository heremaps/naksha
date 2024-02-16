@file:OptIn(ExperimentalJsExport::class)

package com.here.naksha.lib.jbon

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/**
 * A helper class to perform an FNV-1a hashing.
 */
@Suppress("DuplicatedCode")
@JsExport
class Fnv1a64 {
    /**
     * The multiplicator used.
     */
    private val MUL = BigInt64(1099511628211L)

    /**
     * The current hash.
     */
    var hash = BigInt64(0xCBF29CE484222325uL.toLong())

    /**
     * Reset the hash to the default initial value.
     * @return this.
     */
    fun reset(): Fnv1a64 {
        hash = BigInt64(0xCBF29CE484222325uL.toLong())
        return this
    }

    /**
     * Hash the given string. Internally all code-points are hashed by their width, so 8-bit, 16-bit or 24-bit
     * (unicode is never more than 21-bit) produce 1 to 3 hash calculations.
     * @param string The string to hash.
     * @return this.
     */
    fun string(string: String): Fnv1a64 {
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
                in 0..255 -> int8(unicode.toByte())
                in 256..65535 -> int16BE(unicode.toShort())
                in 65536..2_097_151 -> {
                    int8((unicode ushr 16).toByte())
                    int16BE(unicode.toShort())
                }
                else -> throw IllegalArgumentException("Invalid unicode found: $unicode")
            }
        }
        return this
    }

    /**
     * Hash a single byte.
     * @param v The value to hash.
     * @return this.
     */
    fun int8(v: Byte): Fnv1a64 {
        hash = hash xor BigInt64(v.toInt() and 0xff)
        hash *= MUL
        return this
    }

    /**
     * Hash a short that was read in big-endian encoding. This is the default encoding used by [IDataView].
     * @param v The value to hash.
     * @return this.
     */
    fun int16BE(v: Short): Fnv1a64 {
        hash = hash xor BigInt64((v.toInt() and 0xffff) ushr 8)
        hash *= MUL
        hash = hash xor BigInt64(v.toInt() and 0xff)
        hash *= MUL
        return this
    }

    /**
     * Hash an short that was read in little-endian encoding.
     * @param v The value to hash.
     * @return this.
     */
    fun int16LE(v: Short): Fnv1a64 {
        hash = hash xor BigInt64(v.toInt() and 0xff)
        hash *= MUL
        hash = hash xor BigInt64((v.toInt() and 0xffff) ushr 8)
        hash *= MUL
        return this
    }

    /**
     * Hash an integer that was read in big-endian encoding. This is the default encoding used by [IDataView].
     * @param v The value to hash.
     * @return this.
     */
    fun int32BE(v: Int): Fnv1a64 {
        hash = hash xor BigInt64(v ushr 24)
        hash *= MUL
        hash = hash xor BigInt64((v ushr 16) and 0xff)
        hash *= MUL
        hash = hash xor BigInt64((v ushr 8) and 0xff)
        hash *= MUL
        hash = hash xor BigInt64(v and 0xff)
        hash *= MUL
        return this
    }

    /**
     * Hash an integer that was read in little-endian encoding.
     * @param v The value to hash.
     * @return this.
     */
    fun int32LE(v: Int): Fnv1a64 {
        hash = hash xor BigInt64(v and 0xff)
        hash *= MUL
        hash = hash xor BigInt64((v ushr 8) and 0xff)
        hash *= MUL
        hash = hash xor BigInt64((v ushr 16) and 0xff)
        hash *= MUL
        hash = hash xor BigInt64(v ushr 24)
        hash *= MUL
        return this
    }
}