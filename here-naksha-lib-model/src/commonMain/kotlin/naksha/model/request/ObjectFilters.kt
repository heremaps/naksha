@file:OptIn(ExperimentalJsExport::class)
@file:Suppress("OPT_IN_USAGE")

package naksha.model.request

import naksha.base.PAnyMap
import naksha.base.AnyObjectList
import naksha.base.TupleNumber
import naksha.model.ISession
import naksha.model.objects.NakshaCollection
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/**
 * Abstract base that chains multiple [IObjectFilter]s into a single [IObjectFilter].
 *
 * Implements [IObjectFilter] so an instance can be passed wherever a single filter is expected.
 * All chained filters are applied in sequence; if any returns `null` the object is dropped.
 *
 * Subclasses must implement [getTupleNumber] to extract the [TupleNumber] from an arbitrary object,
 * which is needed to resolve the collection for the filters.
 *
 * @since 3.0
 */
@JsExport
abstract class ObjectFilters(
    val session: ISession,
    val collection: NakshaCollection,
    vararg filters: IObjectFilter
) : IObjectFilter {

    private val filters: Array<out IObjectFilter> = filters

    /**
     * Extract the [TupleNumber] from the given object, to resolve its collection.
     * @param obj the object from which to extract the tuple-number.
     * @return the tuple-number, or `null` if not available.
     * @since 3.0
     */
    protected abstract fun getTupleNumber(obj: PAnyMap): TupleNumber?

    /**
     * Apply all chained filters in sequence.
     *
     * Implements [IObjectFilter.filter], so this instance can be used as a single filter.
     * @param collection the collection from which the object comes.
     * @param obj the object to filter.
     * @return the (potentially mutated) object if it passes all filters, or `null` to drop it.
     * @since 3.0
     */
    override fun filter(collection: NakshaCollection, obj: PAnyMap): PAnyMap? {
        var current: PAnyMap = obj
        for (f in filters) {
            current = f.filter(collection, current) ?: return null
        }
        return current
    }

    /**
     * Filter the given list of objects by applying all chained filters.
     * @param objects the objects to filter.
     * @return a new [AnyObjectList] containing only the objects that passed all filters.
     * @since 3.0
     */
    fun filterList(objects: AnyObjectList): AnyObjectList {
        if (filters.isEmpty()) return objects
        val result = AnyObjectList()
        result.setCapacity(objects.size)
        for (obj in objects) {
            val kept = filter(collection, obj ?: continue) ?: continue
            result.add(kept)
        }
        return result
    }
}
