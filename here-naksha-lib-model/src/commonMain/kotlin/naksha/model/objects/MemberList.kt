@file:Suppress("OPT_IN_USAGE")

package naksha.model.objects

import naksha.base.ListProxy
import naksha.model.NakshaError
import naksha.model.NakshaException
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
     * Test whether this member list is valid, so does not have `null` entries and all members have unique names. Throws a [NakshaException], if any error is found.
     */
    fun validate() {
        for (i in 0 until this.size) {
            val member = this[i] ?: throw NakshaException(NakshaError.ILLEGAL_STATE, "Member at index $i is null")
            val memberName = member.name
            for (j in (i + 1) until this.size) {
                val later = this[j] ?: throw NakshaException(NakshaError.ILLEGAL_STATE, "Member at index $j is null")
                if (memberName == later.name) {
                    throw NakshaException(NakshaError.ILLEGAL_STATE, "Member at index $i has same name as member at $j: $memberName")
                }
            }
        }
    }
}
