package naksha.model.objects

import naksha.model.illegalArg
import naksha.model.illegalState
import naksha.model.objects.MemberType.MemberType_C.TUPLE_NUMBER
import kotlin.js.JsName

class TupleNumberMember() : TypedMember<TupleNumberMember>() {
    override fun verify(): TupleNumberMember {
        if (dataType != TUPLE_NUMBER) {
            throw illegalState("The member was illegally cast, expected subtype: $TUPLE_NUMBER, found: $dataType")
        }
        return this
    }

    @JsName("of")
    constructor(name: String, path: JsonPath? = null) : this() {
        this.name = name
        this.dataType = TUPLE_NUMBER
        this.path = path ?: JsonPath(listOf("properties", name))
        this.path.validate()
    }

    @JsName("from")
    constructor(member: Member, path: JsonPath? = null) : this() {
        if (member.dataType != TUPLE_NUMBER) throw illegalArg("The given member is not of tuple_number type")
        this.name = member.name
        this.dataType = TUPLE_NUMBER
        this.path = path?.validate() ?: member.path
    }

    fun get(feature: NakshaFeature): TupleNumber? = getTupleNumber(feature)
    fun set(feature: NakshaFeature, value: TupleNumber): Any? = setPath(feature, path, value)
}
