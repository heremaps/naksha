@file:Suppress("OPT_IN_USAGE")

package naksha.model

import naksha.base.AtomicMap
import naksha.base.Int64
import naksha.base.Platform
import naksha.base.WeakRef
import naksha.model.NakshaError.NakshaErrorCompanion.ILLEGAL_ARGUMENT
import naksha.model.NakshaError.NakshaErrorCompanion.ILLEGAL_STATE
import naksha.model.NakshaError.NakshaErrorCompanion.STORAGE_NOT_FOUND
import naksha.model.NakshaError.NakshaErrorCompanion.TUPLE_CODEC_NOT_FOUND
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.js.JsStatic
import kotlin.jvm.JvmStatic

/**
 * All kind of global caches.
 * @since 3.0.0
 */
@JsExport
class NakshaCache private constructor() {
    companion object PgCache_C {
        private val lock = Platform.newLock()
        private val tupleCodecByStoreNumber = AtomicMap<Int64, ITupleCodec>()
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
         * Add the given [ITupleCodec] into the cache, so that tuples loaded from this storage can be encoded and decoded.
         * @param storageNumber the storage-number for which to add a specific [ITupleCodec].
         * @param tupleCodec the [ITupleCodec] to add.
         * @return the given [ITupleCodec].
         * @since 3.0.0
         */
        @JvmStatic
        @JsStatic
        fun addTupleCodec(storageNumber: Int64, tupleCodec: ITupleCodec): ITupleCodec {
            lock.acquire().use {
                val existing = tupleCodecByStoreNumber.putIfAbsent(storageNumber, tupleCodec)
                if (existing != null) {
                    if (existing === tupleCodec) return tupleCodec // This codec was already added.
                    throw NakshaException(ILLEGAL_STATE, "Another codec is already registered for the same storage-number: $storageNumber")
                }
                tupleCodecByStoreNumber[storageNumber] = tupleCodec
            }
            return tupleCodec
        }

        /**
         * Remove the given [ITupleCodec] from the cache.
         * @param storageNumber the storage-number for which the [ITupleCodec] was added.
         * @param tupleCodec the [ITupleCodec] to remove.
         * @return the given [ITupleCodec].
         */
        @JvmStatic
        @JsStatic
        fun removeTupleCodec(storageNumber: Int64, tupleCodec: ITupleCodec): ITupleCodec {
            tupleCodecByStoreNumber.remove(storageNumber, tupleCodec)
            return tupleCodec
        }

        /**
         * Returns the tuple-codec for the given storage-number.
         * @param storageNumber the storage-number.
         * @return the [ITupleCodec], if any is available.
         * @since 3.0.0
         */
        @JvmStatic
        @JsStatic
        fun getTupleCodec(storageNumber: Int64): ITupleCodec? {
            val codec = getStorage(storageNumber)
            return codec ?: tupleCodecByStoreNumber[storageNumber]
        }

        /**
         * Returns the tuple-codec for the given storage-number.
         * - Throws [NakshaError.TUPLE_CODEC_NOT_FOUND], if no such codec is found.
         * @param storageNumber the storage-number.
         * @return the [ITupleCodec], if any is available.
         * @since 3.0.0
         */
        @JvmStatic
        @JsStatic
        fun useTupleCodec(storageNumber: Int64): ITupleCodec {
            val codec = getStorage(storageNumber)
            return codec
                ?: tupleCodecByStoreNumber[storageNumber]
                ?: throw NakshaException(TUPLE_CODEC_NOT_FOUND, "No codec for storage-number $storageNumber")
        }

        /**
         * Store the given tuple in the cache.
         * @param tuple the [Tuple] to store.
         * @return the cached [Tuple], which may not be the one give, but a merged version.
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
        operator fun get(tupleNumber: TupleNumber): Tuple? = useTupleCache(tupleNumber.storageNumber)[tupleNumber]

        /**
         * Store the given tuple in the cache.
         * @param tupleNumber the [TupleNumber] of the tuple, basically [Tuple.tupleNumber].
         * @param tuple the [Tuple] to store.
         * @return the cached [Tuple], which may not be the one give, but a merged version.
         * @since 3.0.0
         */
        @JvmStatic
        @JsStatic
        operator fun set(tupleNumber: TupleNumber, tuple: Tuple): Tuple {
            if (tupleNumber != tuple.tupleNumber) {
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
    }
}