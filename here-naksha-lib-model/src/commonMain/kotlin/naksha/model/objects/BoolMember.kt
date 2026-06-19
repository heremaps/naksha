package naksha.model.objects

import naksha.model.illegalArg
import naksha.model.illegalState
import naksha.model.objects.MemberType.MemberType_C.BOOLEAN
import kotlin.js.JsName

class BoolMember() : TypedMember<BoolMember>() {
    override fun verify(): BoolMember {
        if (dataType != BOOLEAN) {
            throw illegalState("The member was illegally cast, expected subtype: $BOOLEAN, found: $dataType")
        }
        return this
    }

    @JsName("of")
    constructor(name: String, path: JsonPath? = null) : this() {
        this.name = name
        this.dataType = BOOLEAN
        this.path = path ?: JsonPath(listOf("properties", name))
        this.path.validate()
    }

    @JsName("from")
    constructor(member: Member, path: JsonPath? = null) : this() {
        if (member.dataType != BOOLEAN) throw illegalArg("The given member is not of boolean type")
        this.name = member.name
        this.dataType = BOOLEAN
        this.path = path?.validate() ?: member.path // Only verify modified path.
    }

    fun get(feature: NakshaFeature): Boolean? = getBoolean(feature)
    fun set(feature: NakshaFeature, value: Boolean): Boolean? = setPath(feature, path, value) as Boolean?
}