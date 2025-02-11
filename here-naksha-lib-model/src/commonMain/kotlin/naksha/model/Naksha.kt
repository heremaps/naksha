@file:Suppress("OPT_IN_USAGE")

package naksha.model

import naksha.base.*
import naksha.base.Platform.PlatformCompanion.fromJSON
import naksha.base.Platform.PlatformCompanion.gzipDeflate
import naksha.base.Platform.PlatformCompanion.gzipInflate
import naksha.base.Platform.PlatformCompanion.toJSON
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
import naksha.model.NakshaError.NakshaErrorCompanion.ILLEGAL_STATE
import naksha.model.NakshaError.NakshaErrorCompanion.STORAGE_NOT_FOUND
import naksha.model.NakshaVersion.Companion.LATEST
import naksha.model.objects.NakshaFeature
import naksha.model.objects.NakshaProperties
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.js.JsStatic
import kotlin.jvm.JvmField
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic

/**
 * Utility singleton of the Naksha `lib-models`.
 * @since 3.0.0
 * @see IStorage
 */
@JsExport
class Naksha private constructor() {
    companion object NakshaCompanion {
        /**
         * The prefix for administrative collections.
         * @since 3.0.0
         */
        const val ADMIN_PREFIX = "naksha~"

        /**
         * The identifier of the administration map.
         * @since 3.0.0
         */
        const val ADMIN_MAP = "naksha~admin"

        /**
         * The number of the administration map.
         * @since 3.0.0
         */
        const val ADMIN_MAP_NUMBER = 0

        /**
         * The identifier of the collection in which transactions are stored, located in the [admin-map][ADMIN_MAP].
         * @since 3.0.0
         * @see [naksha.model.objects.NakshaTransaction]
         */
        const val TRANSACTIONS_COL = "naksha~transactions"

        /**
         * The collection-number of the collection in which transactions are stored, located in the [admin-map][ADMIN_MAP].
         * @since 3.0.0
         */
        const val TRANSACTIONS_COL_NUMBER = 1

        /**
         * The identifier of the collection in which maps are stored, located only within the [admin-map][ADMIN_MAP].
         * @see [naksha.model.objects.NakshaMap]
         * @since 3.0.0
         */
        const val MAPS_COL = "naksha~maps"

        /**
         * The collection-number of the collection in which maps are stored, located in the [admin-map][ADMIN_MAP].
         * @since 3.0.0
         */
        const val MAPS_COL_NUMBER = 2

        /**
         * The identifier of the collection in which dictionaries are stored, located in the [admin-map][ADMIN_MAP].
         * @since 3.0.0
         */
        const val DICTIONARIES_COL = "naksha~dictionaries"

        /**
         * The collection-number of the collection in which dictionaries are stored, located in the [admin-map][ADMIN_MAP].
         * @since 3.0.0
         */
        const val DICTIONARIES_COL_NUMBER = 3

        /**
         * The identifier of the virtual collection in which the collections of a map are managed, located within each map.
         * @since 3.0.0
         */
        const val COLLECTIONS_COL = "naksha~collections"

        /**
         * The collection-number of the virtual collection in which the collections of a map are managed, located within each map.
         * @since 3.0.0
         */
        const val COLLECTIONS_COL_NUMBER = 0

        /**
         * The maximum length of identifiers.
         * @since 3.0.0
         */
        const val MAX_ID_LENGTH = 42 // The answer to everything ;-)

        /**
         * Default test configuration for the local test storage.
         *
         * Every implementation handle this specific configuration special internally. Some storages (like `lib-psql`) will start a local docker container, other may not need it (for example `lib-sqlite`), but create some temporary files. Technically, the idea of this shared pseudo configuration is to allow each storage to come up with some easy to use local test setup.
         *
         * The only requirement for this configuration it, that there is docker installed locally on the system.
         * @since 3.0.0
         */
        @JvmStatic
        @JsStatic
        val LOCAL_TEST_STORAGE_CONFIG: StorageConfig = (fromJSON("""{"id":"local_test"}""") as PlatformMap).proxy(StorageConfig::class)

        /**
         * Tests if the given **id** is a valid identifier, so matches:
         *
         * `[a-z][a-z0-9_:-]{31}`
         *
         * **Beware**: Identifiers must not contain upper-case letters, because many storages does not make a difference between upper- and lower-cased letters.
         * @param id the identifier.
         * @return _true_ if the identifier is valid; _false_ otherwise.
         * @since 3.0.0
         * @see [verifyId]
         */
        @JsStatic
        @JvmStatic
        fun isValidId(id: String?): Boolean {
            if (id.isNullOrEmpty() || "naksha" == id || id.length > MAX_ID_LENGTH) return false
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
         * Tests if the given **id** is a valid identifier, so matches:
         *
         * `[a-z][a-z0-9_:-]{31}`
         *
         * If the given identifier is invalid, the methods throws [NakshaError.ILLEGAL_ID].
         * @param id the identifier to test.
         * @return the given identifier, tested.
         * @since 3.0.0
         * @see [isValidId]
         */
        @JsStatic
        @JvmStatic
        fun verifyId(id: String?): String {
            if (id.isNullOrEmpty() || "naksha" == id || id.length > MAX_ID_LENGTH) {
                throw NakshaException(ILLEGAL_ID, "The given identifier is null, empty or has more than 32 characters")
            }
            var i = 0
            var c = id[i++]
            if (c.code < 'a'.code || c.code > 'z'.code) {
                throw NakshaException(ILLEGAL_ID, "The first character must be a-z, but was $c")
            }
            while (i < id.length) {
                c = id[i++]
                when (c.code) {
                    in 'a'.code..'z'.code -> continue
                    in '0'.code..'9'.code -> continue
                    '_'.code, ':'.code, '-'.code -> continue
                    else -> throw NakshaException(ILLEGAL_ID, "Invalid character at index $i: '$c', expected [a-z0-9_:-]")
                }
            }
            return id
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
         * Quotes an identifier, this means to replace all double quotes (`"`) with two double quotes (`""`), but only if necessary, so if not being `a-zA-Z0-9_`. This encloses the string with quotation characters, when needed.
         * @param parts the identifier parts to merge and quote.
         * @return the quoted identifier.
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
         * @since 3.0.0
         */
        @JsStatic
        @JvmStatic
        fun partitionNumber(featureId: String?): Int = if (featureId == null) 0 else Platform.md5(featureId)[0].toInt() and 255

        /**
         * Helper method to calculate a valid storage-number from a given storage-id.
         *
         * @param storageId the storage-id.
         * @return the storage-number.
         */
        @JsStatic
        @JvmStatic
        fun storageNumberByHash(storageId: String): Int64 {
            val hash = Platform.md5(storageId)
            val view = Binary(Platform.newDataView(hash))
            return view.getInt64(8) and Int64(0x7fff_ffff_ffff_ffff)
        }

        /**
         * Tests if the given identifier is an internal one.
         * @param id the identifier to test.
         * @return _true_ if this is an internal identifier; _false_ otherwise.
         * @since 3.0.0
         */
        @JsStatic
        @JvmStatic
        fun isInternal(id: String?): Boolean = id != null && id.startsWith(ADMIN_PREFIX)

        /**
         * Decode the [Naksha feature][NakshaFeature] from the given [tuple][Tuple].
         *
         * This method will query the [cache] to get the [dictionary-manager][IDictManager].
         * - Throws [NakshaError.DICT_MANAGER_NOT_FOUND], if a [dictionary-manager][IDictManager] is needed to decode the [Tuple], but not available in [cache].
         * @param tuple the tuple to decode.
         * @param dictionaryReader the dictionary reader to use, if _null_, then the storage or cache are used.
         * @return the Naksha feature, _null_ if decoding failed or _null_ was given.
         * @since 3.0.0
         */
        @JsStatic
        @JvmStatic
        @JvmOverloads
        fun decodeTuple(tuple: Tuple, dictionaryReader: IDictReader? = null): NakshaFeature {
            val sn = tuple.storageNumber
            val meta = tuple.meta
            val dictReader = dictionaryReader ?: getStorageByNumber(sn) ?: cache
            val feature = decodeFeature(tuple.feature, meta.flags, dictReader) ?: NakshaFeature()
            feature.properties.xyz = XyzNs.fromMetadata(meta)
            val xyz = feature.properties.xyz
            val tags = tuple.tags
            if (tags != null) xyz.tags = decodeTags(tuple.tags, meta.flags, dictReader)?.toTagList() ?: TagList()
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
         * @param dictionary the [dictionary][IDict] to use to encode the feature; _null_ if encoding should be done storage agnostic.
         * @param flags the encoding flags or _null_, if [DEFAULT_FLAGS] should be used.
         * @return the encoded [Tuple].
         * @since 3.0.0
         */
        @JsStatic
        @JvmStatic
        @JvmOverloads
        fun encodeTuple(feature: NakshaFeature, dictionary: IDict? = null, flags: Flags? = null): Tuple {
            val xyz = feature.properties.xyz
            val meta = Metadata.fromXyzNs(xyz) ?: Metadata.UNDEFINED
            val flagsOrDefault = flags ?: DEFAULT_FLAGS
            val featureBytes = encodeFeature(feature, flagsOrDefault, dictionary)
            val geoBytes = encodeGeometry(feature.geometry, flagsOrDefault)
            val refPoint = encodeGeometry(feature.referencePoint, TWKB)
            val tagsBytes = encodeTags(xyz.tags.toTagMap(), flagsOrDefault, dictionary)
            return Tuple(meta, featureBytes, geoBytes, refPoint, tagsBytes, feature.attachment, true)
        }

        /**
         * Encode the given [NakshaFeature] into a [Tuple] for the given [storage][IStorage].
         *
         * @param feature the feature to encode.
         * @param storage the [storage][IStorage] for which to encode the feature.
         * @return the encoded [Tuple].
         * @since 3.0.0
         */
        @JsStatic
        @JvmStatic
        @JsName("encodeTupleForStorage")
        fun encodeTuple(feature: NakshaFeature, storage: IStorage): Tuple {
            val xyz = feature.properties.xyz
            val meta = Metadata.fromXyzNs(xyz) ?: Metadata.UNDEFINED
            val dict = storage.getEncodingDictionary(feature)
            val flags = storage.getEncodingFlags(feature)
            val featureBytes = encodeFeature(feature, flags, dict)
            val geoBytes = encodeGeometry(feature.geometry, flags)
            val refPoint = encodeGeometry(feature.referencePoint, TWKB)
            val tagsBytes = encodeTags(xyz.tags.toTagMap(), flags, dict)
            return Tuple(meta, featureBytes, geoBytes, refPoint, tagsBytes, feature.attachment, true)
        }

        /**
         * Encodes the given [NakshaFeature] into bytes, skipping over the [geometry][NakshaFeature.geometry], and the [XYZ-namespace][XyzNs].
         * @param feature the feature to encode.
         * @param flags the codec flags.
         * @param dict the dictionary to use for encoding; if any.
         * @return the encoded feature.
         * @since 3.0.0
         */
        @JsStatic
        @JvmStatic
        fun encodeFeature(feature: NakshaFeature?, flags: Flags, dict: IDict?): ByteArray? {
            if (feature.isNullOrEmpty()) return null
            val encoding = flags.featureEncoding()
            var byteArray: ByteArray? = null
            if (encoding == JSON || encoding == JSON_GZIP) {
                // We do not want to encode geometry.
                val f = feature.copy<NakshaFeature>(false)
                f.removeRaw(NakshaFeature.GEOMETRY)
                // We do not want to encode properties.@ns:com:here:xyz.
                val p = feature.properties.copy<NakshaProperties>(false)
                p.removeRaw(NakshaProperties.XYZ_KEY)
                val encoded = toJSON(f)
                byteArray = encoded.encodeToByteArray()
            } else if (encoding == JBON || encoding == JBON_GZIP) {
                val encoder = JbEncoder(dict)
                byteArray = encoder.buildFeatureFromMap(feature)
            }
            if (flags.featureGzip() && byteArray != null) byteArray = gzipDeflate(byteArray)
            return byteArray
        }

        /**
         * Decode the Naksha feature.
         * @param bytes the bytes to decode.
         * @param flags the codec flags.
         * @param dictReader the dictionary manager to use for decoding; if any.
         * @return the Naksha feature.
         * @since 3.0.0
         */
        @JsStatic
        @JvmStatic
        fun decodeFeature(bytes: ByteArray?, flags: Flags, dictReader: IDictReader?): NakshaFeature? {
            if (bytes == null || bytes.isEmpty()) return null
            var raw = bytes
            if (flags.featureGzip()) raw = gzipInflate(bytes)
            val encoding = flags.featureEncoding()
            if (encoding == JBON || encoding == JBON_GZIP) {
                val decoder = JbFeatureDecoder(dictReader)
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
         * Decode the Naksha tags.
         * @param bytes the bytes to decode.
         * @param flags the codec flags.
         * @param dictReader the dictionary manager to use for decoding; if any.
         * @return the Naksha tags.
         * @since 3.0.0
         */
        @JsStatic
        @JvmStatic
        fun decodeTags(bytes: ByteArray?, flags: Flags, dictReader: IDictReader?): TagMap? {
            if (bytes == null || bytes.isEmpty()) return null
            var raw = bytes
            if (flags.tagsGzip()) raw = gzipInflate(bytes)
            val encoding = flags.tagsEncoding()
            if (encoding == TagsEncoding.JBON || encoding == TagsEncoding.JBON_GZIP) {
                val decoder = JbFeatureDecoder(dictReader)
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
        fun encodeTags(tags: TagMap?, flags: Flags, dict: IDict?): ByteArray? {
            if (tags.isNullOrEmpty()) return null
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

        /**
         * A lock that is used to modify static values atomically.
         * @since 3.0.0
         */
        @JvmField
        internal val lock = Platform.newLock()

        /**
         * All registered storages by [storage-number][IStorage.number].
         * @since 3.0.0
         */
        @JvmField
        internal val storagesByNumber = AtomicMap<Int64, AbstractStorage<*>>()

        /**
         * All registered storages by [storage-id][IStorage.id].
         * @since 3.0.0
         */
        @JvmField
        internal val storagesById = AtomicMap<String, AbstractStorage<*>>()

        /**
         * Returns a list of all currently registered storages.
         * @since 3.0.0
         */
        @JvmStatic
        @JsStatic
        fun listStorages(): List<IStorage> = storagesByNumber.map { (_, storage) -> storage }

         /**
         * Returns the storage with the given configuration.
         * @param config the configuration.
         * @return the storage, if available.
         */
        @JvmStatic
        @JsStatic
        fun getStorage(config: StorageConfig): IStorage? {
            val s = storagesByNumber[config.number] ?: return null
            val s2 = storagesById[config.id] ?: return null
            return if (s!==s2 || s.config != config) null else s
         }

        /**
         * Returns the storage with the given identifier.
         * @param storageId the storage-id.
         * @return the storage, if added to cache.
         */
        @JvmStatic
        @JsStatic
        fun getStorageById(storageId: String): IStorage? = storagesById[storageId]

        /**
         * Returns the storage with the given number.
         * @param storageNumber the storage-number.
         * @return the storage, if added to cache.
         */
        @JvmStatic
        @JsStatic
        fun getStorageByNumber(storageNumber: Int64): IStorage? = storagesByNumber[storageNumber]

        /**
         * Returns the storage for the given tuple-number.
         * @param tupleNumber the tuple-number.
         * @return the storage, if added to cache.
         */
        @JvmStatic
        @JsStatic
        fun getStorageByTupleNumber(tupleNumber: TupleNumber): IStorage? = storagesByNumber[tupleNumber.storageNumber]

        /**
         * Set up the storage with the given configuration, enforces an [initStorage][AbstractStorage.initStorage] invocation that is forced to `create` or `upgrade` the storage.
         *
         * - If no such storage exists, create it, calling [initStorage][AbstractStorage.initStorage] with `create` and `upgrade` set to `true`.
         * - If the same storage, but with another configuration, exists, shutdown the existing one, and gracefully replace it with a new instance, which is initialized via [initStorage][AbstractStorage.initStorage] with `create` and `upgrade` set to `true`.
         * - If the same storage exists already, invoke [initStorage][AbstractStorage.initStorage] again with `create` and `upgrade` set to `true`.
         * - Throws [NakshaError.ILLEGAL_STATE] if the given **storage-number** and **storage-id** are currently allocated to two different storages.
         * - Throws [NakshaError.FORBIDDEN], if not called as super-user.
         * - Throws [NakshaError.INITIALIZATION_FAILED], if the initialization failed.
         * - Throws [NakshaError.STORAGE_ID_MISMATCH], if the existing _storage-id_ and/or _storage-number_ of the data does not match the given one.
         * @param config the storage configuration.
         * @return the storage.
         */
        @JvmStatic
        @JsStatic
        fun setupStorage(config: StorageConfig): IStorage = _useStorage(config, true)

        /**
         * Returns the storage with the given configuration.
         *
         * - If the same storage with the same configuration exists, just returns the existing one.
         * - If no such storage exists, create it, and invoke [initStorage][AbstractStorage.initStorage].
         * - If the same storage, but with another configuration, exists, shutdown the existing storage, and replace it gracefully with a new instance using the updated configuration, invoking [initStorage][AbstractStorage.initStorage].
         * - Throws [NakshaError.ILLEGAL_STATE] if the given **storage-number** and **storage-id** are currently allocated to two different storages.
         * - Throws [NakshaError.FORBIDDEN], if not called as super-user, but super-user rights are necessary (only needed to create or upgrade storages).
         * - Throws [NakshaError.INITIALIZATION_FAILED], if the initialization failed.
         * - Throws [NakshaError.STORAGE_ID_MISMATCH], if the existing _storage-id_ and/or _storage-number_ of the data does not match the given one.
         * @param config the storage configuration.
         * @return the storage.
         */
        @JvmStatic
        @JsStatic
        fun useStorage(config: StorageConfig): IStorage = _useStorage(config, false)

        private fun _useStorage(config: StorageConfig, forceCreateOrUpgrade: Boolean): IStorage {
            val createOrUpdate = if (forceCreateOrUpgrade) true else null
            val s = storagesByNumber[config.number]
            val s2 = storagesById[config.id]
            if (s !== s2) {
                throw NakshaException(
                    ILLEGAL_ARGUMENT,
                    "The storage-id (${config.id}) and -number (${config.number}) belong to different storages")
            }
            if (s != null && s.config == config) {
                // Only invoke initStorage, when we are forced to do it!
                if (createOrUpdate != null) s.invokeInitStorage(config, create = createOrUpdate, upgrade = createOrUpdate)
                return s
            }
            lock.acquire().use {
                var storage = storagesByNumber[config.number]
                val storage2 = storagesById[config.id]
                if (storage !== storage2) {
                    throw NakshaException(
                        ILLEGAL_ARGUMENT,
                        "The storage-id (${config.id}) and -number (${config.number}) belong to different storages")
                }
                if (storage != null) {
                    if (storage.config == config) return storage
                    storage.invokeShutdownStorage(false)
                }
                val klass = Platform.klassForName<AbstractStorage<*>>(config.className)
                storage = Platform.newInstanceOf(klass)
                storage.invokeInitStorage(config, create = createOrUpdate, upgrade = createOrUpdate)
                storagesById[config.id] = storage
                storagesByNumber[config.number] = storage
                return storage
            }
        }

        /**
         * Returns the storage with the given identifier.
         * - Throws [NakshaError.STORAGE_NOT_FOUND], if no such storage is added to the [cache].
         * @param storageId the storage-id.
         * @return the storage.
         */
        @JvmStatic
        @JsStatic
        fun useStorageById(storageId: String): IStorage = storagesById[storageId]
            ?: throw NakshaException(STORAGE_NOT_FOUND, "No storage found for storage-id: $storageId")

        /**
         * Returns the storage with the given number.
         * - Throws [NakshaError.STORAGE_NOT_FOUND], if no such storage is added to the [cache].
         * @param storageNumber the storage-number.
         * @return the storage.
         */
        @JvmStatic
        @JsStatic
        fun useStorageByNumber(storageNumber: Int64): IStorage = storagesByNumber[storageNumber]
            ?: throw NakshaException(STORAGE_NOT_FOUND, "No storage found for storage-number: $storageNumber")

        /**
         * Remove the given storage, invoke [AbstractStorage.shutdownStorage] so that all cached [Tuple] of this storage are removed, eventually returning the removed and shutdown storage.
         *
         * There is no guarantee that this method does block until the shutdown is finished, it is perfectly fine if the shutdown is done gracefully in the background.
         *
         * - Throws [NakshaError.ILLEGAL_STATE] if the given **storage-number** and **storage-id** are currently allocated to two different storages.
         * @param config the storage configuration to remove.
         * @return the removed storage, if any.
         * @since 3.0.0
         */
        @JvmStatic
        @JsStatic
        fun removeStorage(config: StorageConfig): IStorage? {
            val s = storagesByNumber[config.number]
            if (s == null || s.config != config) return null
            lock.acquire().use {
                val storage = storagesByNumber[config.number]
                if (storage == null || storage.config != config) return null
                val storage2 = storagesById[config.id]
                if (storage !== storage2) {
                    throw NakshaException(
                        ILLEGAL_ARGUMENT,
                        "The storage-id (${config.id}) and -number (${config.number}) belong to different storages")
                }
                storagesById.remove(config.id)
                storagesByNumber.remove(config.number)
                storage.invokeShutdownStorage(true)
                return storage
            }
        }

        /**
         * The reference to the first [tuple-cache][ITupleCache].
         *
         * By default, this will be the [TupleHeapCache], applications can reorganize the cache, for example add a second-level cache (like local filesystem or localhost redis), third-level (remote redis), fourth-level (S3 buckets) or whatever wanted.
         *
         * It is strongly recommended to only use caches that extend [AbstractTupleCache], and then simply to call [start][AbstractTupleCache.start], which will initialize the cache, acquire the [lock], and add the cache to the correct position in the cache chain, releasing the [lock]. This base class ensures that all contracts of [ITupleCache] are followed as specified.
         *
         * - **Warning**: It is highly recommended to keep the [TupleHeapCache] as the first level cache, even while it is possible to remove or replace it, it is strongly discouraged, because many libraries take advantage of the heap-cache and intrinsically expect it to be there!
         * - **Warning**: Setting the cache to _null_ can cause plenty of unexpected exceptions, because a lot of code requires some cache via [cache].
         * @since 3.0.0
         */
        @JvmField
        @JsStatic
        val cacheRef = AtomicRef<ITupleCache>(TupleHeapCache())

        /**
         * Returns the [tuple cache][ITupleCache], usage like:
         * ```kotlin
         * // rs = ResultTupleList
         * val result = Naksha.cache.load(rs)
         * ```
         * ```java
         * // rs = ResultTupleList
         * final ResultTupleList result = Naksha.cache.load(rs, 0, rs.size())
         * ```
         * - Throws [ILLEGAL_STATE], if no cache is available ([cacheRef] is _null_). This only happens, when an application explicitly removes all caches.
         * @return the [cache][ITupleCache] for [Tuple].
         * @since 3.0.0
         */
        @JvmStatic
        @JsStatic
        val cache: ITupleCache
            get() = cacheRef.get() ?: throw NakshaException(ILLEGAL_STATE, "No cache available")

        private val _adminOptions: AtomicRef<SessionOptions> = AtomicRef(null)

        /**
         * The admin-options to use by all storages for internal processing, like setting up the admin-map.
         *
         * This should be overridden by the application when bootstrapping.
         *
         * The admin-options are needed for administrative work, reading dictionaries, collection information, create administrative structures. The application should set the defaults to have more control over the `appId` and/or `author` being used, when internal data is processed, and how internal connections authenticate (`appName`).
         *
         * If not explicitly set, the first time the options are needed, they are creating from the current [NakshaContext].
         *
         * @since 3.0.0
         */
        @JvmStatic
        @JsStatic
        var adminOptions: SessionOptions
            get() {
                var options = _adminOptions.get()
                while (options == null) {
                    options = SessionOptions(
                        appName = "lib-psql/$LATEST",
                        appId = NakshaContext.appId(),
                        author = NakshaContext.author(),
                        parallel = false,
                        useMaster = true,
                        excludePaths = NakshaContext.defaultExcludePaths.get(),
                        excludeFn = NakshaContext.defaultExcludeFn.get(),
                        connectTimeout = NakshaContext.defaultConnectTimeout.get(),
                        socketTimeout = NakshaContext.defaultSocketTimeout.get(),
                        stmtTimeout = NakshaContext.defaultStmtTimeout.get(),
                        lockTimeout = NakshaContext.defaultLockTimeout.get()
                    )
                    if (!_adminOptions.compareAndSet(null, options)) {
                        options = null
                    }
                }
                return options
            }
            set(value) {
                _adminOptions.set(value)
            }
    }
}