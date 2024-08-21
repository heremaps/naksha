@file:Suppress("OPT_IN_USAGE")

package naksha.model

import naksha.base.Int64
import naksha.base.Platform
import naksha.model.NakshaError.NakshaErrorCompanion.ILLEGAL_ID
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmStatic

/**
 * Utility singleton.
 */
@JsExport
class Naksha private constructor() {
    companion object NakshaCompanion {
        /**
         * The prefix for virtual (internal) collections.
         */
        const val VIRT_PREFIX = "naksha~"

        /**
         * The identifier of the virtual collection in which transactions are stored.
         */
        const val VIRT_TRANSACTIONS = "naksha~transactions"

        /**
         * The collection-number of the virtual collection in which transactions are stored.
         */
        @JvmStatic
        @JsStatic
        val VIRT_TRANSACTIONS_NUMBER = Int64(0)

        /**
         * The identifier of the virtual collection in which the collections them-self are stored.
         */
        const val VIRT_COLLECTIONS = "naksha~collections"

        /**
         * The identifier of the virtual collection quoted to be used in queries.
         */
        val VIRT_COLLECTIONS_QUOTED = quoteIdent(VIRT_COLLECTIONS)

        /**
         * The collection-number of the virtual collection in which the collections them-self are stored.
         */
        val VIRT_COLLECTIONS_NUMBER = Int64(0)

        /**
         * The identifier of the virtual collection in which the dictionaries are stored.
         */
        const val VIRT_DICTIONARIES = "naksha~dictionaries"

        /**
         * The collection-number of the virtual collection in which the dictionaries are stored.
         */
        @JvmStatic
        @JsStatic
        val VIRT_DICTIONARIES_NUMBER = Int64(2)

        /**
         * Fetch only the `id`, other parts only from cache.
         */
        const val FETCH_ID = "id"

        /**
         * Fetch the [metadata][Metadata], other parts only from cache.
         */
        const val FETCH_META = "meta"

        /**
         * Fetch all columns.
         */
        const val FETCH_ALL = "all"

        /**
         * Fetch all columns, do not use the cache.
         */
        const val FETCH_ALL_NO_CACHE = "all-no-cache"

        /**
         * Only load form cache.
         */
        const val FETCH_CACHE = "cache"

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
        fun partitionNumber(featureId: String?): Int = if (featureId == null) 0 else Platform.md5(featureId)[0].toInt() and 255

        /**
         * Tests if the given collection is an internal one.
         * @param collectionId the collection-id to test.
         * @return _true_ if this is an internal collection; _false_ otherwise.
         */
        @JsStatic
        @JvmStatic
        fun isInternal(collectionId: String?): Boolean = collectionId != null && collectionId.startsWith(VIRT_PREFIX)
    }
}