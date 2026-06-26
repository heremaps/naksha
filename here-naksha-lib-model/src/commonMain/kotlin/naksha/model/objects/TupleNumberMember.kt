package naksha.model.objects

import naksha.model.TupleNumber
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

    /** Creates a new tuple number member with the given name and an optional custom JSON path. */
    @JsName("of")
    constructor(name: String, path: JsonPath? = null) : this() {
        this.name = name
        this.dataType = TUPLE_NUMBER
        this.path = path ?: JsonPath(listOf("properties", name))
        this.path.validate()
    }

    /** Creates a tuple number member from an existing [Member], validating its type. */
    @JsName("from")
    constructor(member: Member, path: JsonPath? = null) : this() {
        if (member.dataType != TUPLE_NUMBER) throw illegalArg("The given member is not of tuple_number type")
        this.name = member.name
        this.dataType = TUPLE_NUMBER
        this.path = path?.validate() ?: member.path
    }

    /** Retrieves the tuple number value of this member from the given feature. */
    fun get(feature: NakshaFeature): TupleNumber? = getTupleNumber(feature)

    /** Sets the tuple number value of this member on the given feature. */
    fun set(feature: NakshaFeature, value: TupleNumber): Any? = setPath(feature, path, value)
}
