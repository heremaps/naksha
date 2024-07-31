@file:Suppress("OPT_IN_USAGE")

package naksha.model

import naksha.base.CodePoints
import naksha.base.Platform
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
         * The prefix for virtual (internal) collections.
         */
        const val VIRT_PREFIX = "naksha~"

        /**
         * The identifier of the virtual collection in which transactions are stored.
         */
        const val VIRT_TRANSACTIONS = "naksha~transactions"

        /**
         * The identifier of the virtual collection in which the collections them-self are stored.
         */
        const val VIRT_COLLECTIONS = "naksha~collections"

        /**
         * The identifier of the virtual collection in which the dictionaries are stored.
         */
        const val VIRT_DICTIONARIES = "naksha~dictionaries"

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

        /**
         * Calculates the partition number between 0 and 255. This is the unsigned value of the first byte of the MD5 hash above the
         * given feature-id. When there are less than 256 partitions, the value must be divided by the number of partitions, and the rest
         * addresses the partition, for example for 4 partitions do `partitionNumber(id) % 4`, what will be a value between 0 and 3.
         *
         * @param featureId the feature id.
         * @return the partition number of the feature, a value between 0 and 255.
         */
        @JsStatic
        @JvmStatic
        fun partitionNumber(featureId: String): Int = Platform.md5(featureId)[0].toInt() and 255

    }
}