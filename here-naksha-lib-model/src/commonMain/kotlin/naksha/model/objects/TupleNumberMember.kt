package naksha.model.objects

import naksha.base.PTypedMap
import naksha.base.Base.BaseCompanion.UNDEFINED
import naksha.model.Tuple
import naksha.base.TupleNumber
import naksha.base.illegalArg
import naksha.base.illegalState
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
        this.id = name
        this.dataType = TUPLE_NUMBER
        this.path = path ?: JsonPath("properties", name)
        this.path.validate()
    }

    /** Creates a tuple number member from an existing [Member], validating its type. */
    @JsName("from")
    constructor(member: Member, path: JsonPath? = null) : this() {
        if (member.dataType != TUPLE_NUMBER) throw illegalArg("The given member is not of tuple_number type")
        this.id = member.id
        this.dataType = TUPLE_NUMBER
        this.path = path?.validate() ?: member.path
    }

    /** Retrieves the tuple number value of this member from the given object. */
    fun get(feature: PTypedMap<*, *>): TupleNumber? = readTupleNumber(feature)

    /**
     * Retrieves the tuple number value of this member from the given tuple.
     * TODO: When no such member exists in membersBook, should search along [path] in [tuple.featureBytes], but currently cannot due to JbDecoder2 limits.
     */
    @JsName("getFromTuple")
    fun get(tuple: Tuple): TupleNumber? = readTupleNumber(tuple)

    /** Sets the tuple number value of this member on the given object. */
    fun set(feature: PTypedMap<*, *>, value: TupleNumber?): Any? = if (value == null) feature.setPath(UNDEFINED, path) else feature.setPath(value, path)
}