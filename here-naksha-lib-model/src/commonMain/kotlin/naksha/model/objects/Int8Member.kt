package naksha.model.objects

import naksha.model.illegalArg
import naksha.model.illegalState
import naksha.model.objects.MemberType.MemberType_C.INT8
import kotlin.js.JsName

class Int8Member() : TypedMember<Int8Member>() {
    override fun verify(): Int8Member {
        if (dataType != INT8) {
            throw illegalState("The member was illegally cast, expected subtype: $INT8, found: $dataType")
        }
        return this
    }

    @JsName("of")
    constructor(name: String, path: JsonPath? = null) : this() {
        this.name = name
        this.dataType = INT8
        this.path = path ?: JsonPath(listOf("properties", name))
        this.path.validate()
    }

    @JsName("from")
    constructor(member: Member, path: JsonPath? = null) : this() {
        if (member.dataType != INT8) throw illegalArg("The given member is not of int8 type")
        this.name = member.name
        this.dataType = INT8
        this.path = path?.validate() ?: member.path
    }

    fun get(feature: NakshaFeature): Byte? = getInt64(feature)?.toByte()
    fun set(feature: NakshaFeature, value: Byte): Any? = setPath(feature, path, value)
}
