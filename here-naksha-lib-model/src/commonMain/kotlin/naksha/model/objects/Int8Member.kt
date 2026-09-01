package naksha.model.objects

import naksha.model.Tuple
import naksha.base.illegalArg
import naksha.base.illegalState
import naksha.model.objects.MemberType.MemberType_C.INT8
import kotlin.js.JsName

class Int8Member() : TypedMember<Int8Member>() {
    override fun verify(): Int8Member {
        if (dataType != INT8) {
            throw illegalState("The member was illegally cast, expected subtype: $INT8, found: $dataType")
        }
        return this
    }

    /** Creates a new int8 member with the given name and an optional custom JSON path. */
    @JsName("of")
    constructor(name: String, path: JsonPath? = null) : this() {
        this.name = name
        this.dataType = INT8
        this.path = path ?: JsonPath("properties", name)
        this.path.validate()
    }

    /** Creates an int8 member from an existing [Member], validating its type. */
    @JsName("from")
    constructor(member: Member, path: JsonPath? = null) : this() {
        if (member.dataType != INT8) throw illegalArg("The given member is not of int8 type")
        this.name = member.name
        this.dataType = INT8
        this.path = path?.validate() ?: member.path
    }

    /** Retrieves the int8 value of this member from the given feature. */
    fun get(feature: NakshaFeature): Byte? = readInt64(feature)?.toByte()

    /**
     * Retrieves the int8 value of this member from the given tuple.
     * TODO: When no such member exists in membersBook, should search along [path] in [tuple.featureBytes], but currently cannot due to JbDecoder2 limits.
     */
    @JsName("getFromTuple")
    fun get(tuple: Tuple): Byte? = readInt64(tuple)?.toByte()

    /** Sets the int8 value of this member on the given feature. */
    fun set(feature: NakshaFeature, value: Byte): Any? = setPath(feature, path, value)
}
