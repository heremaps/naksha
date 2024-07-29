@file:Suppress("OPT_IN_USAGE")

package naksha.model

import naksha.model.NakshaError.NakshaErrorCompanion.ILLEGAL_ID
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmStatic

/**
 * Utility singleton.
 */
@JsExport
class NakshaUtil private constructor() {
    companion object NakshaUtilCompanion {
        /**
         * The collection identifier in which versions are stored.
         */
        const val VERSIONS_COL_ID = "naksha~versions"
        /**
         * The collection identifier in which the collections them-self are stored.
         */
        const val COLLECTIONS_COL_ID = "naksha~collections"
        /**
         * The collection identifier in which the dictionaries are stored.
         */
        const val DICTIONARIES_COL_ID = "naksha~dictionaries"

        /**
         * Tests if the given **id** is a valid identifier, so matches:
         *
         * `[a-z][a-z0-9_:-]{31}`
         *
         * **Beware**: Identifiers must not contain upper-case letters, because many storages does not make a difference between upper- and lower-cased letters.
         * @param id the identifier.
         * @return _true_ if the identifier is valid; _false_ otherwise.
         */
        @JsStatic
        @JvmStatic
        fun isValidId(id: String?): Boolean {
            if (id.isNullOrEmpty() || "naksha" == id || id.length > 32) return false
            var i = 0
            var c = id[i++]
            // First character must be a-z
            if (c.code < 'a'.code || c.code > 'z'.code) return false
            while (i < id.length) {
                c = id[i++]
                when (c.code) {
                    in 'a'.code..'z'.code -> continue
                    in '0'.code..'9'.code -> continue
                    '_'.code, ':'.code, '-'.code -> continue
                    else -> return false
                }
            }
            return true
        }

        /**
         * Tests if the given **id** is a valid identifier, otherwise throws an [NakshaError.ILLEGAL_ID].
         * @param id the identifier to test.
         */
        @JsStatic
        @JvmStatic
        fun verifyId(id: String?) {
            if (id.isNullOrEmpty() || "naksha" == id || id.length > 32) {
                throw NakshaException(ILLEGAL_ID, "The given identifier is null, empty or has more than 32 characters", id = id)
            }
            var i = 0
            var c = id[i++]
            if (c.code < 'a'.code || c.code > 'z'.code) {
                throw NakshaException(ILLEGAL_ID, "The first character must be a-z, but was $c", id = id)
            }
            while (i < id.length) {
                c = id[i++]
                when (c.code) {
                    in 'a'.code..'z'.code -> continue
                    in '0'.code..'9'.code -> continue
                    '_'.code, ':'.code, '-'.code -> continue
                    else -> throw NakshaException(ILLEGAL_ID, "Invalid character at index $i: '$c', expected [a-z0-9_:-]", id = id)
                }
            }
        }

        /**
         * Quotes a string literal, this means to replace all single quotes (`'`) with two single quotes (`''`). This encloses the string with quotation characters, when needed.
         * @param parts the literal parts to merge and quote.
         * @return The quoted literal.
         */
        @JsStatic
        @JvmStatic
        fun quoteLiteral(vararg parts: String): String {
            val sb = StringBuilder()
            sb.append("E'")
            for (part in parts) {
                for (c in part) {
                    when (c) {
                        '\'' -> sb.append('\'').append('\'')
                        '\\' -> sb.append('\\').append('\\')
                        else -> sb.append(c)
                    }
                }
            }
            sb.append('\'')
            return sb.toString()
        }

        /**
         * Quotes an identifier, this means to replace all double quotes (`"`) with two double quotes (`""`). This encloses the string with quotation characters, when needed.
         * @param parts the identifier parts to merge and quote.
         * @return the quoted identifier.
         */
        @JsStatic
        @JvmStatic
        fun quoteIdent(vararg parts: String): String {
            val sb = StringBuilder()
            sb.append('"')
            for (part in parts) {
                for (c in part) {
                    when (c) {
                        '"' -> sb.append('"').append('"')
                        '\\' -> sb.append('\\').append('\\')
                        else -> sb.append(c)
                    }
                }
            }
            sb.append('"')
            return sb.toString()
        }
    }
}