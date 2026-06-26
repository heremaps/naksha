package naksha.model.objects

import naksha.model.Tuple
import naksha.model.illegalArg
import naksha.model.illegalState
import naksha.model.objects.MemberType.MemberType_C.INT16
import kotlin.js.JsName

class Int16Member() : TypedMember<Int16Member>() {
    override fun verify(): Int16Member {
        if (dataType != INT16) {
            throw illegalState("The member was illegally cast, expected subtype: $INT16, found: $dataType")
        }
        return this
    }

    /** Creates a new int16 member with the given name and an optional custom JSON path. */
    @JsName("of")
    constructor(name: String, path: JsonPath? = null) : this() {
        this.name = name
        this.dataType = INT16
        this.path = path ?: JsonPath(listOf("properties", name))
        this.path.validate()
    }

    /** Creates an int16 member from an existing [Member], validating its type. */
    @JsName("from")
    constructor(member: Member, path: JsonPath? = null) : this() {
        if (member.dataType != INT16) throw illegalArg("The given member is not of int16 type")
        this.name = member.name
        this.dataType = INT16
        this.path = path?.validate() ?: member.path
    }

    /** Retrieves the int16 value of this member from the given feature. */
    fun get(feature: NakshaFeature): Short? = getInt64(feature)?.toShort()

    /**
     * Retrieves the int16 value of this member from the given tuple.
     * TODO: When no such member exists in membersBook, should search along [path] in [tuple.featureBytes], but currently cannot due to JbDecoder2 limits.
     */
    @JsName("getFromTuple")
    fun get(tuple: Tuple): Short? = getInt64(tuple)?.toShort()

    /** Sets the int16 value of this member on the given feature. */
    fun set(feature: NakshaFeature, value: Short): Any? = setPath(feature, path, value)
}
