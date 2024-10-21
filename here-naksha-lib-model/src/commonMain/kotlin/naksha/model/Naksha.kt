@file:Suppress("OPT_IN_USAGE")

package naksha.model

import naksha.base.Int64
import naksha.base.Platform
import naksha.base.Platform.PlatformCompanion.fromJSON
import naksha.base.Platform.PlatformCompanion.gzipDeflate
import naksha.base.Platform.PlatformCompanion.gzipInflate
import naksha.base.Platform.PlatformCompanion.toJSON
import naksha.base.PlatformMap
import naksha.geo.GeoUtil
import naksha.geo.GeoUtil.GeoUtil_C.fromEWKB
import naksha.geo.GeoUtil.GeoUtil_C.fromTWKB
import naksha.geo.GeoUtil.GeoUtil_C.fromWKB
import naksha.geo.GeoUtil.GeoUtil_C.toEWKB
import naksha.geo.GeoUtil.GeoUtil_C.toTWKB
import naksha.geo.GeoUtil.GeoUtil_C.toWKB
import naksha.geo.SpGeometry
import naksha.jbon.*
import naksha.model.FeatureEncoding.FeatureEncoding_C.JBON
import naksha.model.FeatureEncoding.FeatureEncoding_C.JBON_GZIP
import naksha.model.FeatureEncoding.FeatureEncoding_C.JSON
import naksha.model.FeatureEncoding.FeatureEncoding_C.JSON_GZIP
import naksha.model.GeoEncoding.GeoEncoding_C.EWKB
import naksha.model.GeoEncoding.GeoEncoding_C.EWKB_GZIP
import naksha.model.GeoEncoding.GeoEncoding_C.GEO_JSON
import naksha.model.GeoEncoding.GeoEncoding_C.GEO_JSON_GZIP
import naksha.model.GeoEncoding.GeoEncoding_C.TWKB
import naksha.model.GeoEncoding.GeoEncoding_C.TWKB_GZIP
import naksha.model.GeoEncoding.GeoEncoding_C.WKB
import naksha.model.GeoEncoding.GeoEncoding_C.WKB_GZIP
import naksha.model.NakshaError.NakshaErrorCompanion.ILLEGAL_ARGUMENT
import naksha.model.NakshaError.NakshaErrorCompanion.ILLEGAL_ID
import naksha.model.objects.NakshaFeature
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmField
import kotlin.jvm.JvmStatic

/**
 * Utility singleton.
 * @since 3.0.0
 */
