@file:OptIn(ExperimentalJsExport::class)
@file:Suppress("OPT_IN_USAGE")

package naksha.model.request

import naksha.model.Tuple
import naksha.model.objects.NakshaCollection
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/**
 * Chains multiple [ITupleFilter]s into a single [ITupleFilter].
 *
 * Implements [ITupleFilter] so an instance can be passed wherever a single filter is expected.
 * All chained filters are applied in sequence; if any returns `false` the tuple is dropped.
 *
 * @since 3.0
 */
@JsExport
class TupleFilters(
    val collection: NakshaCollection,
    vararg filters: ITupleFilter
) : ITupleFilter {

    private val filters: Array<out ITupleFilter> = filters

    /**
     * Apply all chained filters in sequence.
     *
     * Implements [ITupleFilter.keepTuple], so this instance can be used as a single filter.
     * @param collection the collection from which the tuple comes.
     * @param tuple the tuple to filter.
     * @return `true` if all filters accept the tuple, `false` if any rejects it.
     * @since 3.0
     */
    override fun keepTuple(collection: NakshaCollection, tuple: Tuple): Boolean {
        for (f in filters) {
            if (!f.keepTuple(collection, tuple)) return false
        }
        return true
    }

    /**
     * Filter the given array of tuples by applying all chained filters.
     * @param tuples the tuples to filter.
     * @return a new array containing only the tuples that passed all filters.
     * @since 3.0
     */
    fun filterArray(tuples: Array<Tuple?>): Array<Tuple?> {
        if (filters.isEmpty()) return tuples
        val result = mutableListOf<Tuple?>()
        for (tuple in tuples) {
            if (tuple != null && keepTuple(collection, tuple)) result.add(tuple)
        }
        return result.toTypedArray()
    }
}
