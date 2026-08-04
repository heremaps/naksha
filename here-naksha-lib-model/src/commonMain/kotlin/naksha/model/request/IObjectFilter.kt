@file:OptIn(ExperimentalJsExport::class)

package naksha.model.request

import naksha.base.PAnyMap
import naksha.model.objects.NakshaCollection
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/**
 * A filter that can be used with a [result-set][ResultSet].
 *
 * The [tuple-filter][ITupleFilter] is less flexible, but potentially much faster than the [object-filter][IObjectFilter], when the storage supports tuple.
 * @since 3.0
 * @see ITupleFilter
 */
@JsExport
interface IObjectFilter {
    /**
     * Filter the given object.
     *
     * This filter allows to mutate the given object.
     * @param collection the collection from which the object comes.
     * @param obj the object to filter.
     * @return the filtered object, `null` if the object should be removed form the result-set.
     * @since 3.0
     * @see ITupleFilter
     */
    fun filter(collection: NakshaCollection, obj: PAnyMap): PAnyMap?
}