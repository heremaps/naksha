@file:Suppress("OPT_IN_USAGE")

package naksha.psql

import naksha.base.*
import naksha.geo.SpGeometry
import naksha.jbon.*
import naksha.model.*
import naksha.model.Naksha.NakshaCompanion.ADMIN_MAP
import naksha.model.Naksha.NakshaCompanion.COLLECTIONS_COL
import naksha.model.Naksha.NakshaCompanion.ADMIN_DICT_COL
import naksha.model.Naksha.NakshaCompanion.ADMIN_MAPS_COL
import naksha.model.Naksha.NakshaCompanion.ADMIN_TRANSACTIONS_COL
import naksha.model.NakshaError.NakshaErrorCompanion.ILLEGAL_ARGUMENT
import naksha.model.objects.NakshaFeature
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
         * The quoted identifier of the administration map to be used in queries.
         * @since 3.0.0
         */
        @JvmField
        @JsStatic
        val ADMIN_MAP_QUOTED = quoteIdent(ADMIN_MAP)

        /**
         * The quoted identifier of the collection in which transactions are stored.
         * @since 3.0.0
         */
        @JvmField
        @JsStatic
        val ADMIN_TRANSACTIONS_COL_QUOTED = quoteIdent(ADMIN_TRANSACTIONS_COL)

        /**
         * The quoted identifier of the virtual maps collection to be used in queries.
         * @since 3.0.0
         */
        @JvmField
        @JsStatic
        val ADMIN_MAPS_COL_QUOTED = quoteIdent(ADMIN_MAPS_COL)

        /**
         * The quoted identifier of the virtual collection in which the dictionaries are stored.
         * @since 3.0.0
         */
        @JvmField
        @JsStatic
        val ADMIN_DICT_COL_QUOTED = quoteIdent(ADMIN_DICT_COL)

        /**
         * The quoted identifier of the virtual collections collection to be used in queries.
         * @since 3.0.0
         */
        @JvmField
        @JsStatic
        val COLLECTIONS_COL_QUOTED = quoteIdent(COLLECTIONS_COL)

        /**
         * Array to query the partition name from the partition number (resolves 0 to "000", 1 to "001", ..., 255 to "256").
         * @see partitionSuffix
         */
        @JsStatic
        @JvmField
        val SUFFIX = Array(256) { if (it < 10) "00$it" else if (it < 100) "0$it" else "$it" }

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
         * Quotes a string literal, so a custom string. For PostgresQL database this means to replace all single quotes
         * (`'`) with two single quotes (`''`). This encloses the string with quotation characters, when needed.
         * @param parts the literal parts to merge and quote.
         * @return The quoted literal.
         * @since 3.0.0
         */
        @JsStatic
        @JvmStatic
        fun quoteLiteral(vararg parts: String): String {
            val quote = quote_literal(*parts)
            if (quote != null) return quote
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
         * Quotes an identifier, so a database internal name. For PostgresQL database this means to replace all double quotes
         * (`"`) with two double quotes (`""`). This encloses the string with quotation characters, when needed.
         * @param parts the identifier parts to merge and quote.
         * @return the quoted identifier.
         * @since 3.0.0
         */
        @JsStatic
        @JvmStatic
        fun quoteIdent(vararg parts: String): String {
            if (parts.isEmpty()) throw NakshaException(ILLEGAL_ARGUMENT, "The given parts must not be empty")
            val quote = quote_ident(*parts)
            if (quote != null) return quote
            var quoted = false
            val sb = StringBuilder()
            sb.append('"')
            for (part in parts) {
                for (c in part) {
                    when (c) {
                        in 'a'..'z', in 'A'..'Z', in '0'..'9', '_' -> sb.append(c)
                        '"' -> { quoted = true; sb.append('"').append('"') }
                        '\\' -> { quoted = true; sb.append('\\').append('\\') }
                        else -> { quoted = true; sb.append(c) }
                    }
                }
            }
            if (!quoted) return if (parts.size == 1) return parts[0] else sb.substring(1)
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
        fun partitionNumber(featureId: String): Int = PgPlatform.partitionNumber(featureId)

        /**
         * Returns the suffix of the partition based upon the given partition number, so maps 0 to "000", 1 to "001", ..., and 255 to "255".
         * @param number the partition number.
         * @return the partition suffix.
         */
        @JsStatic
        @JvmStatic
        fun partitionSuffix(number: Int): String = SUFFIX[number and 255]

        /**
         * Calculate a pseudo geo-reference-id from the given feature id.
         * @param id the feature id.
         * @return the pseudo geo-reference-id.
         */
        @JsStatic
        @JvmStatic
        fun geoHashFrom(id: String): String {
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

        /**
         * Decode the Naksha feature.
         * @param bytes the bytes to decode.
         * @param flags the codec flags.
         * @param dictManager the dictionary manager to use for decoding; if any.
         * @return the Naksha feature.
         * @since 3.0.0
         */
        @JsStatic
        @JvmStatic
        @Deprecated(
            message = "Please use Naksha class instead",
            replaceWith = ReplaceWith("Naksha.decodeFeature(bytes, flags, dictManager)"),
            level = DeprecationLevel.WARNING
        )
        fun decodeFeature(bytes: ByteArray?, flags: Flags, dictManager: IDictManager? = null): NakshaFeature? = Naksha.decodeFeature(bytes, flags, dictManager)

        /**
         * Encodes the given [NakshaFeature] into bytes.
         * @param feature the feature to encode.
         * @param flags the codec flags.
         * @param dict the dictionary to use for encoding; if any.
         * @return the encoded feature.
         * @since 3.0.0
         */
        @JsStatic
        @JvmStatic
        @Deprecated(
            message = "Please use Naksha class instead",
            replaceWith = ReplaceWith("Naksha.encodeFeature(feature, flags, dict)"),
            level = DeprecationLevel.WARNING
        )
        fun encodeFeature(feature: NakshaFeature?, flags: Flags, dict: JbDictionary? = null): ByteArray? = Naksha.encodeFeature(feature, flags, dict)

        /**
         * Decode the Naksha tags.
         * @param bytes the bytes to decode.
         * @param flags the codec flags.
         * @param dictManager the dictionary manager to use for decoding; if any.
         * @return the Naksha tags.
         * @since 3.0.0
         */
        @JsStatic
        @JvmStatic
        @Deprecated(
            message = "Please use Naksha class instead",
            replaceWith = ReplaceWith("Naksha.decodeTags(raw, bytes, flags, dictManager)"),
            level = DeprecationLevel.WARNING
        )
        fun decodeTags(bytes: ByteArray?, flags: Flags, dictManager: IDictManager? = null): TagMap? = Naksha.decodeTags(bytes, flags, dictManager)

        /**
         * Encodes the given tags into bytes.
         * @param tags the tags to encode.
         * @param flags the codec flags.
         * @param dict the dictionary to use for encoding; if any.
         * @return the encoded tags.
         * @since 3.0.0
         */
        @JsStatic
        @JvmStatic
        @Deprecated(
            message = "Please use Naksha class instead",
            replaceWith = ReplaceWith("Naksha.encodeTags(tags, flags, dict)"),
            level = DeprecationLevel.WARNING
        )
        fun encodeTags(tags: TagMap?, flags: Flags, dict: JbDictionary? = null): ByteArray? = Naksha.encodeTags(tags, flags, dict)

        /**
         * Decode a GeoJSON geometry from encoded bytes.
         * @param bytes the bytes to decode.
         * @param flags the codec flags.
         * @return the geometry.
         * @since 3.0.0
         */
        @JsStatic
        @JvmStatic
        @Deprecated(
            message = "Please use Naksha class instead",
            replaceWith = ReplaceWith("Naksha.decodeGeometry(bytes, flags)"),
            level = DeprecationLevel.WARNING
        )
        fun decodeGeometry(bytes: ByteArray?, flags: Flags): SpGeometry? = Naksha.decodeGeometry(bytes, flags)

        /**
         * Encodes the given GeoJSON geometry into bytes.
         * @param geometry the geometry to encode.
         * @param flags the codec flags.
         * @return the encoded GeoJSON geometry.
         * @since 3.0.0
         */
        @JsStatic
        @JvmStatic
        @Deprecated(
            message = "Please use Naksha class instead",
            replaceWith = ReplaceWith("Naksha.encodeGeometry(geometry, flags)"),
            level = DeprecationLevel.WARNING
        )
        fun encodeGeometry(geometry: SpGeometry?, flags: Flags): ByteArray? = Naksha.encodeGeometry(geometry, flags)
    }
}