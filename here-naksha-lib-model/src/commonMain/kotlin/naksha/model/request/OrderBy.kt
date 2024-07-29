@file:Suppress("OPT_IN_USAGE")

package naksha.model.request

import naksha.base.NotNullEnum
import naksha.base.NullableProperty
import naksha.base.AnyObject
import naksha.model.request.query.Property
import naksha.model.request.query.SortOrder
import kotlin.js.JsExport
import kotlin.js.JsName

/**
 * Describes a sort order in a [result-set][naksha.model.request.ResultSet].
 *
 * @constructor Creating an ordering, where the details
 */
@JsExport
open class OrderBy() : AnyObject() {

    /**
     * Create a new order.
     * @param property the property to order by, if _null_, any property is okay.
     */
    @JsName("of")
    constructor(property: Property? = null, order: SortOrder = SortOrder.ANY, next: OrderBy? =null) : this() {
        this.property = property
        this.order = order
        this.next = next
    }

    companion object OrderByCompanion {
        private val PROPERTY_NULL = NullableProperty<OrderBy, Property>(Property::class)
        private val ORDER_ENUM = NotNullEnum<OrderBy, SortOrder>(SortOrder::class) { _, _ -> SortOrder.ANY }
        private val ORDER_BY_NULL = NullableProperty<OrderBy, OrderBy>(OrderBy::class)
    }

    /**
     * The property by which to order, if _null_, then ordering is requested, but no specific order is needed.
     */
    var property by PROPERTY_NULL

    /**
     * The sort order, it is strongly recommended to stick with the default value [SortOrder.ANY].
     */
    var order by ORDER_ENUM

    /**
     * Optionally next order, so after ordering by this property, order those that are equal by the given one. If _null_, the order will be random when the properties are equal.
     */
    var next by ORDER_BY_NULL
}