@file:Suppress("OPT_IN_USAGE")

package naksha.model.objects

import naksha.base.ListProxy
import naksha.base.NakshaError.NakshaErrorCompanion.ILLEGAL_STATE
import naksha.base.NakshaError.NakshaErrorCompanion.INTERNAL_ERROR
import naksha.base.NakshaException
import naksha.model.NakshaIdType.INTERNAL_MEMBER
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.jvm.JvmStatic

/**
 * An ordered list of [Member]s on a [NakshaCollection].
 * @since 3.0
 */
@JsExport
open class MemberList() : ListProxy<Member>(Member::class) {

    /**
     * Construct a list from a vararg of members.
     * @since 3.0
     */
    @JsName("fromMembers")
    constructor(vararg members: Member) : this() {
        for (member in members) add(member)
    }

    /**
     * Construct a list from a list of members.
     * @since 3.0
     */
    @JsName("fromList")
    constructor(members: List<Member>) : this() {
        addAll(members)
    }

    companion object MemberList_C {
        /**
         * The maximum number of members allowed in a single collection.
         * @since 3.0
         */
        const val MAX_MEMBERS = 64

        @JvmStatic
        fun of(vararg members: Member): MemberList =
            MemberList().apply { addAll(members.toList()) }
    }

    /**
     * Sort this list by the sort-order of the [MemberType] and updates the `index` of all members.
     * @return this.
     * @since 3.0
     * @throws NakshaException with error [ILLEGAL_STATE], if any member is `null` or has no `dataType`.
     */
    fun sortByDataTypeAndAssignIndex(): MemberList {
        sortBy { member -> member?.dataType?.sortOrder ?: throw NakshaException(ILLEGAL_STATE, "Member is null or has no dataType") }
        // Save the order.
        for (i in 0 until size) {
            val member = this[i] ?: throw NakshaException(INTERNAL_ERROR, "Member is null, that must not happen, should have caught before")
            member["index"] = i
        }
        return this
    }

    /**
     * Sort this list by the index of the [MemberType].
     * @return this.
     * @since 3.0
     * @throws NakshaException with error [ILLEGAL_STATE], if any member is `null` or has no valid `index`.
     */
    fun sortByIndex(): MemberList {
        sortBy { member -> member?.index ?: throw NakshaException(ILLEGAL_STATE, "Member is null or has no index") }
        return this
    }

    /**
     * Tests if this list is sorted by [data-type][sortByDataTypeAndAssignIndex].
     * @return _true_ if the entries are sorted; _false_ otherwise.
     */
    fun isSortedByDataType(): Boolean {
        val END = this.size
        var max = 0
        for (i in 0 until END) {
            val member = this[i] ?: return false
            val sortOrder = member.dataType.sortOrder
            // The elements towards the end of the list must have the biggest sort-order value, we always sort ascending.
            // This results in members in byte-alignment order, so INT8, FLOAT8, INT4, FLOAT4, ...
            // This prevents padding in the database.
            if (max > sortOrder) return false
            max = sortOrder
        }
        return true
    }

    /**
     * Tests if this list is sorted by [index][sortByIndex].
     * @return _true_ if the entries are sorted; _false_ otherwise.
     */
    fun isSortedByIndex(): Boolean {
        val END = this.size
        for (i in 0 until END) {
            val member = this[i] ?: return false
            val index = member.index ?: return false
            if (index != i) return false
        }
        return true
    }

    /**
     * Get the member with the given name from this list.
     * @param name The name of the member.
     * @return The first member with that name or `null`, if no such member was found.
     */
    fun get(name: String): Member? {
        for (member in this) {
            if (member != null && member.name == name) return member
        }
        return null
    }

    /**
     * Test whether this member list is valid, so does not have `null` entries and all members have unique valid names. Throws a [NakshaException], if any error is found.
     */
    fun validate() {
        val size = this.size
        val names : HashSet<String> = HashSet(size)
        for (i in 0 until size) {
            val member = this[i] ?: throw NakshaException(ILLEGAL_STATE, "Member at index $i is null")
            val memberName = member.name
            if (!INTERNAL_MEMBER.isValidId(memberName)) {
                throw NakshaException(ILLEGAL_STATE, "Member at index $i has invalid name: $memberName")
            }
            if (!names.add(memberName)) {
                throw NakshaException(ILLEGAL_STATE, "Member at index $i has duplicate name with another member: $memberName")
            }
        }
    }
}
