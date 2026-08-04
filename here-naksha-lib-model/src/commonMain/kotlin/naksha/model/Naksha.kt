@file:Suppress("OPT_IN_USAGE")

package naksha.model

import naksha.base.*
import naksha.base.Base.BaseCompanion.fromJSON
import naksha.base.Base.BaseCompanion.toJSON
import naksha.geo.GeoUtil.GeoUtil_C.fromTWKB
import naksha.geo.GeoUtil.GeoUtil_C.toTWKB
import naksha.geo.SpGeometry
import naksha.base.NakshaError.NakshaErrorCompanion.STORAGE_NOT_FOUND
import naksha.model.objects.NakshaStorage
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmField
import kotlin.jvm.JvmStatic

/**
 * Utility singleton of the Naksha `lib-models`.
 * @since 3.0
 * @see IStorage
 */
@JsExport
class Naksha private constructor() {
    companion object NakshaCompanion {
        /**
         * The maximum amount of tuple that can be fetched using normal query methods.
         *
         * This protects the database and client for too big data. When reading tuples, each tuple-number is actually returned from the storage as 16-byte value, so feature-number and version. The Java client then adds database-number, catalog-number and collection-number. Therefore, the maximum amount of data transferred when fetching this amount of tuple is roughly `HARD_READ_LIMIT * 16`. This can already be huge, but when we copy this onto the JVM heap, we expand it, because we add the database-number _(8 byte)_, catalog-number _(4 byte)_ and collection-number _(4 byte)_ to it, plus the overhead of the [TupleNumber] instance (16-byte per instance). Then there is the array into which they are added, this array holds a reference for each [TupleNumber], so another 8-byte. In total, for JVM heap usage, we need to multiple this value with around 56 _(16+16+8+4+4+8)_. For the default value of 16,777,216 this already means around 1 GiB of heap usage, not even thinking about how much more memory will be used, when we start loading all these tuple!
         * @since 3.0
         */
        @JvmStatic
        var HARD_TUPLE_LIMIT = 16_777_216

        /**
         * Default feature encoding used by all storages when nothing else is configured.

         */
        @JvmField
        var DEFAULT_DATA_ENCODING: DataEncoding = DataEncoding.DEFAULT

        /**
         * Decides about the default log-level used when creating new [SessionOptions].
         * @since 3.0
         */
        @JvmField
        var DEFAULT_SESSION_LOG_LEVEL: String? = null

        /**
         * Decode Naksha tags from their binary representation.
         * @param json the JSON string from which to decode.
         * @return the Naksha tags.
         * @since 3.0
         */
        @JsStatic
        @JvmStatic
        fun decodeTags(json: String?): TagMap? {
            if (json.isNullOrBlank()) return null
            val decoded = fromJSON(json)
            return if (decoded is PlatformMap) decoded.proxy(TagMap::class) else null
        }

        /**
         * Encodes the given tags into their binary representation.
         * @param tags the tags to encode.
         * @return the JSON text representation, or _null_ if [tags] is _null_ / empty.
         * @since 3.0
         */
        @JsStatic
        @JvmStatic
        fun encodeTags(tags: TagMap?): String? {
            if (tags.isNullOrEmpty()) return null
            return toJSON(tags)
        }

        /**
         * Encodes the given tag-list into the [tag_list][naksha.model.objects.MemberType.TAG_LIST]
         * representation: a JSON array, with the element order preserved.
         * @param tags the tags to encode.
         * @return the JSON array text representation, or _null_ if [tags] is _null_ / empty.
         * @since 3.0
         */
        @JsStatic
        @JvmStatic
        fun encodeTagList(tags: TagList?): String? {
            if (tags.isNullOrEmpty()) return null
            return toJSON(tags)
        }

        /**
         * Decodes Naksha tags from their JSON text representation into a [TagList].
         *
         * Supports both persisted forms:
         * - a JSON array ([tag_list][naksha.model.objects.MemberType.TAG_LIST], the default) is returned
         *   unmodified, preserving the element order;
         * - a JSON object ([naksha.model.objects.MemberType.TAG_MAP_FROM_ARRAY]) is re-flattened via
         *   [TagMap.toTagList], in which case the original order is not guaranteed.
         * @param json the JSON text to decode (value of the `tags` member).
         * @return the decoded tag-list, or _null_ if [json] is _null_, blank, or neither an array nor an object.
         * @since 3.0
         */
        @JsStatic
        @JvmStatic
        fun decodeTagList(json: String?): TagList? {
            if (json.isNullOrBlank()) return null
            return when (val decoded = fromJSON(json)) {
                is PlatformList -> decoded.proxy(TagList::class)
                is PlatformMap -> decoded.proxy(TagMap::class).toTagList()
                else -> null
            }
        }

        /**
         * Decode a GeoJSON geometry from its binary representation.
         * @param bytes the bytes to decode.
         * @return the geometry.
         * @since 3.0
         */
        @JsStatic
        @JvmStatic
        fun decodeGeometry(bytes: ByteArray?): SpGeometry? {
            if (bytes == null || bytes.isEmpty()) return null
            return fromTWKB(bytes)
        }

        /**
         * Encodes the given GeoJSON geometry into its binary representation.
         * @param geometry the geometry to encode.
         * @return the encoded GeoJSON geometry.
         * @since 3.0
         */
        @JsStatic
        @JvmStatic
        fun encodeGeometry(geometry: SpGeometry?): ByteArray? {
            if (geometry == null) return null
            return toTWKB(geometry)
        }

        /**
         * A lock that is used to modify static values atomically.
         * @since 3.0
         */
        @JvmField
        internal val lock = Base.newLock()

        /**
         * All registered storages by [id][IStorage.id].
         * @since 3.0
         */
        @JvmField
        internal val storagesById = AtomicMap<String, AbstractStorage<*>>()

        /**
         * Returns a list of all currently registered storages.
         * @since 3.0
         */
        @JvmStatic
        @JsStatic
        fun listStorages(): List<IStorage> = storagesById.map { (_, storage) -> storage }

         /**
         * Returns the storage with the given configuration.
         * @param storage the storage configuration.
         * @return the storage, if available.
         */
        @JvmStatic
        @JsStatic
        fun getStorage(storage: NakshaStorage): IStorage? = storagesById[storage.id.text]

        /**
         * Returns the storage with the given identifier.
         * @param storageId the storage-id.
         * @return the storage, if added to cache.
         */
        @JvmStatic
        @JsStatic
        fun getStorageById(storageId: String): IStorage? = storagesById[storageId]

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
        fun setupStorage(config: NakshaStorage): IStorage = _useStorage(config, true)

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
        fun useStorage(config: NakshaStorage): IStorage = _useStorage(config, null)

        private tailrec fun _useStorage(config: NakshaStorage, forceCreateOrUpgrade: Boolean?): IStorage {
            var storage = storagesById[config.id.text]
            if (storage != null) {
                if (storage.config.configEquals(config)) {
                    // The storage exists already and is configured as requested.
                    // Only invoke initStorage, when we are forced to do it!
                    if (forceCreateOrUpgrade == true) storage.invokeInitStorage(config, create = true, upgrade = true)
                    return storage
                }
                // The storage exists already, but a new configuration is requested, we need to replace it.
                storage.invokeShutdownStorage(false)
            }
            lock.acquire().use {
                storage = storagesById[config.id.text]
                // No other client created the storage, and we have a lock, so we can create the storage and add it.
                if (storage == null) {
                    val klass = Base.klassForName<AbstractStorage<*>>(config.className)
                    storage = Base.newInstance(klass)
                    storage.invokeInitStorage(config, create = forceCreateOrUpgrade, upgrade = forceCreateOrUpgrade)
                    storagesById[config.id.text] = storage
                    return storage
                }
            }
            // We only end up here if another thread created the storage while we were waiting for the lock.
            // To ensure algorithmic safety we simply repeat the method, which is why we use tailrec.
            // This allows the compiler to create aloop instead of a recursive call, because this can happen quite often,
            // at least theoretically.
            return _useStorage(config, forceCreateOrUpgrade)
        }

        /**
         * Returns the storage with the given identifier.
         * @param storageId the storage-id.
         * @return the storage.
         * @throws NakshaException with [NakshaError.STORAGE_NOT_FOUND], if no such storage is registered.
         */
        @JvmStatic
        @JsStatic
        fun useStorageById(storageId: String): IStorage = storagesById[storageId]
            ?: throw NakshaException(STORAGE_NOT_FOUND, "No storage found for storage-id: $storageId")

        /**
         * Remove the given storage, invoke [AbstractStorage.shutdownStorage] so that all cached [Tuple] of this storage are removed, eventually returning the removed and shutdown storage.
         *
         * There is no guarantee that this method does block until the shutdown is finished, it is perfectly fine if the shutdown is done gracefully in the background.
         *
         * - Throws [NakshaError.ILLEGAL_STATE] if the given **storage-number** and **storage-id** are currently allocated to two different storages.
         * @param config the storage configuration to remove.
         * @return the removed storage, if any.
         * @since 3.0
         */
        @JvmStatic
        @JsStatic
        tailrec fun removeStorage(config: NakshaStorage): IStorage? {
            val storage = storagesById[config.id.text]
            if (storage == null || storage.config != config) return null
            if (storagesById.remove(config.id.text, storage)) {
                // We have removed the storage.
                storage.invokeShutdownStorage(true)
                return storage
            }
            // We use tailrec so the compiler can create a loop instead of a recursive call.
            // We only end up here, when there was a storage in the registry, but the moment
            // we tried to remove it, somebody else modified the registry, so we have to
            // repeat.
            return removeStorage(config)
        }

        // TODO: We should move this method into TupleNumber itself, but unless TupleNumber has access to Naksha, this won't work.
        /**
         * Helper to create a [TupleNumber] from the given values and returns it.
         * @param databaseId the `id` of the database.
         * @param catalogId the `id` of the catalog.
         * @param collectionId the `id` of the collection.
         * @param featureId the `id` of the feature.
         * @param version the version of the feature _(includes [Action])_.
         * @return this.
         * @since 3.0
         */
        @JsStatic
        @JvmStatic
        fun tupleNumber(databaseId: Id, catalogId: Id, collectionId: Id, featureId: Id, version: Version): TupleNumber
            = TupleNumber(
                databaseNumber = databaseId.number,
                catalogNumber = catalogId.number.toInt(),
                collectionNumber = collectionId.number.toInt(),
                featureNumber = featureId.number,
                version = version.number
            )

        /**
         * The shared tuple-cache.
         * @since 3.0
         */
        val cache = TupleCacheManager()
    }
}
