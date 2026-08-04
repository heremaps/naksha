package naksha.model.objects

import naksha.base.PTypedMap
import naksha.base.Base.BaseCompanion.UNDEFINED
import naksha.model.Tuple
import naksha.base.illegalArg
import naksha.base.illegalState
import naksha.model.objects.MemberType.MemberType_C.STRING
import kotlin.js.JsName

class StringMember() : TypedMember<StringMember>() {
    override fun verify(): StringMember {
        if (dataType != STRING) {
            throw illegalState("The member was illegally cast, expected subtype: $STRING, found: $dataType")
        }
        return this
    }

    /** Creates a new string member with the given name and an optional custom JSON path. */
    @JsName("of")
    constructor(name: String, path: JsonPath? = null) : this() {
        this.id = name
        this.dataType = STRING
        this.path = path ?: JsonPath("properties", name)
        this.path.validate()
    }

    /** Creates a string member from an existing [Member], validating its type. */
    @JsName("from")
    constructor(member: Member, path: JsonPath? = null) : this() {
        if (member.dataType != STRING) throw illegalArg("The given member is not of string type")
        this.id = member.id
        this.dataType = STRING
        this.path = path?.validate() ?: member.path
    }

    /** Retrieves the string value of this member from the given feature. */
    fun get(feature: NakshaFeature): String? = readString(feature)

    /**
     * Retrieves the string value of this member from the given tuple.
     * TODO: When no such member exists in membersBook, should search along [path] in [tuple.featureBytes], but currently cannot due to JbDecoder2 limits.
     */
    @JsName("getFromTuple")
    fun get(tuple: Tuple): String? = readString(tuple)

    /** Sets the string value of this member on the given object. */
    fun set(feature: PTypedMap<*, *>, value: String?): Any? = if (value == null) feature.setPath(UNDEFINED, path) else feature.setPath(value, path)
}
