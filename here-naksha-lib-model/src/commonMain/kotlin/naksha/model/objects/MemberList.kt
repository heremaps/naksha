@file:Suppress("OPT_IN_USAGE")

package naksha.model.objects

import naksha.base.ListProxy
import naksha.base.NakshaException
import naksha.base.Platform
import naksha.base.Platform.PlatformCompanion.FAL
import naksha.base.PlatformListApi.PlatformListApiCompanion.array_get
import naksha.base.PlatformListApi.PlatformListApiCompanion.array_push
import naksha.base.PlatformListApi.PlatformListApiCompanion.array_splice
import naksha.base.PlatformObject
import naksha.base.illegalArg
import naksha.base.illegalState
import naksha.base.internalError
import naksha.base.platformHash
import naksha.base.proxy
import naksha.model.objects.StandardMembers.StandardMembers_C.MANDATORY
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic

/**
 * An ordered list of [Member]s on a [NakshaCollection] that does not allow duplicates or `null` entries.
 *
 * The list can be [verified][verify], which will ensure that the [mandatory members][StandardMembers.MANDATORY] are leading the member list, and it will ensure that members have their [index][Member.index] property setup correctly.
 *
 * Note: Formally `null` is allowed, but practically the class does not allow `null` entries.
 * @since 3.0
 */
