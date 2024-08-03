@file:Suppress("OPT_IN_USAGE")

package naksha.model.request

import naksha.base.ListProxy
import naksha.model.*
import naksha.model.NakshaError.NakshaErrorCompanion.COLLECTION_NOT_FOUND
import naksha.model.NakshaError.NakshaErrorCompanion.ILLEGAL_STATE
import naksha.model.NakshaError.NakshaErrorCompanion.MAP_NOT_FOUND
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmStatic

/**
 * A list of result rows.
 */
@JsExport
class ResultRowList : ListProxy<ResultRow>(ResultRow::class) {
    companion object ResultRowList_C {
        /**
         * Read the given binary row array, and convert it into a result-row list.
         * @param storage the storage from which the binary was received.
         * @param array the row binary.
         * @return the given binary converted into a list of result-rows.
         */
        @JvmStatic
        @JsStatic
        fun fromRowNumberArray(storage: IStorage, array: RowNumberByteArray): ResultRowList {
            val list = ResultRowList()
            val length = array.size
            list.setCapacity(length)
            var i = 0
            while (i < length) {
                val rowNumber = array[i] ?: throw NakshaException(ILLEGAL_STATE, "Invalid row-number at index $i")
                val mapNumber = rowNumber.mapNumber()
                val colNumber = rowNumber.collectionNumber()
                val mapId = storage.getMapId(mapNumber) ?: throw NakshaException(MAP_NOT_FOUND, "Map #$mapNumber not found at index $i")
                val map = storage[mapId]
                val colId = map.getCollectionId(colNumber) ?: throw NakshaException(COLLECTION_NOT_FOUND, "Collection #$colNumber not found at index $i")
                val rowCache = NakshaCache.rowCache(storage.id())
                val row = rowCache[rowNumber]
                val resultRow = ResultRow(storage, mapId, colId, rowNumber, ExecutedOp.READ, row?.meta?.id, row)
                list.add(resultRow)
                i++
            }
            return list
        }
    }
}
