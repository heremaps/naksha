@file:Suppress("OPT_IN_USAGE")

package naksha.model

import naksha.base.Int64
import naksha.base.Platform
import naksha.base.PlatformDataView
import naksha.base.PlatformDataViewApi.PlatformDataViewApiCompanion.dataview_get_int32
import naksha.base.PlatformDataViewApi.PlatformDataViewApiCompanion.dataview_get_int64
import naksha.model.NakshaError.NakshaErrorCompanion.ILLEGAL_ARGUMENT
import naksha.model.request.ExecutedOp
import naksha.model.request.ResultTupleList
import naksha.model.request.ResultTupleList.ResultTupleList_C.fromTupleNumberArray
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmField
import kotlin.jvm.JvmStatic

/**
 * A helper that wraps a byte-array that contains binary encoded [tuple-numbers][TupleNumber].
 *
 * There are two forms of the byte-array. Either the lead-in (the first 64-bit) is `-1` (so all bits are set), then the [storage-number][TupleNumber.storageNumber] is encoded individually for each [tuple-number][TupleNumber], resulting in 256-bit encoding (32 byte) per [tuple-number][TupleNumber]. Otherwise, the storage-number is shared by all [tuple-numbers][TupleNumber], and stored in the first 64-bits, then each encoded [tuple-number][TupleNumber] is only 192-bit (24 byte).
 *
 * The second encoding is mostly the result from selecting [tuple-numbers][TupleNumber] from the database, for example like:
 * ```sql
 * SELECT gzip(int8send(naksha_storage_number())||bytea_agg(tuple_number||int4send(flags)) AS rs FROM (SELECT tuple_number FROM (
 *   (SELECT tuple_number, id FROM {collection} WHERE {condition})
 *   UNION ALL
 *   (SELECT tuple_number, id FROM {collection} WHERE {condition})
 *   ...
 * ) ORDER BY id, tuple_number) LIMIT 1000000;
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
    @JvmField val binary: ByteArray
) {
    private val view: PlatformDataView = Platform.newDataView(binary)
    private val storageNumber: Int64 = if (binary.size >= 8) dataview_get_int64(view, 0) else FULL_VARIANT
    private val entrySize: Int = if (storageNumber >= 0) 24 else 32
    init {
        // Ensure that no invalid state is produced!
        if (binary.isNotEmpty() && ((binary.size - 8) % entrySize != 0)) {
            throw NakshaException(ILLEGAL_ARGUMENT, "Invalid tuple-number array, must be a multiple of $entrySize byte plus 8")
        }
        if (storageNumber < -1) {
            throw NakshaException(ILLEGAL_ARGUMENT, "Invalid tuple-number array, lead-in is invalid number: $storageNumber")
        }
    }
    private val last: Int = if (binary.size < entrySize) 0 else binary.size - entrySize

    /**
     * The amount of [tuple-numbers][TupleNumber] in the array.
     * @since 3.0.0
     */
    val size: Int = if (binary.size < 8) 0 else (binary.size - 8) / entrySize

    @Suppress("NOTHING_TO_INLINE")
    private inline fun offset(index: Int): Int = 8 + index * entrySize

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
        val storageNumber = if (entrySize == 32) dataview_get_int64(view, offset + 24) else storageNumber
        return TupleNumber(storageNumber, storeNumber, version, uid, flags)
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
        return if (entrySize == 32) dataview_get_int64(view, offset + 24) else storageNumber
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

    companion object TupleNumberByteArray_C {
        /**
         * The encoding in the lead-in, when the storage-number is not shared, but coded individually for each [tuple-number][TupleNumber].
         * @since 3.0.0
         */
        @JvmStatic
        @JsStatic
        val FULL_VARIANT = Int64(-1)

        /**
         * Return a [TupleNumberByteArray] from a compressed byte-array.
         * @param compressed the compressed tuple-number array.
         * @return the [TupleNumberByteArray].
         * @since 3.0.0
         */
        @JvmStatic
        @JsStatic
        fun fromGzip(compressed: ByteArray): TupleNumberByteArray = TupleNumberByteArray(Platform.gzipInflate(compressed))
    }
}