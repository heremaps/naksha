@file:OptIn(ExperimentalJsExport::class)

package naksha.base

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/**
 * A helper class to perform an FNV-1a hashing.
 */
@Suppress("DuplicatedCode")
@JsExport
object Fnv1a32 {
    /**
     * Start a new hash.
     * @return the default initial value.
     */
    fun start(): Int = 0x811C9DC5u.toInt()

    /**
     * Hash the given string. Internally all code-points are hashed by their width, so 8-bit, 16-bit or 24-bit
     * (unicode is never more than 21-bit) produce 1 to 3 hash calculations.
     * @param hashCode The current hash code.
     * @param string The string to hash.
     * @return the updated hash-code.
     */
    fun string(hashCode: Int, string: String): Int {
        var hash = hashCode
        var i = 0
        while (i < string.length) {
            val hi = string[i++]
            var unicode: Int
            if (i < string.length && hi.isHighSurrogate()) {
                val lo = string[i++]
                require(lo.isLowSurrogate()) {"Found invalid low surrogate at index ${i-1}"}
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
     * Hash the given string reverse (from back to front). Internally all code-points are hashed by their width, so 8-bit, 16-bit or
     * 24-bit (unicode is never more than 21-bit) produce 1 to 3 hash calculations.
     * @param hashCode The current hash code.
     * @param string The string to hash.
     * @return the updated hash-code.
     */
    fun stringReverse(hashCode: Int, string: String): Int {
        var hash = hashCode
        var i = string.length
        while (--i >= 0) {
            val lo = string[i]
            var unicode: Int
            if (i < string.length && lo.isLowSurrogate()) {
                val hi = string[i++]
                require(hi.isHighSurrogate()) {"Found invalid high surrogate at index ${i-1}"}
                unicode = CodePoints.toCodePoint(hi, lo)
            } else {
                unicode = lo.code
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
    fun int8(hashCode: Int, v: Byte): Int {
        var hash = hashCode xor (v.toInt() and 0xff)
        hash *= 16777619
        return hash
    }

    /**
     * Hash a short that was read in big-endian encoding. This is the default encoding used by [DataViewProxy].
     * @param hashCode The current hash code.
     * @param v The value to hash.
     * @return the updated hash.
     */
    fun int16BE(hashCode: Int, v: Short): Int {
        var hash = hashCode xor ((v.toInt() and 0xffff) ushr 8)
        hash *= 16777619
        hash = hash xor (v.toInt() and 0xff)
        hash *= 16777619
        return hash
    }

    /**
     * Hash a short that was read in little-endian encoding.
     * @param hashCode The current hash code.
     * @param v The value to hash.
     * @return the updated hash.
     */
    fun int16LE(hashCode: Int, v: Short): Int {
        var hash = hashCode xor (v.toInt() and 0xff)
        hash *= 16777619
        hash = hash xor ((v.toInt() and 0xffff) ushr 8)
        hash *= 16777619
        return hash
    }

    /**
     * Hash an integer that was read in big-endian encoding. This is the default encoding used by [DataViewProxy].
     * @param hashCode The current hash code.
     * @param v The value to hash.
     * @return the updated hash.
     */
    fun int32BE(hashCode: Int, v: Int): Int {
        var hash = hashCode xor (v ushr 24)
        hash *= 16777619
        hash = hash xor ((v ushr 16) and 0xff)
        hash *= 16777619
        hash = hash xor ((v ushr 8) and 0xff)
        hash *= 16777619
        hash = hash xor (v and 0xff)
        hash *= 16777619
        return hash
    }

    /**
     * Hash an integer that was read in little-endian encoding.
     * @param hashCode The current hash code.
     * @param v The value to hash.
     * @return the updated hash.
     */
    fun int32LE(hashCode: Int, v: Int): Int {
        var hash = hashCode xor (v and 0xff)
        hash *= 16777619
        hash = hash xor ((v ushr 8) and 0xff)
        hash *= 16777619
        hash = hash xor ((v ushr 16) and 0xff)
        hash *= 16777619
        hash = hash xor (v ushr 24)
        hash *= 16777619
        return hash
    }

    /**
     * Hash a byteArray.
     *
     * @param byteArray to calculate hash
     * @param currentHash current hash as a base, or nothing
     * @return hash
     */
    fun hashByteArray(byteArray: ByteArray?, currentHash: Int = start()): Int {
        if (byteArray == null) {
            return currentHash
        }
        var hash = currentHash
        for (byte in byteArray) {
            hash = int8(hash, byte)
        }
        return hash
    }
}