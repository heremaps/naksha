package naksha.model.objects

import naksha.model.Tuple
import naksha.model.illegalArg
import naksha.model.illegalState
import naksha.model.objects.MemberType.MemberType_C.FLOAT64
import kotlin.js.JsName

class Float64Member() : TypedMember<Float64Member>() {
    override fun verify(): Float64Member {
        if (dataType != FLOAT64) {
            throw illegalState("The member was illegally cast, expected subtype: $FLOAT64, found: $dataType")
        }
        return this
    }

    /** Creates a new float64 member with the given name and an optional custom JSON path. */
    @JsName("of")
    constructor(name: String, path: JsonPath? = null) : this() {
        this.name = name
        this.dataType = FLOAT64
        this.path = path ?: JsonPath(listOf("properties", name))
        this.path.validate()
    }

    /** Creates a float64 member from an existing [Member], validating its type. */
    @JsName("from")
    constructor(member: Member, path: JsonPath? = null) : this() {
        if (member.dataType != FLOAT64) throw illegalArg("The given member is not of float64 type")
        this.name = member.name
        this.dataType = FLOAT64
        this.path = path?.validate() ?: member.path
    }

    /** Retrieves the float64 value of this member from the given feature. */
    fun get(feature: NakshaFeature): Double? = getDouble(feature)

    /**
     * Retrieves the float64 value of this member from the given tuple.
     * TODO: When no such member exists in membersBook, should search along [path] in [tuple.featureBytes], but currently cannot due to JbDecoder2 limits.
     */
    @JsName("getFromTuple")
    fun get(tuple: Tuple): Double? = getDouble(tuple)

    /** Sets the float64 value of this member on the given feature. */
    fun set(feature: NakshaFeature, value: Double): Any? = setPath(feature, path, value)
}
