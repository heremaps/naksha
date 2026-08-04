@file:Suppress("OPT_IN_USAGE")

package naksha.model.request

import naksha.base.PAnyMap
import naksha.base.TupleNumber
import naksha.model.ISession
import naksha.model.objects.NakshaCollection
import naksha.model.objects.NakshaFeature
import kotlin.js.JsExport

/**
 * An [ObjectFilters] implementation that knows how to extract the [TupleNumber] from a [NakshaFeature]
 * (via `properties.xyz.guid.tupleNumber`).
 *
 * @since 3.0
 */
@JsExport
class NakshaFeatureFilters(
    session: ISession,
    collection: NakshaCollection,
    vararg filters: IObjectFilter
) : ObjectFilters(session, collection, *filters) {

    override fun getTupleNumber(obj: PAnyMap): TupleNumber? {
        return obj.proxy(NakshaFeature::class).properties.xyz.guid?.tupleNumber
    }
}
