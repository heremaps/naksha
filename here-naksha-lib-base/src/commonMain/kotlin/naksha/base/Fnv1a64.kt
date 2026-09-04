@file:OptIn(ExperimentalJsExport::class)
@file:Suppress("OPT_IN_USAGE")

package naksha.base

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmStatic

/**
 * A helper class to perform an FNV-1a hashing.
 */
@Suppress("DuplicatedCode")
@JsExport
class Fnv1a64 {
    companion object Fnv1a64Companion {

        /**
         * The multiplier used.
         */
        private const val MUL = 1099511628211L

        /**
         * The initial value.
         */
        private const val INITIAL_VALUE = -3750763034362895579L

        /**
         * Reset the hash to the default initial value.
         * @return the initial hash.
         */
        @JvmStatic
        @JsStatic
        fun start(): Long = INITIAL_VALUE

        /**
         * Hash the given string. Internally all code-points are hashed by their width, so 8-bit, 16-bit or 24-bit
         * (unicode is never more than 21-bit) produce 1 to 3 hash calculations.
         * @param hashCode The current hash code.
         * @param string The string to hash.
         * @return the updated hash.
         */
        @JvmStatic
        @JsStatic
        fun string(hashCode: Long, string: String): Long {
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
        @JvmStatic
        @JsStatic
        fun int8(hashCode: Long, v: Byte): Long {
            var hash = hashCode xor (v.toLong() and 0xff)
            hash *= MUL
            return hash
        }

        /**
         * Hash a short that was read in big-endian encoding. This is the default encoding used by [DataViewProxy].
         * @param hashCode The current hash code.
         * @param v The value to hash.
         * @return the updated hash.
         */
        @JvmStatic
        @JsStatic
        fun int16BE(hashCode: Long, v: Short): Long {
            var hash = hashCode xor ((v.toInt() and 0xffff) ushr 8).toLong()
            hash *= MUL
            hash = hash xor (v.toLong() and 0xff)
            hash *= MUL
            return hash
        }

        /**
         * Hash a short that was read in little-endian encoding.
         * @param hashCode The current hash code.
         * @param v The value to hash.
         * @return the updated hash.
         */
        @JvmStatic
        @JsStatic
        fun int16LE(hashCode: Long, v: Short): Long {
            var hash = hashCode xor (v.toLong() and 0xff)
            hash *= MUL
            hash = hash xor ((v.toInt() and 0xffff) ushr 8).toLong()
            hash *= MUL
            return hash
        }

        /**
         * Hash an integer that was read in big-endian encoding. This is the default encoding used by [DataViewProxy].
         * @param hashCode The current hash code.
         * @param v The value to hash.
         * @return the updated hash.
         */
        @JvmStatic
        @JsStatic
        fun int32BE(hashCode: Long, v: Int): Long {
            var hash = hashCode xor (v ushr 24).toLong()
            hash *= MUL
            hash = hash xor ((v ushr 16) and 0xff).toLong()
            hash *= MUL
            hash = hash xor ((v ushr 8) and 0xff).toLong()
            hash *= MUL
            hash = hash xor (v and 0xff).toLong()
            hash *= MUL
            return hash
        }

        /**
         * Hash an integer that was read in little-endian encoding.
         * @param hashCode The current hash code.
         * @param v The value to hash.
         * @return the updated hash.
         */
        @JvmStatic
        @JsStatic
        fun int32LE(hashCode: Long, v: Int): Long {
            var hash = hashCode xor (v and 0xff).toLong()
            hash *= MUL
            hash = hash xor ((v ushr 8) and 0xff).toLong()
            hash *= MUL
            hash = hash xor ((v ushr 16) and 0xff).toLong()
            hash *= MUL
            hash = hash xor (v ushr 24).toLong()
            hash *= MUL
            return hash
        }
    }
}
