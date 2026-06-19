package naksha.model.objects

import naksha.model.illegalArg
import naksha.model.illegalState
import naksha.model.objects.MemberType.MemberType_C.INT32
import kotlin.js.JsName

class Int32Member() : TypedMember<Int32Member>() {
    override fun verify(): Int32Member {
        if (dataType != INT32) {
            throw illegalState("The member was illegally cast, expected subtype: $INT32, found: $dataType")
        }
        return this
    }

    @JsName("of")
    constructor(name: String, path: JsonPath? = null) : this() {
        this.name = name
        this.dataType = INT32
        this.path = path ?: JsonPath(listOf("properties", name))
        this.path.validate()
    }

    @JsName("from")
    constructor(member: Member, path: JsonPath? = null) : this() {
        if (member.dataType != INT32) throw illegalArg("The given member is not of int32 type")
        this.name = member.name
        this.dataType = INT32
        this.path = path?.validate() ?: member.path
    }

    fun get(feature: NakshaFeature): Int? = getInt64(feature)?.toInt()
    fun set(feature: NakshaFeature, value: Int): Any? = setPath(feature, path, value)
}
