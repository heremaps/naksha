@file:Suppress("OPT_IN_USAGE")

package naksha.model

import naksha.base.Int64
import naksha.base.Platform
import naksha.base.PlatformDataView
import naksha.base.PlatformDataViewApi.PlatformDataViewApiCompanion.dataview_get_int32
import naksha.base.PlatformDataViewApi.PlatformDataViewApiCompanion.dataview_get_int64
import naksha.model.NakshaError.NakshaErrorCompanion.ILLEGAL_ARGUMENT
import naksha.model.NakshaError.NakshaErrorCompanion.ILLEGAL_STATE
import naksha.model.request.ExecutedOp
import naksha.model.request.ResultTupleList
import naksha.model.request.ResultTupleList.ResultTupleList_C.fromTupleNumberArray
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.js.JsStatic
import kotlin.jvm.JvmField
import kotlin.jvm.JvmStatic

/**
 * A helper that wraps a byte-array that contains one to n tuple-numbers, all either with storage-number or without. This implementation does not allow mixing tuple-numbers with, and without storage-number, so it requires that all encodings are of the same size.
 * ```sql
 * SELECT r AS (
 *   SELECT tuple_number FROM table1 WHERE ... LIMIT ...
 *   UNION ALL
 *   SELECT tuple_number FROM table2 WHERE ... LIMIT ...
 *   ...
 * )
 * SELECT gzip(bytea_agg(tuple_number)) FROM r LIMIT 1000000
 * ```
 * @since 3.0.0
 */
