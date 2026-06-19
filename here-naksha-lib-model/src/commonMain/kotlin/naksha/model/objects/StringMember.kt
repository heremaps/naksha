package naksha.model.objects

import naksha.model.illegalArg
import naksha.model.illegalState
import naksha.model.objects.MemberType.MemberType_C.STRING
import kotlin.js.JsName

class StringMember() : TypedMember<StringMember>() {
    override fun verify(): StringMember {
        if (dataType != STRING) {
            throw illegalState("The member was illegally cast, expected subtype: $STRING, found: $dataType")
        }
        return this
    }

    @JsName("of")
    constructor(name: String, path: JsonPath? = null) : this() {
        this.name = name
        this.dataType = STRING
        this.path = path ?: JsonPath(listOf("properties", name))
        this.path.validate()
    }

    @JsName("from")
    constructor(member: Member, path: JsonPath? = null) : this() {
        if (member.dataType != STRING) throw illegalArg("The given member is not of string type")
        this.name = member.name
        this.dataType = STRING
        this.path = path?.validate() ?: member.path
    }

    fun get(feature: NakshaFeature): String? = getString(feature)
    fun set(feature: NakshaFeature, value: String): Any? = setPath(feature, path, value)
}
