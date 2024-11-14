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
import naksha.model.objects.NakshaFeature
import naksha.model.objects.NakshaProperties
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.js.JsStatic
import kotlin.jvm.JvmField
import kotlin.jvm.JvmStatic

/**
 * Utility singleton of the Naksha `lib-models`.
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
         * The identifier of the virtual (internal) administration map.
         * @since 3.0.0
         */
        const val VIRT_ADMIN_MAP = "naksha~admin"

        /**
         * The number of the virtual (internal) administration map.
         * @since 3.0.0
         */
        const val VIRT_ADMIN_MAP_NUMBER = 0

        /**
         * The identifier of the virtual collection in which transactions are stored.
         * @since 3.0.0
         */
        const val VIRT_TRANSACTIONS = "naksha~transactions"

        /**
         * The collection-number of the virtual collection in which transactions are stored.
         * @since 3.0.0
         */
        const val VIRT_TRANSACTIONS_NUMBER = 0

        /**
         * The identifier of the virtual collection in which the maps are stored.
         * @since 3.0.0
         */
        const val VIRT_MAPS = "naksha~maps"

        /**
         * The collection-number of the virtual collection in which the maps are stored.
         * @since 3.0.0
         */
        const val VIRT_MAPS_NUMBER = 1

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
         * The identifier of the virtual collection in which the collections them-self are stored.
         * @since 3.0.0
         */
        const val VIRT_COLLECTIONS = "naksha~collections"

        /**
         * The collection-number of the virtual collection in which the collections them-self are stored.
         * @since 3.0.0
         */
        const val VIRT_COLLECTIONS_NUMBER = 0

        /**
         * The maximum length of identifiers.
         * @since 3.0.0
         */
        const val MAX_ID_LENGTH = 45

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
         * Tests if the given **id** is a valid identifier, otherwise throws an [NakshaError.ILLEGAL_ID].
         * @param id the identifier to test.
         * @return the given identifier, tested.
         * @since 3.0.0
         */
        @JsStatic
        @JvmStatic
        fun verifyId(id: String?): String {
            if (id.isNullOrEmpty() || "naksha" == id || id.length > MAX_ID_LENGTH) {
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
         * This method will query the [cache] to get the [dictionary-manager][IDictManager].
         * - Throws [NakshaError.DICT_MANAGER_NOT_FOUND], if a [dictionary-manager][IDictManager] is needed to decode the [Tuple], but not available in [cache].
         * @param tuple the tuple to decode.
         * @return the Naksha feature, _null_ if decoding failed or _null_ was given.
         * @since 3.0.0
         */
        @JsStatic
        @JvmStatic
        fun decodeTuple(tuple: Tuple): NakshaFeature {
            val sn = tuple.storageNumber
            val meta = tuple.meta
            val dictManager = getStorage(sn) ?: cacheRef.get()
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
         * @param dictionary the [dictionary][IDict] to use to encode the feature; _null_ if encoding should be done storage agnostic.
         * @param flags the encoding flags or _null_, if [DEFAULT_FLAGS] should be used.
         * @return the encoded [Tuple].
         * @since 3.0.0
         */
        @JsStatic
        @JvmStatic
        fun encodeTuple(feature: NakshaFeature, dictionary: IDict? = null, flags: Flags? = null): Tuple {
            val xyz = feature.properties.xyz
            val meta = Metadata.fromXyzNs(xyz) ?: Metadata.UNDEFINED
            val flagsOrDefault = flags ?: DEFAULT_FLAGS
            val featureBytes = encodeFeature(feature, flagsOrDefault, dictionary)
            val geoBytes = encodeGeometry(feature.geometry, flagsOrDefault)
            val refPoint = encodeGeometry(feature.referencePoint, TWKB)
            val tagsBytes = encodeTags(xyz.tags.toTagMap(), flagsOrDefault, dictionary)
            return Tuple(meta, featureBytes, geoBytes, refPoint, tagsBytes, feature.attachment, IS_COMPLETE)
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
            return Tuple(meta, featureBytes, geoBytes, refPoint, tagsBytes, feature.attachment, IS_COMPLETE)
        }

        /**
         * Encodes the given [NakshaFeature] into bytes, skipping over the [geometry][NakshaFeature.geometry] or the [XYZ-namespace][XyzNs].
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
         * @param dictManager the dictionary manager to use for decoding; if any.
         * @return the Naksha feature.
         * @since 3.0.0
         */
        @JsStatic
        @JvmStatic
        fun decodeFeature(bytes: ByteArray?, flags: Flags, dictManager: IDictManager?): NakshaFeature? {
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
         * Decode the Naksha tags.
         * @param bytes the bytes to decode.
         * @param flags the codec flags.
         * @param dictManager the dictionary manager to use for decoding; if any.
         * @return the Naksha tags.
         * @since 3.0.0
         */
        @JsStatic
        @JvmStatic
        fun decodeTags(bytes: ByteArray?, flags: Flags, dictManager: IDictManager?): TagMap? {
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
        internal val storagesByNumber = AtomicMap<Int64, IStorage>()

        /**
         * All registered storages by [storage-id][IStorage.id].
         * @since 3.0.0
         */
        @JvmField
        internal val storagesById = AtomicMap<String, IStorage>()

        /**
         * Returns a list of all currently registered storages.
         * @since 3.0.0
         */
        @JvmStatic
        @JsStatic
        fun listStorages(): List<IStorage> = storagesByNumber.map { (_, storage) -> storage }

        /**
         * Register the given storage, this method should be called by [IStorage.initStorage].
         * @param storage the storage to add.
         * @return the added storage.
         * @since 3.0.0
         */
        @JvmStatic
        @JsStatic
        fun addStorage(storage: IStorage): IStorage {
            var added = false
            lock.acquire().use {
                val existing = storagesById.putIfAbsent(storage.id, storage)
                if (existing != null) {
                    if (existing === storage) return storage // This storage was already added.
                    throw NakshaException(ILLEGAL_STATE, "Another storage with the same id ('${storage.id}') is registered already, existing number: ${existing.number}, provided number: ${storage.number}")
                }
                storagesByNumber[storage.number] = storage
                added = true
            }
            if (added) cacheRef.get()?.addedStorage(storage)
            return storage
        }

        /**
         * Unregister the given storage, removes all cached [Tuple] of this storage, should be called by [IStorage.close].
         * @param storage the storage to remove.
         * @return the removed storage.
         * @since 3.0.0
         */
        @JvmStatic
        @JsStatic
        fun removeStorage(storage: IStorage): IStorage {
            var removed = false
            lock.acquire().use {
                if (storagesById.remove(storage.id, storage)) {
                    storagesByNumber.remove(storage.number)
                    removed = true
                }
            }
            if (removed) cacheRef.get()?.removedStorage(storage)
            return storage
        }

        /**
         * Returns the storage with the given identifier.
         * @param storageId the storage-id.
         * @return the storage, if added to cache.
         */
        @JvmStatic
        @JsStatic
        @JsName("getStorageById")
        fun getStorage(storageId: String): IStorage? = storagesById[storageId]

        /**
         * Returns the storage with the given number.
         * @param storageNumber the storage-number.
         * @return the storage, if added to cache.
         */
        @JvmStatic
        @JsStatic
        fun getStorage(storageNumber: Int64): IStorage? = storagesByNumber[storageNumber]

        /**
         * Returns the storage for the given tuple-number.
         * @param tupleNumber the tuple-number.
         * @return the storage, if added to cache.
         */
        @JvmStatic
        @JsStatic
        @JsName("getStorageByTupleNumber")
        fun getStorage(tupleNumber: TupleNumber): IStorage? = storagesByNumber[tupleNumber.storageNumber]

        /**
         * Returns the storage with the given number.
         * - Throws [NakshaError.STORAGE_NOT_FOUND], if no such storage is added to the [cache].
         * @param storageId the storage-id.
         * @return the storage.
         */
        @JvmStatic
        @JsStatic
        @JsName("useStorageById")
        fun useStorage(storageId: String): IStorage = storagesById[storageId]
            ?: throw NakshaException(STORAGE_NOT_FOUND, "No storage found for storage-id: $storageId", id=storageId)

        /**
         * Returns the storage with the given number.
         * - Throws [NakshaError.STORAGE_NOT_FOUND], if no such storage is added to the [cache].
         * @param storageNumber the storage-number.
         * @return the storage.
         */
        @JvmStatic
        @JsStatic
        fun useStorage(storageNumber: Int64): IStorage = storagesByNumber[storageNumber]
            ?: throw NakshaException(STORAGE_NOT_FOUND, "No storage found for storage-number: $storageNumber", id=storageNumber.toString())

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
    }
}