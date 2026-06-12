@file:Suppress("OPT_IN_USAGE")

package naksha.model

import naksha.base.*
import naksha.base.Platform.PlatformCompanion.fromJSON
import naksha.base.Platform.PlatformCompanion.gzipDeflate
import naksha.base.Platform.PlatformCompanion.gzipInflate
import naksha.base.Platform.PlatformCompanion.md5
import naksha.base.Platform.PlatformCompanion.toJSON
import naksha.geo.GeoUtil.GeoUtil_C.fromTWKB
import naksha.model.objects.StandardMembers
import naksha.geo.GeoUtil.GeoUtil_C.toTWKB
import naksha.geo.SpGeometry
import naksha.jbon.*
import naksha.model.NakshaError.NakshaErrorCompanion.ILLEGAL_ARGUMENT
import naksha.model.NakshaError.NakshaErrorCompanion.STORAGE_NOT_FOUND
import naksha.model.NakshaVersion.Companion.CURRENT
import naksha.model.objects.NakshaCollection
import naksha.model.objects.NakshaFeature
import naksha.model.objects.NakshaStorage
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.js.JsStatic
import kotlin.jvm.JvmField
import kotlin.jvm.JvmOverloads
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

        /**
         * The identifier of the administration map _(`naksha~admin`)_.
         * @since 3.0
         */
        const val ADMIN_MAP = "naksha~admin"

        /**
         * The number of the administration map _(`0`)_.
         * @since 3.0
         */
        const val ADMIN_MAP_NUMBER = 0

        /**
         * The identifier of the virtual collection in which the collections of a map are managed, located within each map _(`naksha~collections`)_.
         * @since 3.0
         */
        const val COLLECTIONS_COL = "naksha~collections"

        /**
         * The collection-number of the virtual collection in which the collections of a map are managed, located within each map _(`0`)_ .
         * @since 3.0
         */
        const val COLLECTIONS_COL_NUMBER = 0

        /**
         * The identifier of the collection in which transactions are stored, located in the [admin-map][ADMIN_MAP] _(`naksha~transactions`)_.
         * @since 3.0
         * @see [naksha.model.objects.NakshaTx]
         */
        const val TRANSACTIONS_COL = "naksha~transactions"

        /**
         * The collection-number of the collection in which transactions are stored, located in the [admin-map][ADMIN_MAP] _(`1`)_.
         * @since 3.0
         */
        const val TRANSACTIONS_COL_NUMBER = 1

        /**
         * The identifier of the collection in which catalogs (maps) are stored, located only within the [admin-map][ADMIN_MAP] _(`naksha~catalogs`)_.
         * @see [naksha.model.objects.NakshaMap]
         * @since 3.0
         */
        const val CATALOGS_COL = "naksha~catalogs"

        /**
         * The collection-number of the collection in which catalogs (maps) are stored, located in the [admin-map][ADMIN_MAP] _(`2`)_.
         * @since 3.0
         */
        const val CATALOGS_COL_NUMBER = 2

        /**
         * The identifier of the collection in which books (global JBON2 dictionaries) are stored, located in the [admin-map][ADMIN_MAP] _(`naksha~books`)_.
         * @since 3.0
         */
        const val BOOKS_COL = "naksha~books"

        /**
         * The collection-number of the collection in which books (global JBON2 dictionaries) are stored, located in the [admin-map][ADMIN_MAP] _(`3`)_.
         * @since 3.0
         */
        const val BOOKS_COL_NUMBER = 3

        /**
         * The maximum length of identifiers _(`42`)_ .
         * @since 3.0
         */
        const val MAX_ID_LENGTH = 42 // The answer to everything ;-)

        /**
         * An immutable map between the identifier of an internal collection to the number of that collection.
         * @since 3.0
         */
        @JsStatic
        @JvmStatic
        val internalIdToNumber = mapOf(
            Pair(ADMIN_MAP, ADMIN_MAP_NUMBER),
            Pair(COLLECTIONS_COL, COLLECTIONS_COL_NUMBER),
            Pair(TRANSACTIONS_COL, TRANSACTIONS_COL_NUMBER),
            Pair(CATALOGS_COL, CATALOGS_COL_NUMBER),
            Pair(BOOKS_COL, BOOKS_COL_NUMBER),
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

        /**
         * Tests if the given **id** is a valid identifier, so matches:
         *
         * `[a-z][a-z0-9_:-]{42}`
         *
         * **Beware**: Identifiers must not contain upper-case letters, because many storages does not make a difference between upper- and lower-cased letters.
         * @param id the identifier.
         * @return _true_ if the identifier is valid; _false_ otherwise.
         * @since 3.0
         * @see [verifyId]
         * @see [MAX_ID_LENGTH]
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
         * @since 3.0
         * @see [isValidId]
         */
        @JsStatic
        @JvmStatic
        fun verifyId(id: String?): String {
            if (id.isNullOrEmpty()) {
                throw illegalId("The given identifier is null or empty")
            }
            if (id == "naksha") {
                throw illegalId("The identifier 'naksha' is forbidden")
            }
            if (id.length > MAX_ID_LENGTH) {
                throw illegalId("The identifier '$id' is too long: ${id.length}, must be maximal $MAX_ID_LENGTH")
            }
            var i = 0
            var c = id[i++]
            if (c.code < 'a'.code || c.code > 'z'.code) {
                throw illegalId("The first character must be a-z, but was $c")
            }
            while (i < id.length) {
                c = id[i++]
                when (c.code) {
                    in 'a'.code..'z'.code -> continue
                    in '0'.code..'9'.code -> continue
                    '_'.code, ':'.code, '-'.code -> continue
                    else -> throw illegalId("Invalid character at index $i: '$c', expected [a-z0-9_:-]")
                }
            }
            return id
        }

        /**
         * Tests if the given identifier is an internal one.
         * @param id the identifier to test.
         * @return _true_ if this is an internal identifier; _false_ otherwise.
         * @since 3.0
         */
        @JsStatic
        @JvmStatic
        fun isInternalId(id: String?): Boolean = id != null && id.startsWith(INTERNAL_PREFIX)

        /**
         * Generates an [MD5](https://en.wikipedia.org/wiki/MD5) hash above the given identifier, which is used to extract many values from it.
         * @param id the identifier to hash.
         * @return a [Binary] view above the [MD5](https://en.wikipedia.org/wiki/MD5) hash.
         * @since 3.0
         */
        private fun hashId(id: String): Binary {
            val hash = md5(id)
            return Binary(Platform.newDataView(hash))
        }

        /**
         * A regular expression to test if a string contains potentially a 63-bit unsigned integer (`1 .. 9,223,372,036,854,775,807`).
         * @since 3.0
         */
        private val is63BitUnsigned = Regex("^[1-9][0-9]{0,18}$")

        /**
         * A regular expression to test if a string contains potentially a 31-bit unsigned integer (`1 .. 2,147,483,647`).
         * @since 3.0
         */
        private val is31BitUnsigned = Regex("^[1-9][0-9]{0,9}\$")

        /**
         * A method to calculate a valid storage-number from the storage-id.
         *
         * @param id the id, from which to extract the storage-number.
         * @return the storage-number.
         * @since 3.0
         * @see [hashId]
         */
        @JsStatic
        @JvmStatic
        fun storageNumber(id: String): Int64 {
            if (id == "0" || is63BitUnsigned.matches(id)) {
                try {
                    return id.toLong(10).toInt64()
                } catch (_: Exception) {}
            }
            val md5 = hashId(id)
            return md5.getInt64(8) or INT64_SIGN_BIT
        }

       /**
         * A method to calculate a valid map-number from the map-id.
         *
         * @param id the map-id, from which to extract the map-number.
         * @return the map-number.
         * @since 3.0
         * @see [hashId]
         */
        @JsStatic
        @JvmStatic
        fun mapNumber(id: String): Int {
           if (id == ADMIN_MAP) return ADMIN_MAP_NUMBER
           if (id == "0" || is31BitUnsigned.matches(id)) {
               try {
                   return id.toUInt(10).toInt()
               } catch (_: Exception) {}
           }
           val md5 = hashId(id)
           return md5.getInt32(12) or -2147483648
       }

        /**
         * A method to calculate a valid collection-number from the collection-id.
         *
         * @param id the collection-id, from which to extract the collection-number.
         * @return the collection-number.
         * @since 3.0
         * @see [hashId]
         */
        @JsStatic
        @JvmStatic
        fun collectionNumber(id: String): Int {
            val internalNumber = internalIdToNumber[id]
            if (id != ADMIN_MAP && internalNumber != null) return internalNumber
            if (id == "0" || is31BitUnsigned.matches(id)) {
                try {
                    return id.toUInt(10).toInt()
                } catch (_: Exception) {}
            }
            val md5 = hashId(id)
            return md5.getInt32(12) or -2147483648
        }

        /**
         * A method to calculate the feature-number (`fn`) from the feature-id.
         *
         * Actually, this method will try to detect if the feature-id is a 63-bit unsigned integer, if that is the case, it will convert this string into the corresponding positive 64-bit integer, and return it.
         *
         * Otherwise, it uses the [MD5](https://en.wikipedia.org/wiki/MD5) hash above the feature-id and return the lower 64-bit as feature-number, with the highest bit (sign-bit) always being cleared, which reserves all positive numbers for manually managed feature-numbers, which is compatible to what `Map-Hub` originally did. Considering the [birthday paradox](https://betterexplained.com/articles/understanding-the-birthday-paradox/), we can assume that for the maximum of 2^40 features in a collection, there will be around 65,000 collisions, when using 2^32 features _(4 billion)_ we only get 2 collisions, while for less than 1 billion features we will not encounter any collision _(or, it is unlikely)_.
         *
         * ### Collision handling
         * As collisions in feature numbers are not totally avoidable, the strategy in case of a collision should be to increment to the feature-number until an unused number is found, not modifying the lower 16-bit, which we use as [partition-number][partitionNumber]. The general approach is:
         * ```
         * new_fn = ((fn + 65536) & 0xffff_ffff_ffff_0000)
         *        | (fn & 0xffff) | 0x8000_0000_0000_0000
         * ```
         * A storage may provide a helper (e.g. `naksha_alt64`) that encapsulates this increment.
         *
         * ### Note
         * Generally, the estimated number of collisions is calculated as `n^2 / 2N` with `n` being the number of features and `N` being the entropy, so the maximum amount of numbers available _(so here 2^63)_. The collision possibility can be estimated via `1 - e^( -(n^2 / 2N) )`, for example, for 1 billion features it will be `1 - e^( -(2^60 / 2^64) )`, which results in around 6 percent, for 4 billion features it grows to `1 - e^( -(2^64 / 2^64) )` to around 63.2 percent, reaching 99.99% for around 147 billion features _(there is expected to be at least one collision)_. Beware, just because a collision is unlikely, does not mean there will be none!
         *
         * @param id the feature-id, from which to extract the feature-number.
         * @return the feature-number.
         * @see [hashId]
         * @see [alternativeInt64]
         */
        @JsStatic
        @JvmStatic
        fun featureNumber(id: String): Int64 {
            val internalNumber = internalIdToNumber[id]
            if (internalNumber != null) return internalNumber.toInt64()
            if (id == "0" || is63BitUnsigned.matches(id)) {
                try {
                    return id.toLong(10).toInt64()
                } catch (_: Exception) {}
            }
            val md5 = hashId(id)
            return md5.getInt64(8) or INT64_SIGN_BIT
        }

        /**
         * Test if the given 32-bit represents a number, generated from an [MD5](https://en.wikipedia.org/wiki/MD5) hash above the identifier.
         * @param number the number to test.
         * @return `true` if the given map- or collection-number was generated as hash above the identifier; `false` otherwise.
         */
        @JsName("isAutoNumber32")
        @JsStatic
        @JvmStatic
        fun isAutoNumber(number: Int): Boolean = (number and -2147483648) == -2147483648

        /**
         * Test if the given 64-bit represents a number, generated from an [MD5](https://en.wikipedia.org/wiki/MD5) hash above the identifier.
         * @param number the number to test.
         * @return `true` if the given storage- or feature-number was generated as hash above the identifier; `false` otherwise.
         */
        @JsName("isAutoNumber64")
        @JsStatic
        @JvmStatic
        fun isAutoNumber(number: Int64): Boolean = (number and INT64_SIGN_BIT) == INT64_SIGN_BIT

        /**
         * `0x8000_0000_0000_0000`, should be `-9223372036854775808`, but this does not work in Kotlin, only `-9223372036854775807 -1`?
         * - See [programmer calculator](https://devtools.calckit.io/programmer-calculator)
         */
        @JvmStatic
        internal val INT64_SIGN_BIT = Int64(Long.MIN_VALUE)

        /**
         * `0x7fff_ffff_ffff_ffff`
         * - See [programmer calculator](https://devtools.calckit.io/programmer-calculator)
         */
        @JvmStatic
        internal val INT64_CLEAR_SIGN_BIT = Int64(0x7fff_ffff_ffff_ffff)

        /**
         * `0x0000_0000_0000_ffff`
         * - See [programmer calculator](https://devtools.calckit.io/programmer-calculator)
         */
        @JvmStatic
        internal val INT64_CLEAR_HIGH48 = Int64(0x0000_0000_0000_ffff)

        /**
         * `0x0000_0000_ffff_ffff` aka `4294967295`
         * - See [programmer calculator](https://devtools.calckit.io/programmer-calculator)
         */
        @JvmStatic
        internal val INT64_CLEAR_HIGH32 = Int64(4294967295)

        /**
         * `0xff00_0000_0000_0000` aka `-72057594037927936`
         * - See [programmer calculator](https://devtools.calckit.io/programmer-calculator)
         */
        @JvmStatic
        internal val INT64_CLEAR_HIGH8 = Int64(-72057594037927936)

        /**
         * `0xffff_ffff_ffff_0000` aka `-65536`
         * - See [programmer calculator](https://devtools.calckit.io/programmer-calculator)
         */
        @JvmStatic
        internal val INT64_CLEAR_LOW16 = Int64(-65536)

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
        fun partitionNumber(featureNumber: Int64): Int = featureNumber.toInt() and 0xffff

        /**
         * Increment a 64-bit number _(storage- or feature-number)_ programmatically in case of collision, and return the _alternative_ number, derived deterministically from the given number. This method implements the same behavior as the SQL function `naksha_alt64`.
         *
         * ### Note
         * This method can be applied recursively until a new valid number has been found.
         *
         * @param number the number to calculate an alternative from.
         * @return the alternative number.
         * @since 3.0
         * @see [number]
         * @see [hashId]
         */
        @JsStatic
        @JvmStatic
        fun alternativeInt64(number: Int64): Int64
            = ((number + 65536) and INT64_CLEAR_LOW16) or (number and INT64_CLEAR_HIGH48) or INT64_SIGN_BIT

        /**
         * Increment a 32-bit number _(map- or collection-number)_ programmatically in case of collision, and return the _alternative_ number, derived deterministically from the given number. This method implements the same behavior as the SQL function `naksha_alt32`.
         *
         * ### Note
         * This method can be applied recursively until a new valid number has been found.
         *
         * @param number the number to calculate an alternative from.
         * @return the alternative number.
         * @since 3.0
         * @see [number]
         * @see [hashId]
         */
        @JsStatic
        @JvmStatic
        fun alternativeInt32(number: Int): Int = (number + 1) or -2147483648

        /**
         * Decode Naksha tags from their binary representation.
         * @param bytes the bytes to decode.
         * @param dictReader the dictionary manager to use for decoding; if any.
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
         * Encodes the given tag-list into the [set][naksha.model.objects.MemberType.SET]
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
         * - a JSON array ([set][naksha.model.objects.MemberType.SET], the default) is returned
         *   unmodified, preserving the element order;
         * - a JSON object ([naksha.model.objects.MemberType.TAGS_FROM_ARRAY]) is re-flattened via
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
        internal val storagesByNumber = AtomicMap<Int64, AbstractStorage<*>>()

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
            val s = storagesByNumber[storage.number] ?: return null
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
            var s = storagesByNumber[config.number]
            var s2 = storagesById[config.id]
            if (s !== s2) {
                lock.acquire().use {
                    s = storagesByNumber[config.number]
                    s2 = storagesById[config.id]
                    if (s !== s2) {
                        throw NakshaException(
                            ILLEGAL_ARGUMENT,
                            "The storage-id (${config.id}) and -number (${config.number}) belong to different storages")
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
                var storage = storagesByNumber[config.number]
                val storage2 = storagesById[config.id]
                if (storage !== storage2) {
                    throw NakshaException(
                        ILLEGAL_ARGUMENT,
                        "The storage-id (${config.id}) and -number (${config.number}) belong to different storages")
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
         * @since 3.0
         */
        @JvmStatic
        @JsStatic
        fun removeStorage(config: NakshaStorage): IStorage? {
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
         * The [tuple cache][TupleCache], usage like:
         * ```kotlin
         * // rs = ResultTupleList
         * val result = Naksha.cache.load(rs)
         * ```
         * ```java
         * // rs = ResultTupleList
         * final ResultTupleList result = Naksha.cache.load(rs, 0, rs.size())
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
    }
}
