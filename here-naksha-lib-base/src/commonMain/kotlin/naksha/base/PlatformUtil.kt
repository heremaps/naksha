package naksha.base

import naksha.base.Platform.random
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmStatic

/**
 * All utility functions that work cross-platform.
 */
@Suppress("OPT_IN_USAGE", "unused")
@JsExport
class PlatformUtil {
    companion object {
        /**
         * The maximal 32-bit floating point number that can be encoded without losing precision.
         */
        @JsStatic
        val FLOAT_MAX: Double = Platform.toDoubleRawBits(Int64(0x47efffffe0000000L))

        /**
         * The minimal 32-bit floating point number that can be encoded without losing precision.
         */
        @JsStatic
        val FLOAT_MIN: Double = Platform.toDoubleRawBits(Int64(0x36a0000000000000L))

        /**
         * The default size of a view. This is used at various placed.
         */
        @JsStatic
        var defaultDataViewSize = 128

        /**
         * An array with the Web-Safe Base-64 characters.
         */
        private val randomCharacters = CharArray(64) {
            when (it) {
                in 0..9 -> ('0'.code + it).toChar()
                in 10..35 -> ('a'.code + (it - 10)).toChar()
                in 36..61 -> ('A'.code + (it - 36)).toChar()
                // This duplicates a and z, but we for random strings we do not care that much
                62 -> 'a'
                63 -> 'z'
                else -> throw IllegalStateException()
            }
        }

        /**
         * Generates a random string that Web-URL safe and matches those of the Web-Safe Base64 encoding, so persists
         * only out of `a` to `z`, `A` to `Z`, `0` to `9`.
         * @param length The amount of characters to return, if less than or equal zero, 12 characters are used.
         * @return The random string.
         */
        @JvmStatic
        @JsStatic
        fun randomString(length: Int = 12): String {
            // This way, in Javascript, we catch undefined.
            val end = if (length >= 1) length else 12
            val chars = randomCharacters
            val sb = StringBuilder()
            var i = 0
            while (i++ < end) {
                sb.append(chars[(random() * 64.0).toInt() and 63])
            }
            return sb.toString()
        }

    }
}