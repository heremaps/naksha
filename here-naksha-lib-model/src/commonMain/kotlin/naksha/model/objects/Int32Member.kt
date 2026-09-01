package naksha.model.objects

import naksha.model.Tuple
import naksha.base.illegalArg
import naksha.base.illegalState
import naksha.model.objects.MemberType.MemberType_C.INT32
import kotlin.js.JsName

class Int32Member() : TypedMember<Int32Member>() {
    override fun verify(): Int32Member {
        if (dataType != INT32) {
            throw illegalState("The member was illegally cast, expected subtype: $INT32, found: $dataType")
        }
        return this
    }

    /** Creates a new int32 member with the given name and an optional custom JSON path. */
    @JsName("of")
    constructor(name: String, path: JsonPath? = null) : this() {
        this.name = name
        this.dataType = INT32
        this.path = path ?: JsonPath("properties", name)
        this.path.validate()
    }

    /** Creates an int32 member from an existing [Member], validating its type. */
    @JsName("from")
    constructor(member: Member, path: JsonPath? = null) : this() {
        if (member.dataType != INT32) throw illegalArg("The given member is not of int32 type")
        this.name = member.name
        this.dataType = INT32
        this.path = path?.validate() ?: member.path
    }

    /** Retrieves the int32 value of this member from the given feature. */
    fun get(feature: NakshaFeature): Int? = readInt64(feature)?.toInt()

    /**
     * Retrieves the int32 value of this member from the given tuple.
     * TODO: When no such member exists in membersBook, should search along [path] in [tuple.featureBytes], but currently cannot due to JbDecoder2 limits.
     */
    @JsName("getFromTuple")
    fun get(tuple: Tuple): Int? = readInt64(tuple)?.toInt()

    /** Sets the int32 value of this member on the given feature. */
    fun set(feature: NakshaFeature, value: Int): Any? = setPath(feature, path, value)
}