@JsExport
data class TupleNumberByteArray(
    /**
     * The binary, uncompressed byte-array.
     * @see [fromGzip]
     * @since 3.0.0
     */
    @JvmField val binary: ByteArray,

    /**
     * The storage-number to use, if the byte-array does not encode any.
     * - Throws [NakshaError.ILLEGAL_ARGUMENT] If the storage-number is _null_, and no storage-number is encoded in the given byte-array, or the given array is not of a valid size.
     * @since 3.0.0
     */
    @JvmField val storageNumber: Int64? = null
) {
    /**
     * Create a tuple-number byte-array wrapper that comes from a specific storage.
     * @param binary the byte-array to wrap.
     * @param storage the storage that returned the byte-array.
     */
    @JsName("fromStorage")
    constructor(binary: ByteArray, storage: IStorage): this(binary, storage.number)

    private val view: PlatformDataView = Platform.newDataView(binary)
    private val entrySize: Int = if (binary.size >= 24) {
        val first_flags = dataview_get_int32(view, 20)
        if (first_flags.storageNumber()) 32 else 24
    } else 32
    init {
        if (binary.size % entrySize != 0) {
            throw NakshaException(ILLEGAL_ARGUMENT, "Invalid tuple-number array, must be a multiple of $entrySize byte")
        }
        if (entrySize == 24 && storageNumber == null) {
            throw NakshaException(ILLEGAL_ARGUMENT, "Invalid tuple-number array, does not encode storage-number")
        }
    }
    private val last: Int = binary.size - entrySize
    private fun storageNum(): Int64
        = storageNumber ?: throw NakshaException(ILLEGAL_STATE, "No storage-number in encoding and constructor argument")

    companion object TupleNumberByteArray_C {
        /**
         * Return a [TupleNumberByteArray] from a compressed byte-array.
         * @param storage the storage from which the tuple-number is.
         * @param compressed the compressed tuple-number array.
         * @return the [TupleNumberByteArray].
         * @since 3.0.0
         */
        @JvmStatic
        @JsStatic
        fun fromGzip(storage: IStorage, compressed: ByteArray): TupleNumberByteArray
            = TupleNumberByteArray(Platform.gzipInflate(compressed), storage.number)
    }

    /**
     * Returns the amount of row-ids in the array.
     */
    val size: Int
        get() = binary.size / entrySize

    @Suppress("NOTHING_TO_INLINE")
    private inline fun offset(index: Int): Int = index * entrySize

    /**
     * Tests if this byte-array encodes individual storage-numbers for each tuple-number, so each tuple-number is encoded in 256-bit, or all tuple-numbers are encoded storage local, that means they are encoded in 192-bit.
     *
     * If each tuple-number has an own storage-number, then the result-set comes possibly from different storages, but this is not guaranteed.
     * @return _true_ if the underlying binary contains the storage-numbers; _false_ all come from the same storage.
     */
    fun encodesStorageNumber(): Boolean = entrySize == 32

    /**
     * Returns the tuple-number from the given index.
     * @param index the index.
     * @return the tuple-number or _null_, if out of bounds.
     * @since 3.0.0
     */
    operator fun get(index: Int): TupleNumber? {
        val offset = offset(index)
        if (offset < 0 || offset > last) return null
        val storeNumber = dataview_get_int64(view, offset + 0)
        val version = Version(dataview_get_int64(view, offset + 8))
        val uid = dataview_get_int32(view, offset + 16)
        val flags = dataview_get_int32(view, offset + 20)
        val storageNumber = if (entrySize == 32) dataview_get_int64(view, offset + 24) else storageNum()
        return TupleNumber(storageNumber, storeNumber, version, uid, flags.storageNumber(false))
    }

    /**
     * Returns the storage-number from the given index.
     * @param index the index.
     * @return the storage-number or _null_, if out of bounds.
     * @since 3.0.0
     */
    fun getStorageNumber(index: Int): Int64? {
        val offset = offset(index)
        if (offset < 0 || offset > last) return null
        return if (entrySize == 32) dataview_get_int64(view, offset + 24) else storageNum()
    }

    /**
     * Returns the store-number from the given index.
     * @param index the index.
     * @return the store-number or _null_, if out of bounds.
     * @since 3.0.0
     */
    fun getStoreNumber(index: Int): StoreNumber? {
        val offset = offset(index)
        if (offset < 0 || offset > last) return null
        return dataview_get_int64(view, offset + 0)
    }

    /**
     * Returns the transaction-number from the given index.
     * @param index the index.
     * @return the transaction-number or _null_, if out of bounds.
     * @since 3.0.0
     */
    fun getTxn(index: Int): Int64? {
        val offset = offset(index)
        if (offset < 0 || offset > last) return null
        return dataview_get_int64(view, offset + 8)
    }

    /**
     * Returns the uid at the given index.
     * @param index the index.
     * @return the uid or _null_, if out of bounds.
     * @since 3.0.0
     */
    fun getUid(index: Int): Int? {
        val offset = offset(index)
        if (offset < 0 || offset > last) return null
        return dataview_get_int32(view, offset + 16)
    }

    /**
     * Returns the [flags][Flags] from the given index.
     * @param index the index.
     * @return the [flags][Flags] or _null_, if out of bounds.
     * @since 3.0.0
     */
    fun getFlags(index: Int): Flags? {
        val offset = offset(index)
        if (offset < 0 || offset > last) return null
        return dataview_get_int32(view, offset + 20)
    }

    /**
     * Compress this byte-array and return the compressed version (this is helpful for caching).
     * @return the compressed tuple-number array.
     * @since 3.0.0
     */
    fun gzip(): ByteArray = Platform.gzipDeflate(binary)

    /**
     * Unpack this into an array of [tuple-numbers][TupleNumber].
     * @return an array of [tuple-numbers][TupleNumber].
     * @since 3.0.0
     */
    fun toArray(): Array<TupleNumber> = Array(binary.size) { get(it)!! }

    /**
     * Calculate the MD5 hash above the [binary].
     * @return the MD5 hash above the [binary].
     * @since 3.0.0
     */
    fun md5(): ByteArray = Platform.md5(binary)

    /**
     * Helper method to convert this tuple-number array into a [ResultTupleList].
     *
     * @param executedOp the [ExecutedOp] to set in all tuples; when _null_, it will be set based upon the [Action].
     * @return the [ResultTupleList].
     * @since 3.0.0
     */
    fun toResultRowList(executedOp: ExecutedOp? = null): ResultTupleList
        = fromTupleNumberArray(this, executedOp)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        other as TupleNumberByteArray
        return binary.contentEquals(other.binary)
    }
    override fun hashCode(): Int = binary.contentHashCode()
    override fun toString(): String = binary.contentToString()
}