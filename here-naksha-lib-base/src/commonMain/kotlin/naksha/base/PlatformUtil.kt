package naksha.base

import naksha.base.Platform.PlatformCompanion.random
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmField
import kotlin.jvm.JvmStatic

/**
 * All utility functions that work cross-platform.
 */
@Suppress("OPT_IN_USAGE", "unused")
@JsExport
class PlatformUtil {
    companion object PlatformUtilCompanion {
        /**
         * A switch to toggle debug logs (disabled by default).
         *
         * Sometimes exceptions are caught internally in `lib-psql`, sometimes even suppressed, this normally no problem, but when debugging this can become a problem, therefore this switch enables to print suppressed stack traces as INFO logs and other debug information. It may as well send
         */
        @JsStatic
        @JvmField
        var ENABLE_DEBUG: Boolean = false

        /**
         * A switch to toggle info logs (disabled by default).
         */
        @JsStatic
        @JvmField
        var ENABLE_INFO: Boolean = false

        /**
         * A switch to toggle warning logs (enabled by default).
         */
        @JsStatic
        @JvmField
        var ENABLE_WARN: Boolean = true

        /**
         * A switch to toggle error logs (enabled by default).
         */
        @JsStatic
        @JvmField
        var ENABLE_ERROR: Boolean = true

        /**
         * The maximal 32-bit floating point number that can be encoded without losing precision.
         */
        @JsStatic
        @JvmField
        val FLOAT_MAX: Double = Platform.toDoubleRawBits(Int64(0x47efffffe0000000L))

        /**
         * The minimal 32-bit floating point number that can be encoded without losing precision.
         */
        @JsStatic
        @JvmField
        val FLOAT_MIN: Double = Platform.toDoubleRawBits(Int64(0x36a0000000000000L))

        /**
         * A second in milliseconds.
         */
        @JsStatic
        @JvmField
        val SECOND = Int64(1000)

        /**
         * A minute in milliseconds.
         */
        @JsStatic
        @JvmField
        val MINUTE = Int64(60 * 1000)

        /**
         * An hour in milliseconds.
         */
        @JsStatic
        @JvmField
        val HOUR = Int64(60 * 60 * 1000)

        /**
         * A day in milliseconds.
         */
        @JsStatic
        @JvmField
        val DAY = Int64(24 * 60 * 60 * 1000)

        /**
         * The default size of a view. This is used at various placed.
         */
        @JsStatic
        @JvmField
        var defaultDataViewSize = 128

        /**
         * An array with the Web-Safe Base-64 characters.
         */
        @JvmStatic
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