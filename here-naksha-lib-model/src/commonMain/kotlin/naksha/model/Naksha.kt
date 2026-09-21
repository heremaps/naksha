@file:Suppress("OPT_IN_USAGE")

package naksha.model

import naksha.base.*
import naksha.base.Id.IdCompanion.ADMIN_CATALOG_INT
import naksha.base.Id.IdCompanion.ADMIN_CATALOG_TEXT
import naksha.base.Id.IdCompanion.BOOKS_COL_INT
import naksha.base.Id.IdCompanion.BOOKS_COL_TEXT
import naksha.base.Id.IdCompanion.CATALOGS_COL_INT
import naksha.base.Id.IdCompanion.CATALOGS_COL_TEXT
import naksha.base.Id.IdCompanion.COLLECTIONS_COL_INT
import naksha.base.Id.IdCompanion.COLLECTIONS_COL_TEXT
import naksha.base.Id.IdCompanion.TRANSACTIONS_COL_INT
import naksha.base.Id.IdCompanion.TRANSACTIONS_COL_TEXT
import naksha.base.Platform.PlatformCompanion.fromJSON
import naksha.base.Platform.PlatformCompanion.md5
import naksha.geo.GeoUtil.GeoUtil_C.fromTWKB
import naksha.geo.GeoUtil.GeoUtil_C.toTWKB
import naksha.geo.SpGeometry
import naksha.base.NakshaError.NakshaErrorCompanion.ILLEGAL_ARGUMENT
import naksha.base.NakshaError.NakshaErrorCompanion.STORAGE_NOT_FOUND
import naksha.model.NakshaVersion.Companion.CURRENT
import naksha.model.objects.NakshaStorage
import kotlin.js.JsExport
import kotlin.js.JsName
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
         * The prefix for internal identifiers.
         * @since 3.0
         */
        const val INTERNAL_PREFIX = "naksha~"

        //
        // Move constants into `Id` class, mainly because `Id` is in `lib-base` where we need it, and `Naksha` is in `lib-model`.
        //
        @Deprecated(message = "Use Id.ADMIN_CATALOG_TEXT", replaceWith = ReplaceWith("Id.ADMIN_CATALOG_TEXT"))
        const val ADMIN_CATALOG_ID = ADMIN_CATALOG_TEXT
        @Deprecated(message = "Use Id.ADMIN_CATALOG_INT", replaceWith = ReplaceWith("Id.ADMIN_CATALOG_INT"))
        const val ADMIN_CATALOG_FN = ADMIN_CATALOG_INT
        @Deprecated(message = "Use Id.COLLECTIONS_COL_TEXT", replaceWith = ReplaceWith("Id.COLLECTIONS_COL_TEXT"))
        const val COLLECTIONS_COL_ID = COLLECTIONS_COL_TEXT
        @Deprecated(message = "Use Id.COLLECTIONS_COL_INT", replaceWith = ReplaceWith("Id.COLLECTIONS_COL_INT"))
        const val COLLECTIONS_COL_FN = COLLECTIONS_COL_INT
        @Deprecated(message = "Use Id.TRANSACTIONS_COL_TEXT", replaceWith = ReplaceWith("Id.TRANSACTIONS_COL_TEXT"))
        const val TRANSACTIONS_COL_ID = TRANSACTIONS_COL_TEXT
        @Deprecated(message = "Use Id.TRANSACTIONS_COL_INT", replaceWith = ReplaceWith("Id.TRANSACTIONS_COL_INT"))
        const val TRANSACTIONS_COL_FN = TRANSACTIONS_COL_INT
        @Deprecated(message = "Use Id.CATALOGS_COL_TEXT", replaceWith = ReplaceWith("Id.CATALOGS_COL_TEXT"))
        const val CATALOGS_COL_ID = CATALOGS_COL_TEXT
        @Deprecated(message = "Use Id.CATALOGS_COL_INT", replaceWith = ReplaceWith("Id.CATALOGS_COL_INT"))
        const val CATALOGS_COL_FN = CATALOGS_COL_INT
        @Deprecated(message = "Use Id.BOOKS_COL_TEXT", replaceWith = ReplaceWith("Id.BOOKS_COL_TEXT"))
        const val BOOKS_COL_ID = BOOKS_COL_TEXT
        @Deprecated(message = "Use Id.BOOKS_COL_INT", replaceWith = ReplaceWith("Id.BOOKS_COL_INT"))
        const val BOOKS_COL_FN = BOOKS_COL_INT

        /**
         * The maximum length of identifiers _(`42`)_.
         * @since 3.0
         */
        const val MAX_ID_LENGTH = 42 // The answer to everything ;-)

        /**
         * The maximum length of internal identifiers.
         * @since 3.0
         */
        const val MAX_INTERNAL_ID_LENGTH = 63

        // TODO: This shows why we really need a special TupleNumberArray next to TupleNumberList, which stores all tuple-numbers in a single
        //       byte-array, compressed by adding the database-number, catalog-number and collection-number upfront, then followed by all the
        //       tuple-numbers. It will reduce memory consumption from 1 GiB to around 256 MiB.

        /**
         * The maximum amount of tuple that can be fetched using normal query methods.
         *
         * This protects the database and client for too big data. When reading tuples, each tuple-number is actually returned from the storage as 16-byte value, so feature-number and version. The Java client then adds database-number, catalog-number and collection-number. Therefore, the maximum amount of data transferred when fetching this amount of tuple is roughly `HARD_READ_LIMIT * 16`. This can already be huge, but when we copy this onto the JVM heap, we expand it, because we add the database-number _(8 byte)_, catalog-number _(4 byte)_ and collection-number _(4 byte)_ to it, plus the overhead of the [TupleNumber] instance (16-byte per instance). Then there is the array into which they are added, this array holds a reference for each [TupleNumber], so another 8-byte. In total, for JVM heap usage, we need to multiple this value with around 56 _(16+16+8+4+4+8)_. For the default value of 16,777,216 this already means around 1 GiB of heap usage, not even thinking about how much more memory will be used, when we start loading all these tuple!
         * @since 3.0
         */
        @JvmStatic
        var HARD_TUPLE_LIMIT = 16_777_216

        /**
         * An immutable map between the identifier of an internal collection to the number of that collection.
         * @since 3.0
         */
        @JsStatic
        @JvmStatic
        val internalIdToNumber = mapOf(
            Pair(ADMIN_CATALOG_ID, ADMIN_CATALOG_FN),
            Pair(COLLECTIONS_COL_ID, COLLECTIONS_COL_FN),
            Pair(TRANSACTIONS_COL_ID, TRANSACTIONS_COL_FN),
            Pair(CATALOGS_COL_ID, CATALOGS_COL_FN),
            Pair(BOOKS_COL_ID, BOOKS_COL_FN),
        )

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

        @Deprecated(message = "Use Id.textToNumber", replaceWith = ReplaceWith("Id.textToNumber"))
        @JsStatic
        @JvmStatic
        fun databaseNumber(id: String): Long = Id.textToNumber(id)

        @Deprecated(message = "Use Id.textToInt", replaceWith = ReplaceWith("Id.textToInt"))
        @JsStatic
        @JvmStatic
        fun catalogNumber(id: String): Int = Id.textToInt(id)

        @Deprecated(message = "Use Id.textToInt", replaceWith = ReplaceWith("Id.textToInt"))
        @JsStatic
        @JvmStatic
        fun collectionNumber(id: String): Int = Id.textToInt(id)

        @Deprecated(message = "Use Id.textToNumber", replaceWith = ReplaceWith("Id.textToNumber"))
        @JsStatic
        @JvmStatic
        fun featureNumber(id: String): Long = Id.textToNumber(id)

        @Deprecated(message = "Use Id.hashText", replaceWith = ReplaceWith("Id.hashText"))
        @JsStatic
        @JvmStatic
        fun featureNumberAsHash(id: String): Long = Id.hashText(id)

        /**
         * `0x8000_0000_0000_0000`, should be `-9223372036854775808`, but this does not work in Kotlin, only `-9223372036854775807 -1`?
         * - See [programmer calculator](https://devtools.calckit.io/programmer-calculator)
         */
        internal const val INT64_SIGN_BIT = Long.MIN_VALUE

        /**
         * `0x7fff_ffff_ffff_ffff`
         * - See [programmer calculator](https://devtools.calckit.io/programmer-calculator)
         */
        internal const val INT64_CLEAR_SIGN_BIT = 0x7fff_ffff_ffff_ffffL

        /**
         * `0x0000_0000_0000_ffff`
         * - See [programmer calculator](https://devtools.calckit.io/programmer-calculator)
         */
        internal const val INT64_CLEAR_HIGH48 = 0x0000_0000_0000_ffffL

        /**
         * `0x0000_0000_ffff_ffff` aka `4294967295`
         * - See [programmer calculator](https://devtools.calckit.io/programmer-calculator)
         */
        internal const val INT64_CLEAR_HIGH32 = 4294967295L

        /**
         * `0xff00_0000_0000_0000` aka `-72057594037927936`
         * - See [programmer calculator](https://devtools.calckit.io/programmer-calculator)
         */
        internal const val INT64_CLEAR_HIGH8 = -72057594037927936L

        /**
         * `0xffff_ffff_ffff_0000` aka `-65536`
         * - See [programmer calculator](https://devtools.calckit.io/programmer-calculator)
         */
        internal const val INT64_CLEAR_LOW16 = -65536L

        /**
         * Returns the partition-number from the given feature-id.
         *
         * This is basically just an unsigned 16-bit integer, extracted from the lowest 16-bit of the feature-number. When there are less than 65536 partitions, the value must be divided by the number of real partitions, and the rest indexes the partition, for example for 4 partitions do `partitionNumber(featureNumber) % 4`, what will be a value between 0 and 3.
         * @param featureId the feature-id.
         * @return the partition-number.
         * @see [featureNumber]
         */
        @JsName("featureNumberById")
        @JsStatic
        @JvmStatic
        fun partitionNumber(featureId: String): Int = partitionNumber(featureNumber(featureId))

        /**
         * Returns the partition-number from the given feature-number.
         *
         * This is basically just an unsigned 16-bit integer, extracted from the lowest 16-bit of the feature-number. When there are less than 65536 partitions, the value must be divided by the number of real partitions, and the rest indexes the partition, for example for 4 partitions do `partitionNumber(featureNumber) % 4`, what will be a value between 0 and 3.
         * @param featureNumber the feature-number.
         * @return the partition-number.
         * @see [featureNumber]
         */
        @JsStatic
        @JvmStatic
        fun partitionNumber(featureNumber: Long): Int = featureNumber.toInt() and 0xffff

        /**
         * Decodes Naksha tags from their JSON text representation into a [TagList].
         *
         * Supports both persisted forms:
         * - a JSON array ([tag_list][naksha.model.objects.MemberType.TAG_LIST], the default) is returned
         *   unmodified, preserving the element order;
         * - a JSON object ([naksha.model.objects.MemberType.TAG_MAP_FROM_TAG_LIST]) is re-flattened via
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
        internal val lock = Platform.newLock()

        /**
         * All registered storages by [storage-number][IStorage.number].
         * @since 3.0
         */
        @JvmField
        internal val storagesByNumber = AtomicMap<Long, AbstractStorage<*>>()

        /**
         * All registered storages by [storage-id][IStorage.id].
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
        fun listStorages(): List<IStorage> = storagesByNumber.map { (_, storage) -> storage }

         /**
         * Returns the storage with the given configuration.
         * @param storage the storage configuration.
         * @return the storage, if available.
         */
        @JvmStatic
        @JsStatic
        fun getStorage(storage: NakshaStorage): IStorage? {
            val s = storagesByNumber[storage.databaseNumber] ?: return null
            val s2 = storagesById[storage.id] ?: return null
            return if (s!==s2 || s.config != storage) null else s
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
        fun getStorageByNumber(storageNumber: Long): IStorage? = storagesByNumber[storageNumber]

        /**
         * Returns the storage for the given tuple-number.
         * @param tupleNumber the tuple-number.
         * @return the storage, if added to cache.
         */
        @JvmStatic
        @JsStatic
        fun getStorageByTupleNumber(tupleNumber: TupleNumber): IStorage? = storagesByNumber[tupleNumber.databaseNumber]

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

        private fun _useStorage(config: NakshaStorage, forceCreateOrUpgrade: Boolean?): IStorage {
            var s = storagesByNumber[config.databaseNumber]
            var s2 = storagesById[config.id]
            if (s !== s2) {
                lock.acquire().use {
                    s = storagesByNumber[config.databaseNumber]
                    s2 = storagesById[config.id]
                    if (s !== s2) {
                        throw NakshaException(
                            ILLEGAL_ARGUMENT,
                            "The storage-id (${config.id}) and -number (${config.databaseNumber}) belong to different storages")
                    }
                }
            }
            val localS = s
            if (localS != null && localS.config.configEquals(config)) {
                // Only invoke initStorage, when we are forced to do it!
                if (forceCreateOrUpgrade == true) localS.invokeInitStorage(config, create = true, upgrade = true)
                return localS
            }
            lock.acquire().use {
                var storage = storagesByNumber[config.databaseNumber]
                val storage2 = storagesById[config.id]
                if (storage !== storage2) {
                    throw NakshaException(
                        ILLEGAL_ARGUMENT,
                        "The storage-id (${config.id}) and -number (${config.databaseNumber}) belong to different storages")
                }
                if (storage != null) {
                    if (storage.config.configEquals(config)) {
                        return storage
                    }
                    storage.invokeShutdownStorage(false)
                }
                val klass = Platform.klassForName<AbstractStorage<*>>(config.className)
                storage = Platform.newInstanceOf(klass)
                storage.invokeInitStorage(config, create = forceCreateOrUpgrade, upgrade = forceCreateOrUpgrade)
                storagesById[config.id] = storage
                storagesByNumber[config.databaseNumber] = storage
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
        fun useStorageByNumber(storageNumber: Long): IStorage = storagesByNumber[storageNumber]
            ?: throw NakshaException(STORAGE_NOT_FOUND, "No storage found for storage-number: $storageNumber")

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
        fun removeStorage(config: NakshaStorage): IStorage? {
            val s = storagesByNumber[config.databaseNumber]
            if (s == null || s.config != config) return null
            lock.acquire().use {
                val storage = storagesByNumber[config.databaseNumber]
                if (storage == null || storage.config != config) return null
                val storage2 = storagesById[config.id]
                if (storage !== storage2) {
                    throw NakshaException(
                        ILLEGAL_ARGUMENT,
                        "The storage-id (${config.id}) and -number (${config.databaseNumber}) belong to different storages")
                }
                storagesById.remove(config.id)
                storagesByNumber.remove(config.databaseNumber)
                storage.invokeShutdownStorage(true)
                return storage
            }
        }

        /**
         * The [tuple cache][TupleCache], usage like:
         * ```kotlin
         * // rs = ResultTupleList
         * val result = Naksha.cache.load(rs)
         * ```
         * ```java
         * // rs = ResultTupleList
         * final ResultTupleList result = Naksha.cache.load(rs, 0, rs.size());
         * ```
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val cache = TupleCache()

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
         * @since 3.0
         */
        @JvmStatic
        @JsStatic
        var adminOptions: SessionOptions
            get() {
                var options = _adminOptions.get()
                while (options == null) {
                    options = SessionOptions(
                        appName = "naksha/$CURRENT",
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


        /**
         * Creates a new virtual [TupleNumber] for a feature in the given storage, catalog, collection.
         *
         * You need a new version number for features belonging together to the same virtual transaction, therefore, you need to query this via [Version.virtualVersion] before invoking this method. This method is for testing and Mockups only!
         * @param storageId the `id` of the storage where the feature is contained.
         * @param catalogId the `id` of the catalog where the feature is contained.
         * @param collectionId the `id` of the collection where the feature is contained.
         * @param featureId the `id` of the feature being stored.
         * @param version the version, see [Version.virtualVersion].
         * @return the new virtual feature-number.
         * @since 3.0
         */
        @JsStatic
        @JvmStatic
        fun virtualTupleNumber(storageId: String, catalogId: String, collectionId: String, featureId: String, version: Version): TupleNumber
            = TupleNumber(databaseNumber(storageId), catalogNumber(catalogId), collectionNumber(collectionId), featureNumber(featureId), version.number)
    }
}
