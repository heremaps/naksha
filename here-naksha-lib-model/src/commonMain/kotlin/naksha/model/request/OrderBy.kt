@file:Suppress("OPT_IN_USAGE")

package naksha.model.request

import naksha.base.NotNullEnum
import naksha.base.NullableProperty
import naksha.base.AnyObject
import naksha.base.Platform.Platform_C.forKClass
import naksha.base.PlatformType
import naksha.model.request.query.MetaColumn
import naksha.model.request.query.SortOrder
import naksha.model.request.query.SortOrder.SortOrder_C.ANY
import naksha.model.request.query.SortOrder.SortOrder_C.DESCENDING
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.js.JsStatic
import kotlin.jvm.JvmField
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic

/**
 * Describes a sort order in a result-set.
 *
 * **Warning**: Using custom ordering may not be supported by the storage. The best is to only use the pre-defined sort orders:
 * - [deterministic]
 * - [version]
 * - [id]
 * - [author]
 */
@JsExport
class OrderBy() : AnyObject() {

    /**
     * Create a new order.
     * @param column the column by which to order by, if _null_, any column is okay, just a deterministic order is requested.
     * @param order the sort order, if [ANY][SortOrder.ANY] is given, then the storage can pick whatever is faster.
     * @param next if a second-level order is requested, for example order by `id` and then by `txn`, and finally by `uid`.
     */
    @JsName("of")
    @JvmOverloads
    constructor(column: MetaColumn?, order: SortOrder = ANY, next: OrderBy? = null) : this() {
        this.column = column
        this.sortOrder = order
        this.next = next
    }

    companion object OrderBy_C {
        /**
         * The [PlatformType] of [OrderBy].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE = forKClass(OrderBy::class).withPackageName(PACKAGE_NAME)

        /**
         * Create a deterministic order of a result-set, but without specifying by which column to order, nor how to [sort][SortOrder.ANY]. Therefore, the ordering can be done very efficiently by the storage (it can for example read in index order).
         */
        @JsStatic
        @JvmStatic
        fun deterministic(): OrderBy = OrderBy()

        /**
         * Supported ordering by `version` _(aka transaction-number)_.
         */
        @JsStatic
        @JvmStatic
        fun version(): OrderBy = OrderBy(MetaColumn.version())

        /**
         * Supported ordering by `tuple-number` _(so by storage, map, collection, feature, version, uid).
         */
        @JsStatic
        @JvmStatic
        fun tupleNumber(): OrderBy = OrderBy(column=MetaColumn.tupleNumber())

        /**
         * Supported ordering by `id` and `version`.
         */
        @JsStatic
        @JvmStatic
        fun id(): OrderBy = OrderBy(MetaColumn.id(), next = version())

        /**
         * Supported ordering by `author`, `updatedAt`, `id`, and `version`.
         */
        @JsStatic
        @JvmStatic
        fun author(): OrderBy = OrderBy(MetaColumn.author(), next = OrderBy(MetaColumn.updatedAt(), DESCENDING, id()))

        private val COLUMN_OR_NULL = NullableProperty<OrderBy, MetaColumn>(MetaColumn.TYPE)
        private val SORT_ORDER = NotNullEnum<OrderBy, SortOrder>(SortOrder.TYPE) { _, _ -> ANY }
        private val NEXT_OR_NULL = NullableProperty<OrderBy, OrderBy>(TYPE)
    }

    /**
     * The [MetaColumn] by which to order, if `null`, then deterministic ordering is requested.
     * @since 3.0
     */
    var column by COLUMN_OR_NULL

    /**
     * @see [column]
     */
    fun withColumn(value: MetaColumn?): OrderBy {
        column = value
        return this
    }

    /**
     * The sort-order, it is recommended to stick with the default value [ANY][SortOrder.ANY].
     * @since 3.0
     */
    var sortOrder by SORT_ORDER

    /**
     * @see [sortOrder]
     */
    fun withSortOrder(value: SortOrder): OrderBy {
        sortOrder = value
        return this
    }

    /**
     * Optionally next order, so after ordering by this [MetaColumn], order those that are equal by the given next one. If `null`, the order will switch to just be deterministic, when the [MetaColumn] values are equal so far _(internally storages are recommended to use the [TupleNumber][naksha.model.TupleNumber] to the final ordering)_.
     * @since 3.0
     */
    var next by NEXT_OR_NULL

    /**
     * @see [next]
     */
    fun withNext(value: OrderBy?): OrderBy {
        next = value
        return this
    }

    /**
     * Tests if this represents deterministic ordering, which means that no specific column is selected (`null`), the order is [Any], and no other conditions are given ([next] = `null`).
     * @return `true` if this represents the deterministic order; `false` otherwise.
     */
    fun isDeterministic(): Boolean = column == null && sortOrder == ANY && next == null

    override fun equals(other: Any?): Boolean {
        if (other !is OrderBy) return false
        return column == other.column
            && sortOrder == other.sortOrder
            && next == other.next
    }

    @Suppress("RedundantOverride")
    override fun hashCode(): Int = super.hashCode()

    override fun toString(): String {
        val col = column ?: return ""
        val next = this.next
        return "${col.columnName} $sortOrder${if (next != null) ", $next" else ""}"
    }
}