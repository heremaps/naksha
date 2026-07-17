package naksha.model.objects

import naksha.model.Tuple
import naksha.base.illegalArg
import naksha.base.illegalState
import naksha.model.objects.MemberType.MemberType_C.FLOAT32
import kotlin.js.JsName

class Float32Member() : TypedMember<Float32Member>() {
    override fun verify(): Float32Member {
        if (dataType != FLOAT32) {
            throw illegalState("The member was illegally cast, expected subtype: $FLOAT32, found: $dataType")
        }
        return this
    }

    /** Creates a new float32 member with the given name and an optional custom JSON path. */
    @JsName("of")
    constructor(name: String, path: JsonPath? = null) : this() {
        this.name = name
        this.dataType = FLOAT32
        this.path = path ?: JsonPath("properties", name)
        this.path.validate()
    }

    /** Creates a float32 member from an existing [Member], validating its type. */
    @JsName("from")
    constructor(member: Member, path: JsonPath? = null) : this() {
        if (member.dataType != FLOAT32) throw illegalArg("The given member is not of float32 type")
        this.name = member.name
        this.dataType = FLOAT32
        this.path = path?.validate() ?: member.path
    }

    /** Retrieves the float32 value of this member from the given feature. */
    fun get(feature: NakshaFeature): Float? = getDouble(feature)?.toFloat()

    /**
     * Retrieves the float32 value of this member from the given tuple.
     * TODO: When no such member exists in membersBook, should search along [path] in [tuple.featureBytes], but currently cannot due to JbDecoder2 limits.
     */
    @JsName("getFromTuple")
    fun get(tuple: Tuple): Float? = getDouble(tuple)?.toFloat()

    /** Sets the float32 value of this member on the given feature. */
    fun set(feature: NakshaFeature, value: Float): Any? = setPath(feature, path, value)
}