@JsExport
class MemberList() : ListProxy<Member>(Member::class) {

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
    constructor(members: List<Member?>) : this() {
        for (member in members) if (member != null) add(member)
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

    /** The internal, potentially outdated, members by name map */
    private val _memberIndexByName = mutableMapOf<String, Int>()

    /**
     * A map between the name of a member and its index in the list.
     *
     * If there are duplicates in the list, the last index is stored.
     * @since 3.0
     */
    private val memberIndexByName: MutableMap<String, Int>
        get() {
            val memberIndexByName = this._memberIndexByName
            if (this.size != memberIndexByName.size) {
                memberIndexByName.clear()
                val array = platformObject()
                for (i in indices) {
                    val member: Member = (array_get(array, i) as? PlatformObject).proxy(Member::class)
                        ?: throw illegalState("Member at index $i is null")
                    val existing = memberIndexByName[member.name]
                    if (existing != null) throw illegalState("Member at index $i ('${member.name}') already exists at $existing")
                    memberIndexByName[member.name] = i
                }
            }
            return memberIndexByName
        }

    override fun clear() {
        super.clear()
        _memberIndexByName.clear()
    }
    override fun removeAt(index: Int): Member? {
        if (index < 0 || index >= this.size) return null
        _memberIndexByName.clear()
        return super.removeAt(index)
    }
    override fun set(index: Int, element: Member?): Member? {
        if (element == null) throw illegalArg("Members cannot be null")
        val existing = memberIndexByName[element.name]
        if (existing == null) {
            memberIndexByName[element.name] = index
        } else if (index != existing) {
            throw illegalArg("Member '${element.name}' exists already at index $existing, failed to add a second time to index $index")
        } // else: nothing changed, same element at same index
        return super.set(index, element)
    }
    override fun retainAll(elements: Collection<Member?>): Boolean {
        for (element in elements) if (element == null) throw illegalArg("Members cannot be null")
        _memberIndexByName.clear()
        return super.retainAll(elements)
    }
    override fun removeAll(elements: Collection<Member?>): Boolean {
        _memberIndexByName.clear()
        return super.removeAll(elements)
    }
    override fun remove(element: Member?): Boolean {
        if (element == null) return false
        val removed = super.remove(element)
        if (removed) {
            // all indices may have been modified, even while we can optimize this, for now it's enough.
            _memberIndexByName.clear()
        }
        return removed
    }
    override fun lastIndexOf(element: Member?): Int {
        if (element == null) return -1
        return memberIndexByName[element.name] ?: -1
    }
    override fun indexOf(element: Member?): Int {
        if (element == null) return -1
        return memberIndexByName[element.name] ?: -1
    }
    override fun containsAll(elements: Collection<Member?>): Boolean {
        val memberIndexByName = this.memberIndexByName
        for (element in elements) {
            if (element == null) return false
            if (memberIndexByName[element.name] == null) return false
        }
        return true
    }
    override fun contains(element: Member?): Boolean = element != null && memberIndexByName[element.name] != null
    override fun addAll(elements: Collection<Member?>): Boolean {
        if (elements.isEmpty()) return false
        val memberIndexByName = this.memberIndexByName
        val allNullable = arrayOfNulls<Member>(elements.size)
        var i = 0
        for (element in elements) {
            if (element == null) throw illegalArg("Members cannot be null")
            val existing = memberIndexByName[element.name]
            if (existing != null) throw illegalArg("The member '${element.name}' already exists at $existing")
            allNullable[i++] = element
        }
        array_push(platformObject(), *Array(allNullable.size) { i ->
            val member = allNullable[i] as Member
            memberIndexByName[member.name] = i
            unbox(member)
        })
        return true
    }
    override fun addAll(index: Int, elements: Collection<Member?>): Boolean {
        if (elements.isEmpty()) return false
        val memberIndexByName = this.memberIndexByName
        val allNullable = arrayOfNulls<Member>(elements.size)
        var i = 0
        for (element in elements) {
            if (element == null) throw illegalArg("Members cannot be null")
            val existing = memberIndexByName[element.name]
            if (existing != null) throw illegalArg("The member '${element.name}' already exists at $existing")
            allNullable[i++] = element
        }
        array_splice(platformObject(), index,0, *Array(allNullable.size) { i ->
            val member = allNullable[i] as Member
            memberIndexByName[member.name] = i
            unbox(member)
        })
        return true
    }
    override fun add(index: Int, element: Member?) {
        if (element == null) throw illegalArg("Members cannot be null")
        val existing = memberIndexByName[element.name]
        if (existing != null) {
            throw illegalArg("Member '${element.name}' exists already at index $existing, failed to add a second time to index $index")
        }
        array_splice(platformObject(), index, 0, unbox(element))
        _memberIndexByName.clear() // All indices are now changed.
    }
    override fun add(element: Member?): Boolean {
        if (element == null) throw illegalArg("Members cannot be null")
        val existing = memberIndexByName[element.name]
        if (existing != null) {
            throw illegalArg("Member '${element.name}' exists already at index $existing")
        }
        memberIndexByName[element.name] = array_push(platformObject(), Platform.unbox(element)) - 1
        return true
    }

    /**
     * Sort this list by the [byte-alignment][MemberType.byteAlignment] of the [MemberType] and updates the [index][Member.index] of all members. This method will sort the [mandatory members][StandardMembers.MANDATORY] first, if those members are not yet in the member list, it will inject them.
     *
     * This method is normally called by storages before creating internal structures.
     * @return this.
     * @since 3.0
     * @throws NakshaException with error [ILLEGAL_STATE][naksha.base.NakshaError.ILLEGAL_STATE], if any member is `null`.
     */
    fun verify(): MemberList {
        val memberIndexByName = this._memberIndexByName
        // Clear index cache, then add negative indices for where mandatories should be.
        memberIndexByName.clear()
        for (i in 0 until MANDATORY.size) {
            val mandatory = MANDATORY[i]
            memberIndexByName[mandatory.name] = -i
        }
        // Keep track if there are any missing mandatories.
        var isOrdered = true
        var mandatoriesMissing = MANDATORY.size
        // Test if the members are already sorted, and all mandatory members are at place.
        for (i in 0 until size) {
            val member = this[i] ?: throw illegalState("Member at $i is null or of invalid type")
            val name = member.name
            val index = memberIndexByName[name]
            if (index != null) {
                if (index >= 0) throw illegalState("Member '$name' at $i is a duplicate of member at $index")
                else { // Mandatory found first time (stored index is negative).
                    if (-index == i) {
                        // This mandatory is at the correct expected index.
                        val mandatory = MANDATORY[i]
                        if (mandatory.isSameAs(member)) {
                            // The mandatory has the correct data-type.
                            mandatoriesMissing--
                            member["index"] = i
                        } else {
                            isOrdered = false
                        }
                    } else {
                        // Mandatory found at wrong index.
                        isOrdered = false
                    }
                    // Remember where we found this member for duplicate detection.
                    memberIndexByName[name] = i
                }
            } else {
                isOrdered = isOrdered && member.index == i
                memberIndexByName[name] = i
            }
        }
        if (isOrdered && mandatoriesMissing == 0) return this

        // Add missing mandatories, if any.
        if (mandatoriesMissing > 0) {
            for (i in 0 until MANDATORY.size) {
                val mandatory = MANDATORY[i]
                val pos = memberIndexByName[mandatory.name] ?: throw internalError("${FAL}Mandatory $i not found")
                if (pos < 0) this.add(mandatory.copy())
            }
        }

        // Sort.
        sortByDescending(this::sortByDescFunction)

        // Clear index cache (the sort invalidated it, by intention).
        memberIndexByName.clear()

        // Then mark all members with the index; use the same loop to update the index cache (side effect).
        for (i in 0 until size) {
            val member = this[i] ?: throw internalError("${FAL}Member at $i is invalid, this must not happen at this point")
            member["index"] = i // main reason for loop
            memberIndexByName[member.name] = i // nice side effect, cache updates.
        }
        return this
    }

    private fun sortByDescFunction(member: Member?): Int {
        if (member == null) return 1
        val mandatories = MANDATORY
        for (i in 0 until mandatories.size) {
            val mandatory = MANDATORY[i]
            if (mandatory.isSameAs(member)) {
                // We know that the biggest possible alignment is 32.
                // We want to sort the first mandatory (at i=0) first, so it must have the biggest value.
                return 32 + (mandatories.size - i)
            }
        }
        return member.dataType.byteAlignment
    }

    /**
     * Test whether this member list is valid, so does not have `null` entries and all members have unique valid names. Throws a [NakshaException], if any error is found.
     * @return this.
     */
    fun validateAndSort(): MemberList {
        val size = this.size
        for (i in 0 until size) {
            val member = this[i] ?: throw illegalState("Member at index $i is null")
            val memberName = member.name
            for (j in (i + 1) until this.size) {
                val later = this[j] ?: throw illegalState("Member at index $j is null")
                if (memberName == later.name) {
                    throw illegalState("Member at index $i has same name as member at $j: $memberName")
                }
            }
        }
        return this
    }

    /**
     * Sort this list by the index of the [MemberType].
     * @return this.
     * @since 3.0
     * @throws NakshaException with error [ILLEGAL_STATE][naksha.base.NakshaError.ILLEGAL_ARGUMENT], if any member is `null` or has no valid `index`.
     */
    fun sortByIndex(): MemberList {
        if (isSortedByIndex()) return this

        sortBy { member -> member?.index ?: throw illegalState("Member is null, of invalid type, or has no index") }
        _memberIndexByName.clear() // All indices may have changed.
        return this
    }

    /**
     * Tests if this list is sorted by [data-type][verify].
     * @return _true_ if the entries are sorted; _false_ otherwise.
     */
    fun isSortedByDataType(): Boolean {
        val END = this.size
        var last = Int.MAX_VALUE
        for (i in 0 until END) {
            val member = this[i] ?: return false
            // The elements towards the end of the list must have the smallest byte-alignment, we always sort descending.
            // This results in members in byte-alignment order, so INT8, FLOAT8, INT4, FLOAT4, ...
            // This prevents padding in the database.
            val byteAlignment = member.dataType.byteAlignment
            if (byteAlignment > last) return false
            last = byteAlignment
        }
        return true
    }

    /**
     * Tests if this list is sorted by [index][sortByIndex] with leading [mandatory members][StandardMembers.MANDATORY].
     * @return _true_ if the entries are sorted; _false_ otherwise.
     */
    fun isSortedByIndex(): Boolean {
        val mandatories = MANDATORY
        val END = this.size
        if (END < mandatories.size) return false
        for (i in 0 until END) {
            val member = this[i] ?: return false
            val index = member.index ?: return false
            if (index != i) return false

            if (i < mandatories.size) {
                val mandatory = mandatories[i]
                if (!mandatory.isSameAs(member)) return false
            }
        }
        return true
    }

    /**
     * Get the member with the given name from this list.
     * @param name The name of the member.
     * @return The first member with that name or `null`, if no such member was found.
     */
    operator fun get(name: String): Member? {
        val index = memberIndexByName[name] ?: return null
        return this[index]
    }

    /**
     * Get the member with the same name as the given one.
     * @param member the member from which to take the name and to return the found member.
     * @return the first member with that name or `null`, if no such member was found.
     */
    @JsName("getMember")
    operator fun get(member: Member): Member? {
        val index = memberIndexByName[member.name] ?: return null
        return this[index]
    }

    /**
     * Safely removes the given `member` from this list.
     * @param member the member to remove.
     * @return this.
     * @since 3.0
     */
    @JsName("removeMember")
    fun remove(member: Member): MemberList {
        val index = memberIndexByName[member.name] ?: return this
        this.removeAt(index)
        return this
    }

    /**
     * Safely removes the member with the given `name` from the list.
     * @param name the name of the [member][Member] to remove.
     * @return this.
     * @since 3.0
     */
    fun remove(name: String): MemberList {
        val index = memberIndexByName[name] ?: return this
        this.removeAt(index)
        return this
    }

    /**
     * Search for the given member in this collection and returns it, throws an exception when the member is not found.
     * @param member The member to search for, compares name and type.
     * @param comparePath If _true_, then the path is as well compared; otherwise any path is accepted.
     * @return the found member.
     * @throws NakshaException with error [ILLEGAL_STATE][naksha.base.NakshaError.ILLEGAL_STATE], if no such member was found.
     */
    @JvmOverloads
    fun useMember(member: Member, comparePath: Boolean = false): Member = member.asOther(this[member], comparePath)

    /**
     * Search for the given member in this collection and returns it, throws an exception when the member is not found.
     * @param name the name of the member to use.
     * @return the member.
     * @throws NakshaException with error [ILLEGAL_STATE][naksha.base.NakshaError.ILLEGAL_STATE], if no such member was found.
     */
    @JsName("useMemberByName")
    fun useMember(name: String): Member = this[name] ?: throw illegalState("Member $name does not exist")

    /**
     * Search for the given member in this collection.
     * @param member The member to search for, compares name and type.
     * @param comparePath If _true_, then the path is as well compared; otherwise any path is accepted.
     * @return the found member or `null`, if no such member is declared in this collection.
     */
    @JvmOverloads
    fun findMember(member: Member, comparePath: Boolean = false): Member? {
        val found = this[member.name] ?: return null
        return if (member.isSameAs(found, comparePath)) found else null
    }

    override fun hashCode(): Int = this.platformHash()
    override fun equals(other: Any?): Boolean = this === other || other is MemberList && this.platformHash() == other.platformHash()
}
