@file:Suppress("OPT_IN_USAGE")

package naksha.model

import naksha.base.AtomicMap
import naksha.base.Int64
import naksha.base.Platform
import naksha.base.WeakRef
import naksha.jbon.IDictManager
import naksha.model.NakshaError.NakshaErrorCompanion.ILLEGAL_ARGUMENT
import naksha.model.NakshaError.NakshaErrorCompanion.ILLEGAL_STATE
import naksha.model.NakshaError.NakshaErrorCompanion.STORAGE_NOT_FOUND
import naksha.model.NakshaError.NakshaErrorCompanion.DICT_MANAGER_NOT_FOUND
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.js.JsStatic
import kotlin.jvm.JvmStatic

/**
 * A global cache that holds [storages][IStorage], [dictionary-managers][IDictManager], and [Tuple].
 *
 * As long as a [tuples][Tuple] does not require a global (shared) dictionary, it can be decoded and encoded using [decodeTuple][Naksha.decodeTuple] or [encodeTuple][Naksha.encodeTuple] without any further needs. As soon as a [Tuple] requires a global (shared) dictionary, or a [Tuple] should be encoded optimised for a specific [storage][IStorage], the encoder/decoder will query for the [dictionary-manager][IDictManager] in this cache via [getDictManager] or [useDictManager]. In the case of encoding, a missing [dictionary-manager][IDictManager] will only increase the size of the encoding with no other harm. When decoding, a missing [dictionary-manager][IDictManager] will raise an [DICT_MANAGER_NOT_FOUND] exception. So, if a [Tuple] is read from some cache, and the corresponding origin [storage][IStorage] is not available, then the managing application should register some own [dictionary-manager][IDictManager], so that the corresponding dictionary can be read from the cache too.
 * @since 3.0.0
 */
