package naksha.model.objects

import naksha.base.StringList
import naksha.base.illegalArg
import kotlin.js.JsExport
import kotlin.js.JsName

/**
 * A helper class that creates a list of verified members and indices. It validates against duplicates and ensures that the indices refer to the given members. It as well ensures that all mandatory members are contained in correct order.
 *
 * ### WARNING
 * The constructor will modify the given `memberList`, because it must be sorted, have correct indices, and all mandatory members!
 * @property members the list of members; will be modified!
 * @property indices the list of indices.
 * @since 3.0
 * @throws naksha.base.NakshaException with error [ILLEGAL_ARGUMENT][naksha.base.NakshaError.ILLEGAL_ARGUMENT] if the given members and/or indices are invalid.
 */
@JsExport
class MemberAndIndices(memberList: MemberList, indexList: IndexList) {
    /**
     * Creates a validated members and indices from the given collection, considering downward compatibility hacks.
     * @param collection the collection from which to extract the members and indices.
     * @since 3.0
     * @throws naksha.base.NakshaException with error [ILLEGAL_ARGUMENT][naksha.base.NakshaError.ILLEGAL_ARGUMENT] if the given members and/or indices are invalid.
     */
    @JsName("newMembersAndIndices")
    constructor(collection: NakshaCollection)
        : this( collection.members ?: MemberList(XyzMembers.ALL).verify(), collection.indices ?: IndexList(XyzIndices.ALL))

    /**
     * The verified members, **read-only**, ordered.
     * @since 3.0
     */
    val members: Array<Member>

    /**
     * The index of members by name, **read-only**.
     * @since 3.0
     */
    val memberPosByName: Map<String, Int>

    /**
     * The verified indices, **read-only**.
     * @since 3.0
     */
    val indices: Array<Index>

    /**
     * The index of an index by name, **read-only**.
     * @since 3.0
     */
    val indexPosByName: Map<String, Int>

    init {
        // Initialize and verify members.
        val mandatories = StandardMembers.MANDATORY
        val memberPosByName = mutableMapOf<String, Int>()

        // Sort the members specified by the client.
        if (!memberList.isSortedByIndex()) memberList.verify()

        this.members = Array(memberList.size) { i ->
            val member = memberList[i] ?: throw illegalArg("Member at $i is null")
            val existing = memberPosByName[member.name]
            if (existing != null) throw illegalArg("Member '${member.name}' already exists at index $existing")
            memberPosByName[member.name] = i
            member
        }
        this.memberPosByName = memberPosByName

        // Initialize and verify indices.
        val indexPosByName = mutableMapOf<String, Int>()
        this.indices = Array(indexList.size) { i ->
            val index = indexList[i] ?: throw illegalArg("Index at $i is null")
            val existing = indexPosByName[index.name]
            if (existing != null) throw illegalArg("Index '${index.name}' already exists at index $existing")

            // Verify, that the index only refers valid members.
            var j = -1
            while (++j < index.on.size) {
                val memberName = index.on[j] ?: throw illegalArg("Index '${index.name}' contains a reference to null in on[$j]")
                if (memberPosByName[memberName] == null) throw illegalArg("Index '${index.name}' reference to not existing member '$memberName' in on[$j]")
            }
            // Verify, that the index only include valid members.
            val include: StringList? = index.include
            if (include != null) {
                j = -1
                while (++j < include.size) {
                    val memberName = include[j] ?: throw illegalArg("Index '${index.name}' contains a reference to null in include[$j]")
                    if (memberPosByName[memberName] == null) throw illegalArg("Index '${index.name}' reference to not existing member '$memberName' in include[$j]")
                }
            }
            indexPosByName[index.name] = i

            index
        }
        this.indexPosByName = indexPosByName
    }
}