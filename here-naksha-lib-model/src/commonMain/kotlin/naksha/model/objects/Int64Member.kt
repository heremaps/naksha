package naksha.model.objects

import naksha.model.Tuple
import naksha.base.illegalArg
import naksha.base.illegalState
import naksha.model.objects.MemberType.MemberType_C.INT64
import kotlin.js.JsName

class Int64Member() : TypedMember<Int64Member>() {
    override fun verify(): Int64Member {
        if (dataType != INT64) {
            throw illegalState("The member was illegally cast, expected subtype: $INT64, found: $dataType")
        }
        return this
    }

    /** Creates a new int64 member with the given name and an optional custom JSON path. */
    @JsName("of")
    constructor(name: String, path: JsonPath? = null) : this() {
        this.name = name
        this.dataType = INT64
        this.path = path ?: JsonPath("properties", name)
        this.path.validate()
    }

    /** Creates an int64 member from an existing [Member], validating its type. */
    @JsName("from")
    constructor(member: Member, path: JsonPath? = null) : this() {
        if (member.dataType != INT64) throw illegalArg("The given member is not of int64 type")
        this.name = member.name
        this.dataType = INT64
        this.path = path?.validate() ?: member.path
    }

    /** Retrieves the int64 value of this member from the given feature. */
    fun get(feature: NakshaFeature): Long? = readLong(feature)

    /**
     * Retrieves the int64 value of this member from the given tuple.
     * TODO: When no such member exists in membersBook, should search along [path] in [tuple.featureBytes], but currently cannot due to JbDecoder2 limits.
     */
    @JsName("getFromTuple")
    fun get(tuple: Tuple): Long? = readLong(tuple)

    /** Sets the int64 value of this member on the given feature. */
    fun set(feature: NakshaFeature, value: Long): Any? = setPath(feature, path, value)
}
