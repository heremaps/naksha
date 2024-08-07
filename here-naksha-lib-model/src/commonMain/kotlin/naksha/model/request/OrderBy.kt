@file:Suppress("OPT_IN_USAGE")

package naksha.model.request

import naksha.base.NotNullEnum
import naksha.base.NullableProperty
import naksha.base.AnyObject
import naksha.model.request.query.TupleColumn
import naksha.model.request.query.SortOrder
import naksha.model.request.query.SortOrder.SortOrderCompanion.DESCENDING
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.js.JsStatic
import kotlin.jvm.JvmStatic

/**
 * Describes a sort order in a [result-set][naksha.model.request.IResultSet].
 *
 * **Warning**: Using custom ordering may not be supported by the storage. The best is to only use the pre-defined sort orders:
 * - [deterministic]
 * - [version]
 * - [id]
 * - [author]
 *
 * @constructor Creating an ordering, where the details
 */
@JsExport
open class OrderBy() : AnyObject() {

    /**
     * Create a new order.
     * @param column the column by which to order by, if _null_, any column is okay, just a deterministic order is requested.
     * @param order the sort order, if [ANY][SortOrder.ANY] is given, then the storage can pick whatever is faster.
     * @param next if a second-level order is requested, for example order by `id` and then by `txn`, and finally by `uid`.
     */
    @JsName("of")
    constructor(column: TupleColumn? = null, order: SortOrder = SortOrder.ANY, next: OrderBy? = null) : this() {
        this.column = column
        this.order = order
        this.next = next
    }

    companion object OrderByCompanion {
        /**
         * Create a deterministic order of a result-set, but without specifying by which column to order, nor how to [sort][SortOrder.ANY]. Therefore, the ordering can be done very efficiently by the storage (it can for example read in index order).
         */
        @JsStatic
        @JvmStatic
        fun deterministic(): OrderBy = OrderBy()

        /**
         * Supported ordering by `version`.
         */
        @JsStatic
        @JvmStatic
        fun version(): OrderBy = OrderBy(TupleColumn.version(), DESCENDING, OrderBy(TupleColumn.uid(), DESCENDING))

        /**
         * Supported ordering by `id` and `version`.
         */
        @JsStatic
        @JvmStatic
        fun id(): OrderBy = OrderBy(TupleColumn.id(), next = version())

        /**
         * Supported ordering by `author`, `updatedAt`, `id`, and `version`.
         */
        @JsStatic
        @JvmStatic
        fun author(): OrderBy = OrderBy(TupleColumn.author(), next = OrderBy(TupleColumn.updatedAt(), DESCENDING, id()))

        private val COLUMN_NULL = NullableProperty<OrderBy, TupleColumn>(TupleColumn::class)
        private val ORDER_ENUM = NotNullEnum<OrderBy, SortOrder>(SortOrder::class) { _, _ -> SortOrder.ANY }
        private val ORDER_BY_NULL = NullableProperty<OrderBy, OrderBy>(OrderBy::class)
    }

    /**
     * The [row column][TupleColumn] by which to order, if _null_, then ordering is requested, but no specific order is needed.
     */
    var column by COLUMN_NULL

    /**
     * The sort order, it is strongly recommended to stick with the default value [SortOrder.ANY].
     */
    var order by ORDER_ENUM

    /**
     * Optionally next order, so after ordering by this property, order those that are equal by the given one. If _null_, the order will be random when the properties are equal.
     */
    var next by ORDER_BY_NULL

    override fun equals(other: Any?): Boolean {
        if (other !is OrderBy) return false
        return column == other.column
                && order == other.order
                && next == other.next
    }

    override fun hashCode(): Int = super.hashCode()

    override fun toString(): String {
        val col = column ?: return ""
        val next = this.next
        return "${col.name} $order${if (next != null) ", $next" else ""}"
    }
}