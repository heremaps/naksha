package naksha.model.objects

import naksha.model.illegalArg
import naksha.model.illegalState
import naksha.model.objects.MemberType.MemberType_C.FLOAT64
import kotlin.js.JsName

class Float64Member() : TypedMember<Float64Member>() {
    override fun verify(): Float64Member {
        if (dataType != FLOAT64) {
            throw illegalState("The member was illegally cast, expected subtype: $FLOAT64, found: $dataType")
        }
        return this
    }

    @JsName("of")
    constructor(name: String, path: JsonPath? = null) : this() {
        this.name = name
        this.dataType = FLOAT64
        this.path = path ?: JsonPath(listOf("properties", name))
        this.path.validate()
    }

    @JsName("from")
    constructor(member: Member, path: JsonPath? = null) : this() {
        if (member.dataType != FLOAT64) throw illegalArg("The given member is not of float64 type")
        this.name = member.name
        this.dataType = FLOAT64
        this.path = path?.validate() ?: member.path
    }

    fun get(feature: NakshaFeature): Double? = getDouble(feature)
    fun set(feature: NakshaFeature, value: Double): Any? = setPath(feature, path, value)
}
