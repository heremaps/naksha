@file:Suppress("OPT_IN_USAGE")

package naksha.model.request

import naksha.base.ListProxy
import naksha.model.*
import naksha.model.NakshaError.NakshaErrorCompanion.ILLEGAL_STATE
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmStatic

/**
 * A list of result rows.
 */
@JsExport
class ResultTupleList : ListProxy<ResultTuple>(ResultTuple::class) {
    companion object ResultTupleList_C {
        /**
         * Read the given binary tuple-number byte-array, and convert it into a result-tuple list.
         * @param storage the storage from which the binary was received.
         * @param array the tuple-number binary.
         * @return the given binary converted into a list of result-tuples.
         */
        @JvmStatic
        @JsStatic
        fun fromTupleNumberArray(storage: IStorage, array: TupleNumberByteArray): ResultTupleList {
            val list = ResultTupleList()
            val length = array.size
            list.setCapacity(length)
            var i = 0
            while (i < length) {
                val tupleNumber = array[i] ?: throw NakshaException(ILLEGAL_STATE, "Invalid tuple-number at index $i")
                val tupleCache = NakshaCache.tupleCache(storage.id)
                val tuple = tupleCache[tupleNumber]
                val resultTuple = ResultTuple(storage, tupleNumber, ExecutedOp.READ, tuple)
                list.add(resultTuple)
                i++
            }
            return list
        }
    }
}
