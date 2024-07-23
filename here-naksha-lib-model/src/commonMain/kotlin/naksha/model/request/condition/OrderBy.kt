@file:Suppress("OPT_IN_USAGE")

package naksha.model.request.condition

import kotlin.js.JsExport
import kotlin.jvm.JvmField

/**
 * Describes an ordering.
 * @property property the property by which to order.
 * @property order the sort order required, strongly recommended to stick with the default value [SortOrder.ANY].
 * @property next optionally next order, so after ordering by this property, order by the given one.
 */
@JsExport
class OrderBy(
    @JvmField var property: Property,
    @JvmField var order: SortOrder = SortOrder.ANY,
    @JvmField var next: OrderBy? = null)