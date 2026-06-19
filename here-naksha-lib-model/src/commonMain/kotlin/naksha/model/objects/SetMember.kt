package naksha.model.objects

import naksha.base.AnyList
import naksha.model.illegalArg
import naksha.model.illegalState
import naksha.model.objects.MemberType.MemberType_C.SET
import kotlin.js.JsName

class SetMember() : TypedMember<SetMember>() {
    override fun verify(): SetMember {
        if (dataType != SET) {
            throw illegalState("The member was illegally cast, expected subtype: $SET, found: $dataType")
        }
        return this
    }

    @JsName("of")
    constructor(name: String, path: JsonPath? = null) : this() {
        this.name = name
        this.dataType = SET
        this.path = path ?: JsonPath(listOf("properties", name))
        this.path.validate()
    }

    @JsName("from")
    constructor(member: Member, path: JsonPath? = null) : this() {
        if (member.dataType != SET) throw illegalArg("The given member is not of set type")
        this.name = member.name
        this.dataType = SET
        this.path = path?.validate() ?: member.path
    }

    fun get(feature: NakshaFeature): AnyList? = getSet(feature)
    fun set(feature: NakshaFeature, value: AnyList): Any? = setPath(feature, path, value)
}
