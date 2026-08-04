package naksha.model.objects

import naksha.model.Tuple
import naksha.base.PTypedMap
import naksha.base.Base.BaseCompanion.UNDEFINED
import naksha.base.illegalArg
import naksha.base.illegalState
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
        this.id = name
        this.dataType = FLOAT64
        this.path = path ?: JsonPath("properties", name)
        this.path.validate()
    }

    /** Creates a float64 member from an existing [Member], validating its type. */
    @JsName("from")
    constructor(member: Member, path: JsonPath? = null) : this() {
        if (member.dataType != FLOAT64) throw illegalArg("The given member is not of float64 type")
        this.id = member.id
        this.dataType = FLOAT64
        this.path = path?.validate() ?: member.path
    }

    /** Retrieves the float64 value of this member from the given feature. */
    fun get(feature: NakshaFeature): Double? = readDouble(feature)

    /**
     * Retrieves the float64 value of this member from the given tuple.
     * TODO: When no such member exists in membersBook, should search along [path] in [tuple.featureBytes], but currently cannot due to JbDecoder2 limits.
     */
    @JsName("getFromTuple")
    fun get(tuple: Tuple): Double? = readDouble(tuple)

    /** Sets the float64 value of this member on the given object. */
    fun set(feature: PTypedMap<*, *>, value: Double?): Any? = if (value == null) feature.setPath(UNDEFINED, path) else feature.setPath(value, path)
}