@JsExport
class Naksha private constructor() {
    companion object NakshaCompanion {
        /**
         * The prefix for virtual (internal) collections.
         * @since 3.0.0
         */
        const val VIRT_PREFIX = "naksha~"

        /**
         * The identifier of the virtual collection in which transactions are stored.
         * @since 3.0.0
         */
        const val VIRT_TRANSACTIONS = "naksha~transactions"

        /**
         * The quoted identifier of the virtual collection in which transactions are stored.
         * @since 3.0.0
         */
        @JvmField
        @JsStatic
        val VIRT_TRANSACTIONS_QUOTED = quoteIdent(VIRT_TRANSACTIONS)

        /**
         * The collection-number of the virtual collection in which transactions are stored.
         * @since 3.0.0
         */
        const val VIRT_TRANSACTIONS_NUMBER = 0

        /**
         * The identifier of the virtual collection in which the collections them-self are stored.
         * @since 3.0.0
         */
        const val VIRT_COLLECTIONS = "naksha~collections"

        /**
         * The quoted identifier of the virtual collections collection to be used in queries.
         * @since 3.0.0
         */
        @JvmField
        @JsStatic
        val VIRT_COLLECTIONS_QUOTED = quoteIdent(VIRT_COLLECTIONS)

        /**
         * The collection-number of the virtual collection in which the collections them-self are stored.
         * @since 3.0.0
         */
        const val VIRT_COLLECTIONS_NUMBER = 1

        /**
         * The identifier of the virtual collection in which the dictionaries are stored.
         * @since 3.0.0
         */
        const val VIRT_DICTIONARIES = "naksha~dictionaries"

        /**
         * The collection-number of the virtual collection in which the dictionaries are stored.
         * @since 3.0.0
         */
        const val VIRT_DICTIONARIES_NUMBER = 2

        /**
         * The quoted identifier of the virtual collection in which the dictionaries are stored.
         * @since 3.0.0
         */
        @JvmField
        @JsStatic
        val VIRT_DICTIONARIES_QUOTED = quoteIdent(VIRT_DICTIONARIES)

        /**
         * Tests if the given **id** is a valid identifier, so matches:
         *
         * `[a-z][a-z0-9_:-]{31}`
         *
         * **Beware**: Identifiers must not contain upper-case letters, because many storages does not make a difference between upper- and lower-cased letters.
         * @param id the identifier.
         * @return _true_ if the identifier is valid; _false_ otherwise.
         * @since 3.0.0
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
         * @return the given identifier, tested.
         * @since 3.0.0
         */
        @JsStatic
        @JvmStatic
        fun verifyId(id: String?): String {
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
            return id
        }

        /**
         * Quotes a string literal, this means to replace all single quotes (`'`) with two single quotes (`''`). This encloses the string with quotation characters, when needed.
         * @param parts the literal parts to merge and quote.
         * @return The quoted literal.
         * @since 3.0.0
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
         * @since 3.0.0
         */
        @JsStatic
        @JvmStatic
        fun quoteIdent(vararg parts: String): String {
            if (parts.isEmpty()) throw NakshaException(ILLEGAL_ARGUMENT, "The given parts must not be empty")
            var quoted = false
            val sb = StringBuilder()
            sb.append('"')
            for (part in parts) {
                for (c in part) {
                    when (c) {
                        in 'a'..'z', in 'A'..'Z', in '0'..'9','_' -> sb.append(c)
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
         * @since 3.0.0
         */
        @JsStatic
        @JvmStatic
        fun partitionNumber(featureId: String?): Int = if (featureId == null) 0 else Platform.md5(featureId)[0].toInt() and 255

        /**
         * Tests if the given collection is an internal one.
         * @param collectionId the collection-id to test.
         * @return _true_ if this is an internal collection; _false_ otherwise.
         * @since 3.0.0
         */
        @JsStatic
        @JvmStatic
        fun isInternal(collectionId: String?): Boolean = collectionId != null && collectionId.startsWith(VIRT_PREFIX)

        /**
         * Decode the [Naksha feature][NakshaFeature] from the given [tuple][Tuple].
         *
         * This method will query the [NakshaCache] to get the [dictionary-manager][IDictManager].
         * @param tuple the tuple to decode.
         * @return the Naksha feature, _null_ if decoding failed or _null_ was given.
         * @since 3.0.0
         */
        @JsStatic
        @JvmStatic
        fun decodeTuple(tuple: Tuple): NakshaFeature {
            val sn = tuple.storageNumber
            val meta = tuple.meta
            val dictManager = NakshaCache.getDictManager(sn) ?: NakshaCache.getStorage(sn)
            val feature = decodeFeature(tuple.feature, meta.flags, dictManager) ?: NakshaFeature()
            feature.properties.xyz = XyzNs.fromMetadata(meta)
            val xyz = feature.properties.xyz
            val tags = tuple.tags
            if (tags != null) xyz.tags = decodeTags(tuple.tags, meta.flags, dictManager)?.toTagList() ?: TagList()
            val geo = tuple.geo
            if (geo != null) feature.geometry = decodeGeometry(geo, meta.flags)
            val attachment = tuple.attachment
            if (attachment != null) feature.attachment = attachment
            return feature
        }

        /**
         * Encode the given [NakshaFeature] into a [Tuple].
         *
         * The best way to use the [collection][ICollection] into which the tuple should be inserted as [dictionary-manager][IDictManager], the next best thing is use the [map][IMap] into which it should be stored, eventually using the [storage][IStorage] is better than nothing, aka _null_ (no [dictionary-manager][IDictManager]).
         * @param feature the feature to encode.
         * @param dictManager the [dictionary-manager] to use to encode the feature.
         * @param flags the encoding flags, should be [DEFAULT_FLAGS].
         * @return the encoded [Tuple].
         * @since 3.0.0
         */
        @JsStatic
        @JvmStatic
        fun encodeTuple(feature: NakshaFeature, dictManager: IDictManager?, flags: Flags = DEFAULT_FLAGS): Tuple {
            val xyz = feature.properties.xyz
            val meta = Metadata.fromXyzNs(xyz) ?: Metadata.UNDEFINED
            val dict = dictManager?.getEncodingDictionary(feature)
            val featureBytes = encodeFeature(feature, flags, dict)
            val geoBytes = encodeGeometry(feature.geometry, flags)
            val refPoint = encodeGeometry(feature.referencePoint, TWKB)
            val tagsBytes = encodeTags(xyz.tags.toTagMap(), flags, dict)
            return Tuple(meta, featureBytes, geoBytes, refPoint, tagsBytes, feature.attachment, IS_COMPLETE)
        }

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
        fun decodeFeature(bytes: ByteArray?, flags: Flags, dictManager: IDictManager? = null): NakshaFeature? {
            if (bytes == null || bytes.isEmpty()) return null
            var raw = bytes
            if (flags.featureGzip()) raw = gzipInflate(bytes)
            val encoding = flags.featureEncoding()
            if (encoding == JBON || encoding == JBON_GZIP) {
                val decoder = JbFeatureDecoder(dictManager)
                decoder.mapBytes(raw)
                return decoder.toAnyObject().proxy(NakshaFeature::class)
            }
            if (encoding == JSON || encoding == JSON_GZIP) {
                val decoded = fromJSON(bytes.decodeToString())
                if (decoded is PlatformMap) return decoded.proxy(NakshaFeature::class)
            }
            return null
        }

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
        fun encodeFeature(feature: NakshaFeature?, flags: Flags, dict: JbDictionary? = null): ByteArray? {
            if (feature == null) return null
            val encoding = flags.featureEncoding()
            var byteArray: ByteArray? = null
            if (encoding == JSON || encoding == JSON_GZIP) {
                val encoded = toJSON(feature)
                byteArray = encoded.encodeToByteArray()
            } else if (encoding == JBON || encoding == JBON_GZIP) {
                val encoder = JbEncoder(dict)
                byteArray = encoder.buildFeatureFromMap(feature)
            }
            if (flags.featureGzip() && byteArray != null) byteArray = Platform.gzipDeflate(byteArray)
            return byteArray
        }

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
        fun decodeTags(bytes: ByteArray?, flags: Flags, dictManager: IDictManager? = null): TagMap? {
            if (bytes == null || bytes.isEmpty()) return null
            var raw = bytes
            if (flags.tagsGzip()) raw = gzipInflate(bytes)
            val encoding = flags.tagsEncoding()
            if (encoding == TagsEncoding.JBON || encoding == TagsEncoding.JBON_GZIP) {
                val decoder = JbFeatureDecoder(dictManager)
                decoder.mapBytes(raw)
                return decoder.toAnyObject().proxy(TagMap::class)
            }
            if (encoding == TagsEncoding.JSON || encoding == TagsEncoding.JSON_GZIP) {
                val decoded = fromJSON(bytes.decodeToString())
                if (decoded is PlatformMap) return decoded.proxy(TagMap::class)
            }
            return null
        }

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
        fun encodeTags(tags: TagMap?, flags: Flags, dict: JbDictionary? = null): ByteArray? {
            if (tags == null) return null
            val encoding = flags.tagsEncoding()
            var byteArray: ByteArray? = null
            if (encoding == TagsEncoding.JSON || encoding == TagsEncoding.JSON_GZIP) {
                val encoded = toJSON(tags)
                byteArray = encoded.encodeToByteArray()
            } else if (encoding == TagsEncoding.JBON || encoding == TagsEncoding.JBON_GZIP) {
                val encoder = JbEncoder(dict)
                encoder.encodeMap(tags)
                byteArray = encoder.buildFeature(null, FEATURE_VARIANT_TAGS)
            }
            if (flags.tagsGzip() && byteArray != null) byteArray = gzipDeflate(byteArray)
            return byteArray
        }

        /**
         * Decode a GeoJSON geometry from encoded bytes.
         * @param bytes the bytes to decode.
         * @param flags the codec flags.
         * @return the geometry.
         * @since 3.0.0
         */
        @JsStatic
        @JvmStatic
        fun decodeGeometry(bytes: ByteArray?, flags: Flags): SpGeometry? {
            if (bytes == null || bytes.isEmpty()) return null
            val encoding = flags.geoEncoding()
            val rawBytes = if (encoding.geoGzip()) gzipInflate(bytes) else bytes
            return when(encoding) {
                TWKB, TWKB_GZIP -> fromTWKB(rawBytes)
                WKB, WKB_GZIP -> fromWKB(rawBytes)
                EWKB, EWKB_GZIP -> fromEWKB(rawBytes)
                GEO_JSON, GEO_JSON_GZIP -> (fromJSON(rawBytes.decodeToString()) as PlatformMap).proxy(SpGeometry::class)
                else -> throw NakshaException(ILLEGAL_ARGUMENT, "Unknown geometry encoding")
            }

        }

        /**
         * Encodes the given GeoJSON geometry into bytes.
         * @param geometry the geometry to encode.
         * @param flags the codec flags.
         * @return the encoded GeoJSON geometry.
         * @since 3.0.0
         */
        @JsStatic
        @JvmStatic
        fun encodeGeometry(geometry: SpGeometry?, flags: Flags): ByteArray? {
            if (geometry == null) return null
            val encoding = flags.geoEncoding()
            val bytes = when(encoding) {
                TWKB, TWKB_GZIP -> toTWKB(geometry)
                WKB, WKB_GZIP -> toWKB(geometry)
                EWKB, EWKB_GZIP -> toEWKB(geometry)
                GEO_JSON, GEO_JSON_GZIP -> toJSON(geometry).encodeToByteArray()
                else -> throw NakshaException(ILLEGAL_ARGUMENT, "Unknown geometry encoding")
            }
            return if (encoding.geoGzip() && bytes != null) gzipDeflate(bytes) else bytes
        }
    }
}