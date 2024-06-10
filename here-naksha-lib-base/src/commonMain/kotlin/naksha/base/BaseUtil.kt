package naksha.base

import naksha.base.Platform.Companion.random
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmStatic

/**
 * All utility functions that work cross-platform.
 */
@Suppress("OPT_IN_USAGE")
@JsExport
class BaseUtil {
    companion object {
        /**
         * When no size is explicitly given, the amount of byte to allocated initially for a data-view.
         */
        var defaultDataViewSize = 128

        /**
         * An array with the Web-Safe Base-64 characters.
         */
        private val randomCharacters = CharArray(64) {
            when (it) {
                in 0..9 -> ('0'.code + it).toChar()
                in 10..35 -> ('a'.code + (it - 10)).toChar()
                in 36..61 -> ('A'.code + (it - 36)).toChar()
                62 -> '_'
                63 -> '-'
                else -> throw IllegalStateException()
            }
        }

        /**
         * Generates a random string that Web-URL safe and matches those of the Web-Safe Base64 encoding, so persists
         * only out of `a` to `z`, `A` to `Z`, `0` to `9`, `_` or `-`.
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