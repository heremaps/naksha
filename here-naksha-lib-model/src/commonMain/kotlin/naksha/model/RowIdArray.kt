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
import naksha.model.request.ResultRow
import naksha.model.request.ResultRowList
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmField
import kotlin.jvm.JvmStatic

/**
 * A helper that wraps a byte-array that contains one to n row-ids.
 * ```sql
 * SELECT r AS (
 *   SELECT rowid, 1 as c FROM table1 WHERE ... LIMIT ...
 *   UNION ALL
 *   SELECT rowid, 2 as c FROM table2 WHERE ... LIMIT ...
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
data class RowIdArray(
    /**
     * The `rowid`.
     */
    @JvmField val rowid_arr: ByteArray,
    /**
     * The array to map a collection numbers with a collection name.
     */
    @JvmField val collections: Array<String>
) {
    private val elementSize: Int
    private val last: Int
    private val view: PlatformDataView
    init {
        if (collections.isEmpty()) {
            throw NakshaException(ILLEGAL_ARGUMENT, "Empty collection names")
        }
        elementSize = if (collections.size > 1) 20 else 16
        if (rowid_arr.size % elementSize != 0) {
            throw NakshaException(ILLEGAL_ARGUMENT, "Invalid rowid array, must be a multiple of $elementSize byte")
        }
        view = Platform.newDataView(rowid_arr)
        last = rowid_arr.size - elementSize
    }

    companion object RowIdArray_C {
        /**
         * Return a [RowIdArray] from a compressed byte-array.
         * @param compressed the compressed row-id array.
         * @param collections the array to map a collection numbers with a collection name.
         * @return the [RowIdArray].
         */
        @JvmStatic
        @JsStatic
        fun fromGzip(compressed: ByteArray, collections: Array<String>): RowIdArray
            = RowIdArray(Platform.gzipInflate(compressed), collections)
    }

    /**
     * Returns the amount of row-ids in the array.
     */
    val size: Int
        get() = rowid_arr.size / elementSize

    @Suppress("NOTHING_TO_INLINE")
    private inline fun offset(index: Int): Int = index * elementSize

    /**
     * Return the transaction number (aka [Version]) from the given index.
     * @param index the index.
     * @return the transaction number or _null_, if the index is out of bounds.
     */
    fun getTxn(index: Int): Int64? {
        val offset = offset(index)
        if (offset < 0 || offset > last) return null
        return dataview_get_int64(view, offset)
    }

    /**
     * Return the `uid` from the given index.
     * @param index the index.
     * @return the `uid` or _null_, if the index is out of bounds.
     */
    fun getUid(index: Int): Int? {
        val offset = offset(index)
        if (offset < 0 || offset > last) return null
        return dataview_get_int32(view, offset + 8)
    }

    /**
     * Return the `flags` from the given index.
     * @param index the index.
     * @return the `flags` or _null_, if the index is out of bounds.
     */
    fun getFlags(index: Int): Int? {
        val offset = offset(index)
        if (offset < 0 || offset > last) return null
        return dataview_get_int32(view, offset + 12)
    }

    /**
     * Return the collection from which the row is.
     * @param index the index.
     * @return the collection name or _null_, if the index is out of bounds.
     */
    fun getCollection(index: Int): String? {
        if (collections.size == 1) return collections[0]
        val offset = offset(index)
        if (offset < 0 || offset > last) return null
        val c = dataview_get_int32(view, offset + 16)
        return if (c >= 0 && c < collections.size) collections[c] else null
    }

    /**
     * Return the [RowId] from the given index.
     *
     * This method create a new instance of [RowId] and [Version], just to be aware, it does not cache this.
     * @param index the index.
     * @return the [RowId] or _null_, if the index is out of bounds.
     */
    fun getRowId(index: Int): RowId? {
        val offset = offset(index)
        if (offset < 0 || offset > last) return null
        return RowId(Version(dataview_get_int64(view, offset)),
            dataview_get_int32(view, offset + 8),
            dataview_get_int32(view, offset + 12))
    }

    /**
     * Returns the row-ids compressed (this is helpful for in-memory caching).
     * @return the compressed row-ids.
     */
    fun gzip(): ByteArray = Platform.gzipDeflate(rowid_arr)

    /**
     * Return this as native array.
     * @return an array of row-ids.
     */
    fun toArray(): Array<RowId> = Array(rowid_arr.size) { getRowId(it)!! }

    /**
     * Convert the row-ids into a result-row list.
     *
     * This method queries cached row entries.
     * @param storage the storage from which the row-ids are.
     * @param map the map from which the row-ids are.
     * @return the result-row list generated from the information.
     */
    fun toResultRowList(storage: IStorage, map: String): ResultRowList {
        val list = ResultRowList()
        val length = size
        list.setCapacity(length)
        var i = 0
        while (i < length) {
            val rowId = getRowId(i) ?: throw NakshaException(ILLEGAL_STATE, "Invalid rowId at index $i")
            val collectionId = getCollection(i) ?: throw NakshaException(ILLEGAL_STATE, "Invalid collection reference at index $i")
            val row = NakshaCache.rowCache(storage.id())[rowId]
            val resultRow = ResultRow(storage, map, collectionId, rowId, ExecutedOp.READ, row?.meta?.id, row)
            list.add(resultRow)
            i++
        }
        return list
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        other as RowIdArray
        return rowid_arr.contentEquals(other.rowid_arr)
    }
    override fun hashCode(): Int = rowid_arr.contentHashCode()
    override fun toString(): String = rowid_arr.contentToString()
}