@file:Suppress("OPT_IN_USAGE")

package naksha.model.request

import naksha.base.ListProxy
import naksha.model.*
import naksha.model.NakshaError.NakshaErrorCompanion.ILLEGAL_STATE
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmStatic

/**
 * A list of [result tuples][FeatureTuple].
 * @since 3.0.0
 */
@JsExport
open class FeatureTupleList : ListProxy<FeatureTuple>(FeatureTuple::class) {
    companion object FeatureTupleList_C {
        /**
         * Convert the given tuple-number-binary-array into a result-tuple list.
         *
         * This does not actually load the [Tuple] from storage or cache, should they be loaded from cache, it can simply be wrapped like:
         * ```kotlin
         * val rs = Naksha.cache.load( ResultTupleList.fromByteArray(array) )
         * ```
         *
         * @param array the tuple-number binary.
         * @return the given binary converted into a list of result-tuples.
         * @since 3.0.0
         */
        @JvmStatic
        @JsStatic
        fun fromByteArray(array: TupleNumberBinaryArray): FeatureTupleList {
            val rs = FeatureTupleList()
            val length = array.size
            rs.setCapacity(length)
            var i = 0
            while (i < length) {
                val tupleNumber = array[i] ?: throw NakshaException(ILLEGAL_STATE, "Invalid tuple-number at index $i")
                val featureTuple = FeatureTuple(tupleNumber)
                rs.add(featureTuple)
                i++
            }
            return rs
        }

        /**
         * Convert the given array of tuple-number into a list of feature-tuple to be loaded using [ISession.fetchTuples].
         * @param array the array of tuple-number.
         * @return a list of [FeatureTuple] with the logically same content as the given array.
         * @since 3.0.0
         */
        @JvmStatic
        @JsStatic
        fun fromTupleNumberArray(vararg array: TupleNumber): FeatureTupleList {
            val rs = FeatureTupleList()
            val length = array.size
            rs.setCapacity(length)
            var i = 0
            while (i < length) {
                val tupleNumber = array[i]
                val featureTuple = FeatureTuple(tupleNumber)
                rs.add(featureTuple)
                i++
            }
            return rs
        }
    }
}