@JsExport
class NakshaCache private constructor() {
    companion object PgCache_C {
        private val lock = Platform.newLock()
        private val dictManagerByStoreNumber = AtomicMap<Int64, IDictManager>()
        private val storagesById = AtomicMap<String, IStorage>()
        private val storagesByNumber = AtomicMap<Int64, IStorage>()
        private val tupleCacheByStorageNumber = AtomicMap<Int64, WeakRef<TupleCache>>()

        /**
         * Add the given storage into the cache.
         *
         * This method is called by [IStorage.initStorage].
         * @param storage the storage to add.
         * @return the added storage.
         * @since 3.0.0
         */
        @JvmStatic
        @JsStatic
        fun addStorage(storage: IStorage): IStorage {
            lock.acquire().use {
                val existing = storagesById.putIfAbsent(storage.id, storage)
                if (existing != null) {
                    if (existing === storage) return storage // This storage was already added.
                    throw NakshaException(ILLEGAL_STATE, "Another storage with the same id ('${storage.id}') is registered already, existing number: ${existing.number}, provided number: ${storage.number}")
                }
                storagesByNumber[storage.number] = storage
            }
            return storage
        }

        /**
         * Remove the given storage from the cache, instantly removes all cached [Tuple].
         * @param storage the storage to remove.
         * @return the removed storage.
         * @since 3.0.0
         */
        @JvmStatic
        @JsStatic
        fun removeStorage(storage: IStorage): IStorage {
            lock.acquire().use {
                if (storagesById.remove(storage.id, storage)) {
                    storagesByNumber.remove(storage.number)
                }
                tupleCacheByStorageNumber.remove(storage.number)
            }
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
         * Returns the storage with the given number.
         * - Throws [NakshaError.STORAGE_NOT_FOUND], if no such storage is added to the [NakshaCache].
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
         * - Throws [NakshaError.STORAGE_NOT_FOUND], if no such storage is added to the [NakshaCache].
         * @param storageNumber the storage-number.
         * @return the storage.
         */
        @JvmStatic
        @JsStatic
        fun useStorage(storageNumber: Int64): IStorage = storagesByNumber[storageNumber]
            ?: throw NakshaException(STORAGE_NOT_FOUND, "No storage found for storage-number: $storageNumber", id=storageNumber.toString())

        /**
         * Add the given [dictionary-manager][IDictManager] into the cache, so that tuples loaded from this storage can be encoded and decoded.
         * @param storageNumber the storage-number for which to add a specific [dictionary-manager][IDictManager].
         * @param dictManager the [dictionary-manager][IDictManager] to add.
         * @return the given [dictionary-manager][IDictManager].
         * @since 3.0.0
         */
        @JvmStatic
        @JsStatic
        fun addDictManager(storageNumber: Int64, dictManager: IDictManager): IDictManager {
            lock.acquire().use {
                val existing = dictManagerByStoreNumber.putIfAbsent(storageNumber, dictManager)
                if (existing != null) {
                    if (existing === dictManager) return dictManager // This codec was already added.
                    throw NakshaException(ILLEGAL_STATE, "Another dictionary-manager is already registered for the same storage-number: $storageNumber")
                }
                dictManagerByStoreNumber[storageNumber] = dictManager
            }
            return dictManager
        }

        /**
         * Remove the given [dictionary-manager][IDictManager] from the cache.
         * @param storageNumber the storage-number for which the [IDictManager] was added.
         * @param tupleCodec the [dictionary-manager][IDictManager] to remove.
         * @return the given [dictionary-manager][IDictManager].
         */
        @JvmStatic
        @JsStatic
        fun removeDictManager(storageNumber: Int64, tupleCodec: IDictManager): IDictManager {
            dictManagerByStoreNumber.remove(storageNumber, tupleCodec)
            return tupleCodec
        }

        /**
         * Returns the [dictionary-manager][IDictManager] for the given storage-number.
         * @param storageNumber the storage-number.
         * @return the [IDictManager], if any is available.
         * @since 3.0.0
         */
        @JvmStatic
        @JsStatic
        fun getDictManager(storageNumber: Int64): IDictManager? = dictManagerByStoreNumber[storageNumber]

        /**
         * Returns the [dictionary-manager][IDictManager] for the given storage-number. If no dedicated [dictionary-manager][IDictManager] is registered, queries for the [storage][IStorage] and returns this. If neither is available, throws an [NakshaError.DICT_MANAGER_NOT_FOUND].
         *
         * To not throw an exception, this can be replaced with:
         *
         * `getDictManager(storageNumber) ?: getStorage(storageNumber)`
         * @param storageNumber the storage-number.
         * @return the [IDictManager], if any is available.
         * @since 3.0.0
         */
        @JvmStatic
        @JsStatic
        fun useDictManager(storageNumber: Int64): IDictManager =
            dictManagerByStoreNumber[storageNumber] ?: getStorage(storageNumber)
            ?: throw NakshaException(DICT_MANAGER_NOT_FOUND, "No dictionary-manager for storage-number $storageNumber")

        /**
         * Store the given tuple in the cache.
         * @param tuple the [Tuple] to store.
         * @return the cached [Tuple], which may not be the one given, but a merged version.
         * @since 3.0.0
         */
        @JvmStatic
        @JsStatic
        fun store(tuple: Tuple): Tuple = useTupleCache(tuple.storageNumber).store(tuple)

        /**
         * Returns the cache for all tuples of a specific storage.
         * @param storage the storage for which to return the [TupleCache].
         * @return the [TupleCache] for the storage.
         * @since 3.0.0
         */
        @JvmStatic
        @JsStatic
        @JsName("getTupleCacheByStorage")
        fun getTupleCache(storage: IStorage): TupleCache = useTupleCache(storage.number)

        /**
         * Returns the cache for all tuples of a specific storage.
         * @param storageId the storage-id of the storage for which to return the [TupleCache].
         * @return the [TupleCache] for the storage.
         * @since 3.0.0
         */
        @JvmStatic
        @JsStatic
        @JsName("getTupleCacheByStorageId")
        fun getTupleCache(storageId: String): TupleCache? {
            val storageNumber = storagesById[storageId]?.number ?: return null
            return useTupleCache(storageNumber)
        }

        /**
         * Returns the cache for all rows of a specific storage.
         * @param storageNumber the storage-number of the storage for which to return the [row-cache][TupleCache].
         * @return the [row-cache][TupleCache] for the storage.
         * @since 3.0.0
         */
        @JvmStatic
        @JsStatic
        @JsName("getTupleCacheByStorageNumber")
        fun getTupleCache(storageNumber: Int64): TupleCache? = tupleCacheByStorageNumber[storageNumber]?.deref()

        /**
         * Returns the [TupleCache] for all [TupleCache] of a specific storage.
         *
         * If no such cache exists yet, creates a new cache.
         * @param storageNumber the storage-number of the storage for which to return the [TupleCache].
         * @return the [TupleCache] for the storage-number.
         * @since 3.0.0
         */
        @JvmStatic
        @JsStatic
        tailrec fun useTupleCache(storageNumber: Int64): TupleCache {
            var ref = tupleCacheByStorageNumber[storageNumber]
            var cache = ref?.deref()
            if (cache != null) return cache
            if (ref != null) {
                tupleCacheByStorageNumber.remove(storageNumber, ref)
            }
            cache = TupleCache(storageNumber)
            ref = WeakRef(cache)
            if (tupleCacheByStorageNumber.putIfAbsent(storageNumber, ref) == null) return cache
            return useTupleCache(storageNumber)
        }

        /**
         * Returns the cached-tuple.
         * @param tupleNumber the [TupleNumber] to lookup.
         * @return the [Tuple], if found in cache.
         * @since 3.0.0
         */
        @JvmStatic
        @JsStatic
        operator fun get(tupleNumber: TupleNumber): Tuple? = tupleCacheByStorageNumber[tupleNumber.storageNumber]?.deref()?.get(tupleNumber)

        /**
         * Store the given tuple in the cache.
         * @param tupleNumber the [TupleNumber] of the tuple.
         * @param tuple the [Tuple] to store.
         * @return the cached [Tuple], which may not be the one give, but a merged version.
         * @since 3.0.0
         */
        @JvmStatic
        @JsStatic
        operator fun set(tupleNumber: TupleNumber, tuple: Tuple): Tuple {
            if (tupleNumber != tuple.meta.tupleNumber()) {
                throw NakshaException(ILLEGAL_ARGUMENT, "Given key does not match tuple.tupleNumber")
            }
            return useTupleCache(tupleNumber.storageNumber).store(tuple)
        }

        /**
         * Tests if the cache contains a row with the given id.
         * @param tupleNumber the[TupleNumber].
         * @return _true_ if the tuple is contained in cache; _false_ otherwise.
         * @since 3.0.0
         */
        @JvmStatic
        @JsStatic
        operator fun contains(tupleNumber: TupleNumber): Boolean {
            val ref = tupleCacheByStorageNumber[tupleNumber.storageNumber]
            val cache = ref?.deref() ?: return false
            return cache.contains(tupleNumber)
        }

        /**
         * Remove (evict) the cached [Tuple].
         * @param tupleNumber the [TupleNumber] of the [Tuple] to remove.
         * @return the removed [Tuple]; if any.
         * @since 3.0.0
         */
        @JvmStatic
        @JsStatic
        fun remove(tupleNumber: TupleNumber): Tuple? {
            val ref = tupleCacheByStorageNumber[tupleNumber.storageNumber]
            val cache = ref?.deref() ?: return null
            return cache.remove(tupleNumber)
        }

        /**
         * A helper to clear caches, this method should be called by the host ones in a while, like ones every 15 minutes, using a dedicated background job. It helps to remove garbage from the caches (remainders that are not weak-references, when the counterparts have been collection).
         *
         * @since 3.0.0
         */
        @JvmStatic
        @JsStatic
        fun gc() {
            for ((key, cache_ref) in tupleCacheByStorageNumber) {
                val cache = cache_ref.deref()
                if (cache == null) {
                    tupleCacheByStorageNumber.remove(key, cache_ref)
                } else cache.gc()
            }
        }
    }
}