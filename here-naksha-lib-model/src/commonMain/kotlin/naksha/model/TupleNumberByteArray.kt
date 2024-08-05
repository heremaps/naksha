@file:Suppress("OPT_IN_USAGE")

package naksha.model

import naksha.base.Int64
import naksha.base.Platform
import naksha.base.PlatformDataView
import naksha.base.PlatformDataViewApi.PlatformDataViewApiCompanion.dataview_get_int32
import naksha.base.PlatformDataViewApi.PlatformDataViewApiCompanion.dataview_get_int64
import naksha.model.NakshaError.NakshaErrorCompanion.ILLEGAL_ARGUMENT
import naksha.model.request.ResultTupleList
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmField
import kotlin.jvm.JvmStatic

/**
 * A helper that wraps a byte-array that contains one to n row-ids.
 * ```sql
 * SELECT r AS (
 *   SELECT rowid, ... FROM table1 WHERE ... LIMIT ...
 *   UNION ALL
 *   SELECT rowid, ... FROM table2 WHERE ... LIMIT ...
 *   ...
 * )
 * SELECT gzip(array_agg(rowid||int4send(c))) FROM r
 * ```
 * If only one collection is selected, this should be done:
 * ```sql
 * SELECT r AS (
 *   SELECT rowid FROM table1 WHERE ... LIMIT ...
 *   UNION ALL
 *   SELECT rowid FROM table2 WHERE ... LIMIT ...
 *   ...
 * )
 * SELECT gzip(array_agg(rowid)) FROM r
 * ```
 */
@JsExport
data class TupleNumberByteArray(
    /**
     * The storage from which the row-number was read.
     */
    @JvmField val storage: IStorage,

    /**
     * The binary.
     */
    @JvmField val binary: ByteArray
) {
    private val view: PlatformDataView = Platform.newDataView(binary)
    private val last: Int = binary.size - 20
    init {
        if (binary.size % 20 != 0) {
            throw NakshaException(ILLEGAL_ARGUMENT, "Invalid rowid array, must be a multiple of 20 byte")
        }
    }

    companion object TupleNumberByteArray_C {
        /**
         * Return a [TupleNumberByteArray] from a compressed byte-array.
         * @param storage the storage from which the row-number is.
         * @param compressed the compressed row-number array.
         * @return the [TupleNumberByteArray].
         */
        @JvmStatic
        @JsStatic
        fun fromGzip(storage: IStorage, compressed: ByteArray): TupleNumberByteArray
            = TupleNumberByteArray(storage, Platform.gzipInflate(compressed))
    }

    /**
     * Returns the amount of row-ids in the array.
     */
    val size: Int
        get() = binary.size / 20

    @Suppress("NOTHING_TO_INLINE")
    private inline fun offset(index: Int): Int = index * 20

    /**
     * Returns the row-number at the given index.
     * @param index the index.
     * @return the row-number or _null_, if out of bounds.
     */
    operator fun get(index: Int): TupleNumber? {
        val offset = offset(index)
        if (offset < 0 || offset > last) return null
        return TupleNumber(
            dataview_get_int64(view, offset),
            Version(dataview_get_int64(view, offset + 8)),
            dataview_get_int32(view, offset + 16)
        )
    }

    /**
     * Returns the store-number at the given index.
     * @param index the index.
     * @return the store-number or _null_, if out of bounds.
     */
    fun getStoreNumber(index: Int): StoreNumber? {
        val offset = offset(index)
        if (offset < 0 || offset > last) return null
        return dataview_get_int64(view, offset)
    }

    /**
     * Returns the transaction-number at the given index.
     * @param index the index.
     * @return the transaction-number or _null_, if out of bounds.
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
     */
    fun getUid(index: Int): Int? {
        val offset = offset(index)
        if (offset < 0 || offset > last) return null
        return dataview_get_int32(view, offset + 16)
    }

    /**
     * Returns the row-ids compressed (this is helpful for in-memory caching).
     * @return the compressed row-ids.
     */
    fun gzip(): ByteArray = Platform.gzipDeflate(binary)

    /**
     * Return this as native array.
     * @return an array of row-ids.
     */
    fun toArray(): Array<TupleNumber> = Array(binary.size) { get(it)!! }

    fun toResultRowList(storage: IStorage): ResultTupleList = ResultTupleList.fromRowNumberArray(storage, this)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        other as TupleNumberByteArray
        return binary.contentEquals(other.binary)
    }
    override fun hashCode(): Int = binary.contentHashCode()
    override fun toString(): String = binary.contentToString()
}