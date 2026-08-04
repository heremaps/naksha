package naksha.model.objects

import naksha.model.Tuple
import naksha.base.PTypedMap
import naksha.base.Base.BaseCompanion.UNDEFINED
import naksha.base.illegalArg
import naksha.base.illegalState
import naksha.model.objects.MemberType.MemberType_C.BOOLEAN
import kotlin.js.JsName

class BoolMember() : TypedMember<BoolMember>() {
    override fun verify(): BoolMember {
        if (dataType != BOOLEAN) {
            throw illegalState("The member was illegally cast, expected subtype: $BOOLEAN, found: $dataType")
        }
        return this
    }

    /** Creates a new boolean member with the given name and an optional custom JSON path. */
    @JsName("of")
    constructor(name: String, path: JsonPath? = null) : this() {
        this.id = name
        this.dataType = BOOLEAN
        this.path = path ?: JsonPath("properties", name)
        this.path.validate()
    }

    /** Creates a boolean member from an existing [Member], validating its type. */
    @JsName("from")
    constructor(member: Member, path: JsonPath? = null) : this() {
        if (member.dataType != BOOLEAN) throw illegalArg("The given member is not of boolean type")
        this.id = member.id
        this.dataType = BOOLEAN
        this.path = path?.validate() ?: member.path
    }

    /** Retrieves the boolean value of this member from the given feature. */
    fun get(feature: NakshaFeature): Boolean? = readBoolean(feature)

    /**
     * Retrieves the boolean value of this member from the given tuple.
     * TODO: When no such member exists in membersBook, should search along [path] in [tuple.featureBytes], but currently cannot due to JbDecoder2 limits.
     */
    @JsName("getFromTuple")
    fun get(tuple: Tuple): Boolean? = readBoolean(tuple)

    /** Sets the boolean value of this member on the given object. */
    fun set(feature: PTypedMap<*, *>, value: Boolean?): Any? = if (value == null) feature.setPath(UNDEFINED, path) else feature.setPath(value, path)
}