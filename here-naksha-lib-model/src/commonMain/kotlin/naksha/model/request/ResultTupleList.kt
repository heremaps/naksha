@file:Suppress("OPT_IN_USAGE")

package naksha.model.request

import naksha.base.ListProxy
import naksha.model.*
import naksha.model.NakshaCache.PgCache_C.getTupleCache
import naksha.model.NakshaCache.PgCache_C.useTupleCache
import naksha.model.NakshaCache.PgCache_C.getStorage
import naksha.model.NakshaError.NakshaErrorCompanion.ILLEGAL_ARGUMENT
import naksha.model.NakshaError.NakshaErrorCompanion.ILLEGAL_STATE
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmStatic

/**
 * A list of [result tuples][ResultTuple].
 * @since 3.0.0
 */
@JsExport
class ResultTupleList : ListProxy<ResultTuple>(ResultTuple::class) {
    companion object ResultTupleList_C {
        /**
         * Read the given binary tuple-number byte-array, and convert it into a result-tuple list.
         *
         * @param array the tuple-number binary.
         * @param executedOp the [ExecutedOp] to set in all tuples; when _null_, it will be set based upon the [Action].
         * @return the given binary converted into a list of result-tuples.
         * @since 3.0.0
         */
        @JvmStatic
        @JsStatic
        fun fromTupleNumberArray(array: TupleNumberByteArray, executedOp: ExecutedOp? = null): ResultTupleList {
            val list = ResultTupleList()
            val length = array.size
            list.setCapacity(length)
            var cache: TupleCache? = null
            var i = 0
            while (i < length) {
                val tupleNumber = array[i] ?: throw NakshaException(ILLEGAL_STATE, "Invalid tuple-number at index $i")
                if (cache == null || cache.storageNumber != tupleNumber.storageNumber) {
                    cache = useTupleCache(tupleNumber.storageNumber)
                }
                val tuple = cache[tupleNumber]
                val op = executedOp ?: ExecutedOp.fromAction(tupleNumber.flags.action())
                val resultTuple = ResultTuple(tupleNumber, tuple, op)
                list.add(resultTuple)
                i++
            }
            return list
        }
    }
}