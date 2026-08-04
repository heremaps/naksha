@file:OptIn(ExperimentalJsExport::class)

package naksha.model.request

import naksha.model.Tuple
import naksha.model.objects.NakshaCollection
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/**
 * A filter that can be used with a [result-set][ResultSet].
 *
 * The [object-filter][IObjectFilter] is more flexible.
 * @since 3.0
 * @see IObjectFilter
 */
@JsExport
interface ITupleFilter {
    /**
     * Filter the given tuple.
     *
     * @param collection the collection from which the object comes.
     * @param tuple the object to filter.
     * @return `true` if the tuple should stay in the result-set, potentially given to the [object-filter][IObjectFilter]; `false` if the tuple should be removed from the result-set _(will not even reach the [object-filter][IObjectFilter]).
     * @since 3.0
     * @see IObjectFilter
     */
    fun keepTuple(collection: NakshaCollection, tuple: Tuple): Boolean
}