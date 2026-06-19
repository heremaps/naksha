package naksha.model.objects

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

    @JsName("of")
    constructor(name: String, path: JsonPath? = null) : this() {
        this.name = name
        this.dataType = INT16
        this.path = path ?: JsonPath(listOf("properties", name))
        this.path.validate()
    }

    @JsName("from")
    constructor(member: Member, path: JsonPath? = null) : this() {
        if (member.dataType != INT16) throw illegalArg("The given member is not of int16 type")
        this.name = member.name
        this.dataType = INT16
        this.path = path?.validate() ?: member.path
    }

    fun get(feature: NakshaFeature): Short? = getInt64(feature)?.toShort()
    fun set(feature: NakshaFeature, value: Short): Any? = setPath(feature, path, value)
}
