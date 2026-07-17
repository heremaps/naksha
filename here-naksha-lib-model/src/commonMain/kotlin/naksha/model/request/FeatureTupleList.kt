@file:Suppress("OPT_IN_USAGE")

package naksha.model.request

import naksha.base.ListProxy
import naksha.model.*
import naksha.base.NakshaError.NakshaErrorCompanion.ILLEGAL_STATE
import naksha.base.NakshaException
import naksha.base.TupleNumber
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.js.JsStatic
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic
import kotlin.math.min

/**
 * A list of [result tuples][FeatureTuple].
 * @since 3.0.0
 */
@JsExport
open class FeatureTupleList : ListProxy<FeatureTuple>(FeatureTuple::class) {
    companion object FeatureTupleList_C {
        /**
         * Convert the given [tuple-number-binary-array][TupleNumberBinaryArray] into a [feature-tuple list][FeatureTupleList].
         *
         * This does not actually load the [Tuple] from storage or cache, should they be loaded from cache, it can simply be wrapped like:
         * ```kotlin
         * val rs = Naksha.cache.load( FeatureTupleList.fromByteArray(array) )
         * ```
         *
         * @param array the tuple-number-binary-array.
         * @param from the index of the first entry to convert.
         * @param to the index of the first entry **not** to convert.
         * @return the given binary converted into a list of feature-tuple.
         * @since 3.0.0
         */
        @JvmStatic
        @JsStatic
        @JvmOverloads
        fun fromByteArray(array: TupleNumberBinaryArray, from: Int = 0, to: Int = array.size): FeatureTupleList {
            val rs = FeatureTupleList()
            val length = to - from
            rs.setCapacity(length)
            var i = from
            while (i < to) {
                val tupleNumber = array[i] ?: throw NakshaException(ILLEGAL_STATE, "Invalid tuple-number at index $i")
                val featureTuple = FeatureTuple(tupleNumber)
                rs.add(featureTuple)
                i++
            }
            return rs
        }

        /**
         * Convert the given array of tuple-number into a list of feature-tuple to be loaded using [ISession.loadTuples].
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

    /**
     * Returns the [feature-tuple][FeatureTuple] with the given [tuple-number][TupleNumber].
     *
     * @param tupleNumber the [TupleNumber] to search for.
     * @return the [FeatureTuple] with the given [TupleNumber] or `null`, if no such feature is within the list.
     */
    operator fun get(tupleNumber: TupleNumber): FeatureTuple? {
        val i = indexOf(tupleNumber)
        return if (i >= 0) get(i) else null
    }

    /**
     * Searches for a [feature-tuple][FeatureTuple] with the given [tuple-number][TupleNumber].
     * @param tupleNumber the [TupleNumber] to search for.
     * @param from the index to start searching at; defaults to `0`.
     * @param to the index to end the search at (exclusive); defaults to [size].
     * @return the index of the next [FeatureTuple] with the given `id` or `null`, if no such feature is within the list.
     */
    @JsName("indexOfTupleNumber")
    @JvmOverloads
    fun indexOf(tupleNumber: TupleNumber, from: Int = 0, to: Int = size): Int {
        for (i in from until to) {
            val featureTuple = get(i) ?: continue
            if (featureTuple.tupleNumber == tupleNumber) return i
        }
        return -1
    }

    /**
     * Searches for a [feature-tuple][FeatureTuple] with the given [tuple-number][TupleNumber].
     * @param id the feature-id to search for.
     * @param from the index to start searching at; defaults to `0`.
     * @param to the index to end the search at (exclusive); defaults to [size].
     * @return the index of the next [FeatureTuple] with the given [TupleNumber] or `null`, if no such feature is within the list.
     */
    @JsName("indexOfId")
    @JvmOverloads
    fun indexOf(id: String, from: Int = 0, to: Int = size): Int {
        for (i in from until to) {
            val featureTuple = get(i) ?: continue
            if (featureTuple.id == id) return i
        }
        return -1
    }

    /**
     * Tries to load all tuples.
     * @param from the index of the first element to load; defaults to `0`.
     * @param to the index of the first element **not to load**; defaults to [size].
     * @param loadFromStorage if _true_, tries to load tuples not being in the cache from their storages; if being _false_, then only caches are asked, but the storage is not contacted; defaults to `true`.
     * @param acceptFeature if _true_, then the [Tuple] will only be loaded, when [FeatureTuple.feature] is as well _null_; defaults to `false`.
     * @return this.
     */
    fun loadAll(from: Int = 0, to: Int = size, loadFromStorage: Boolean = true, acceptFeature: Boolean = false): FeatureTupleList {
        // We only actually contact the cache, if there is at least one missing tuple!
        for (i in from until to) {
            val featureTuple = get(i) ?: continue
            if (featureTuple.tuple != null) continue
            if (acceptFeature && featureTuple.feature != null) continue
            // Found first feature that need to be loaded, load all and return.
            Naksha.cache.load(this, from, to, loadFromStorage = loadFromStorage, acceptFeature = acceptFeature)
            break
        }
        return this
    }

    /**
     * Convert this [feature-tuple list][FeatureTupleList] into a pure list of [Tuple], removing `null` values.
     * @param from the index of the first value to convert, defaults to `0`.
     * @param to the index of the fist value **not** to convert, defaults to [size].
     * @return the list of [Tuple].
     * @since 3.0
     */
    @JvmOverloads
    fun toTupleList(from:Int = 0, to:Int = size): List<Tuple> {
        val end = min(size, to)
        if (from < 0 || from >= end) return emptyList()
        val list = mutableListOf<Tuple>()
        for (i in from until to) {
            val ft = this[i]
            if (ft != null) {
                val tuple = ft.tuple
                if (tuple != null) list.add(tuple)
            }
        }
        return list
    }
}