package naksha.model.objects

import naksha.base.Int64
import naksha.model.illegalArg
import naksha.model.illegalState
import naksha.model.objects.MemberType.MemberType_C.INT64
import kotlin.js.JsName

class Int64Member() : TypedMember<Int64Member>() {
    override fun verify(): Int64Member {
        if (dataType != INT64) {
            throw illegalState("The member was illegally cast, expected subtype: $INT64, found: $dataType")
        }
        return this
    }

    @JsName("of")
    constructor(name: String, path: JsonPath? = null) : this() {
        this.name = name
        this.dataType = INT64
        this.path = path ?: JsonPath(listOf("properties", name))
        this.path.validate()
    }

    @JsName("from")
    constructor(member: Member, path: JsonPath? = null) : this() {
        if (member.dataType != INT64) throw illegalArg("The given member is not of int64 type")
        this.name = member.name
        this.dataType = INT64
        this.path = path?.validate() ?: member.path
    }

    fun get(feature: NakshaFeature): Int64? = getInt64(feature)
    fun set(feature: NakshaFeature, value: Int64): Any? = setPath(feature, path, value)
}
