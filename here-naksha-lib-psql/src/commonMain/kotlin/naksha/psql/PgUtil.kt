@file:Suppress("OPT_IN_USAGE")

package naksha.psql

import naksha.base.Fnv1a32
import naksha.base.Fnv1a64
import naksha.base.Int64
import naksha.model.NakshaUtil
import naksha.psql.PgPlatform.PgPlatformCompanion.quote_ident
import naksha.psql.PgPlatform.PgPlatformCompanion.quote_literal
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmField
import kotlin.jvm.JvmStatic

/**
 * Utility functions, redirecting some calls to platform specific functions provided by [PgPlatform].
 */
@JsExport
class PgUtil private constructor() {
    companion object PgUtilCompanion {
        /**
         * Array to query the partition name from the partition number (resolves 0 to "000", 1 to "001", ..., 255 to "256"), usage like:
         *
         * `partitionName[partitionNumber(conn, "id", 16)]`
         *
         * @see partitionPosix
         */
        @JsStatic
        @JvmField
        val POSIX = Array(256) { if (it < 10) "00$it" else if (it < 100) "0$it" else "$it" }

        /**
         * Array to create a pseudo GeoHash, which is BASE-32 encoded.
         */
        @JvmField
        internal val BASE32 = arrayOf(
            '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'b', 'c', 'd', 'e', 'f', 'g',
            'h', 'j', 'k', 'm', 'n', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z'
        )

        /**
         * The lock-id for the transaction number sequence.
         */
        @JvmField
        internal val TXN_LOCK_ID = lockId("naksha_txn_seq")

        /**
         * Given as parameter for [PgStorage.initStorage], `override` can be set to _true_ to force the storage to reinstall, even when
         * the existing installed version of Naksha code is up-to-date.
         */
        const val OVERRIDE = "override"

        /**
         * Given as parameter for [PgStorage.initStorage], `options` can be a [PgOptions] object to be used for the initialization
         * connection (specific changed defaults to timeouts and locks).
         */
        const val OPTIONS = "options"

        /**
         * Given as parameter for [PgStorage.initStorage], `context` can be a [naksha.model.NakshaContext] to be used while doing the
         * initialization; only if [superuser][naksha.model.NakshaContext.su] is _true_, then a not uninitialized storage is installed.
         * This requires as well superuser rights in the PostgresQL database.
         */
        const val CONTEXT = "context"

        /**
         * Given as parameter for [PgStorage.initStorage], `id` used if the storage is uninitialized, initialize it with the given
         * storage identifier. If the storage is already initialized, reads the existing identifier and compares it with the given one.
         * If they do not match, throws an [IllegalStateException]. If not given a random new identifier is generated, when no identifier
         * yet exists. It is strongly recommended to provide the identifier.
         */
        const val ID = "id"

        /**
         * Quotes a string literal, so a custom string. For PostgresQL database this means to replace all single quotes
         * (`'`) with two single quotes (`''`). This encloses the string with quotation characters, when needed.
         * @param parts the literal parts to merge and quote.
         * @return The quoted literal.
         */
        @JsStatic
        @JvmStatic
        fun quoteLiteral(vararg parts: String): String = quote_literal(*parts) ?: NakshaUtil.quoteLiteral(*parts)

        /**
         * Quotes an identifier, so a database internal name. For PostgresQL database this means to replace all double quotes
         * (`"`) with two double quotes (`""`). This encloses the string with quotation characters, when needed.
         * @param parts the identifier parts to merge and quote.
         * @return the quoted identifier.
         */
        @JsStatic
        @JvmStatic
        fun quoteIdent(vararg parts: String): String = quote_ident(*parts) ?: NakshaUtil.quoteIdent(*parts)

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
        fun partitionNumber(featureId: String): Int = PgPlatform.partitionNumber(featureId)

        /**
         * Returns the posix of the partition based upon the given partition number, so maps 0 to "000", 1 to "001", ..., and 255 to "255".
         * @param number the partition number.
         * @return the partition posix.
         */
        @JsStatic
        @JvmStatic
        fun partitionPosix(number: Int): String = POSIX[number and 255]

        /**
         * Calculate a pseudo geo-reference-id from the given feature id.
         * @param id the feature id.
         * @return the pseudo geo-reference-id.
         */
        @JsStatic
        @JvmStatic
        fun gridFromId(id: String): String {
            val BASE32 = PgUtil.BASE32
            val sb = StringBuilder()
            var hash = Fnv1a32.string(Fnv1a32.start(), id)
            var i = 0
            sb.append(BASE32[id[0].code and 31])
            while (i++ < 6) {
                val b32 = hash and 31
                sb.append(BASE32[b32])
                hash = hash ushr 5
            }
            hash = Fnv1a32.stringReverse(Fnv1a32.start(), id)
            i = 0
            sb.append(BASE32[id[0].code and 31])
            while (i++ < 6) {
                val b32 = hash and 31
                sb.append(BASE32[b32])
                hash = hash ushr 5
            }
            return sb.toString()
        }

        /**
         * Returns the lock-id for the given name.
         * @param name the name to query the lock-id for.
         * @return the 64-bit FNV1a hash.
         */
        @JsStatic
        @JvmStatic
        fun lockId(name: String): Int64 = Fnv1a64.string(Fnv1a64.start(), name)
    }
}