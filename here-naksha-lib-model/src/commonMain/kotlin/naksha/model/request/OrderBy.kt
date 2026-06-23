@file:Suppress("OPT_IN_USAGE")

package naksha.model.request

import naksha.base.NotNullEnum
import naksha.base.NullableProperty
import naksha.base.AnyObject
import naksha.model.objects.Member
import naksha.model.objects.StandardMembers
import naksha.model.request.query.SortOrder
import naksha.model.request.query.SortOrder.SortOrderCompanion.ANY
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.js.JsStatic
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic

/**
 * Describes a sort order in a [result-set][naksha.model.request.IResultSet].
 *
 * @constructor Create an ordering.
 */
@JsExport
class OrderBy() : AnyObject() {

    /**
     * Create an order.
     *
     * If [member] is `null`, [next] must be `null` as well.
     * @param member the member by which to order by, if _null_, any member is accepted, only a deterministic order is requested.
     * @param order the sort order, if [ANY][SortOrder.ANY] is given, then the storage can pick whatever is faster.
     * @param next if a second-level order is requested; i.e. order by `id`, then by `version`.
     */
    @JsName("ofMember")
    @JvmOverloads
    constructor(member: Member?, order: SortOrder = ANY, next: OrderBy? = null) : this() {
        this.member = member?.name
        this.sortOrder = order
        this.next = next
    }

    /**
     * Create an order.
     *
     * If [member] is `null`, [next] must be `null` as well.
     * @param memberName the name fo the member by which to order by, if _null_, any member is accepted, only a deterministic order is requested.
     * @param order the sort order, if [ANY][SortOrder.ANY] is given, then the storage can pick whatever is faster.
     * @param next if a second-level order is requested; i.e. order by `id`, then by `version`.
     */
    @JsName("of")
    @JvmOverloads
    constructor(memberName: String?, order: SortOrder = ANY, next: OrderBy? = null) : this() {
        this.member = memberName
        this.sortOrder = order
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
         * Supported ordering by `version` _(aka transaction-number)_.
         */
        @JsStatic
        @JvmStatic
        fun version(): OrderBy = OrderBy(StandardMembers.Version)

        /**
         * Supported ordering by `tuple-number` _(so by storage, map, collection, feature, version, uid).
         */
        @JsStatic
        @JvmStatic
        fun tupleNumber(): OrderBy = OrderBy(StandardMembers.Tn)

        /**
         * Supported ordering by `id` and `version`.
         */
        @JsStatic
        @JvmStatic
        fun id(): OrderBy = OrderBy(StandardMembers.Id, next = version())

        private val STRING_OR_NULL = NullableProperty<OrderBy, String>(String::class)
        private val SORT_ORDER = NotNullEnum<OrderBy, SortOrder>(SortOrder::class) { _, _ -> ANY }
        private val NEXT_OR_NULL = NullableProperty<OrderBy, OrderBy>(OrderBy::class)
    }

    /**
     * The name of the [Member] by which to order, if `null`, then deterministic ordering is requested.
     * @since 3.0
     */
    var member: String? by STRING_OR_NULL

    /**
     * @see [member]
     */
    @JsName("withMember")
    fun withMember(member: Member?): OrderBy {
        this.member = member?.name
        return this
    }

    /**
     * @see [member]
     */
    @JsName("withMemberName")
    fun withMember(name: String?): OrderBy {
        this.member = name
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
     * Optionally next order, so after ordering by this [Member], order those that are equal by the given next one. If `null`, the order will switch to just be deterministic, when the [Member] values are equal so far _(internally storages are recommended to use the [TupleNumber][naksha.model.TupleNumber] to the final ordering)_.
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
    fun isDeterministic(): Boolean = member == null && sortOrder == ANY && next == null

    override fun equals(other: Any?): Boolean {
        if (other !is OrderBy) return false
        return member == other.member
            && sortOrder == other.sortOrder
            && next == other.next
    }

    override fun hashCode(): Int = super.hashCode()

    override fun toString(): String = "OrderBy(member=$member, sortOrder=$sortOrder, next=$next)"
}