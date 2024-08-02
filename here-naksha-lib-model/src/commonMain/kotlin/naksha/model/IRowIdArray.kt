@file:Suppress("OPT_IN_USAGE")

package naksha.model

import naksha.base.Int64
import naksha.model.request.ResultRowList
import kotlin.js.JsExport

/**
 * An interface to an array of [row-ids][RowId].
 */
@JsExport
interface IRowIdArray {
    /**
     * The amount of [row-ids][RowId] being part of this array.
     */
    val size: Int

    /**
     * Return the transaction number (aka [Version]) from the given index.
     * @param index the index.
     * @return the transaction number or _null_, if the index is out of bounds.
     */
    fun getTxn(index: Int): Int64?

    /**
     * Return the `uid` from the given index.
     * @param index the index.
     * @return the `uid` or _null_, if the index is out of bounds.
     */
    fun getUid(index: Int): Int?

    /**
     * Return the `flags` from the given index.
     * @param index the index.
     * @return the `flags` or _null_, if the index is out of bounds.
     */
    fun getFlags(index: Int): Int?

    /**
     * Return the collection from which the row is.
     * @param index the index.
     * @return the collection name or _null_, if the index is out of bounds.
     */
    fun getCollection(index: Int): String?

    /**
     * Return the [RowId] from the given index.
     *
     * This method create a new instance of [RowId] and [Version], just to be aware, it does not cache this.
     * @param index the index.
     * @return the [RowId] or _null_, if the index is out of bounds.
     */
    fun getRowId(index: Int): RowId?

    /**
     * Convert the row-ids into a result-row list.
     *
     * This method queries cached row entries.
     * @param storage the storage from which the row-ids are.
     * @param map the map from which the row-ids are.
     * @return the result-row list generated from the information.
     */
    fun toResultRowList(storage: IStorage, map: String): ResultRowList
}