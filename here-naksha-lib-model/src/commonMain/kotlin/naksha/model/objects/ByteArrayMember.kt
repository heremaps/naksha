package naksha.model.objects

import naksha.model.illegalArg
import naksha.model.illegalState
import naksha.model.objects.MemberType.MemberType_C.BYTE_ARRAY
import kotlin.js.JsName

class ByteArrayMember() : TypedMember<ByteArrayMember>() {
    override fun verify(): ByteArrayMember {
        if (dataType != BYTE_ARRAY) {
            throw illegalState("The member was illegally cast, expected subtype: $BYTE_ARRAY, found: $dataType")
        }
        return this
    }

    @JsName("of")
    constructor(name: String, path: JsonPath? = null) : this() {
        this.name = name
        this.dataType = BYTE_ARRAY
        this.path = path ?: JsonPath(listOf("properties", name))
        this.path.validate()
    }

    @JsName("from")
    constructor(member: Member, path: JsonPath? = null) : this() {
        if (member.dataType != BYTE_ARRAY) throw illegalArg("The given member is not of byte_array type")
        this.name = member.name
        this.dataType = BYTE_ARRAY
        this.path = path?.validate() ?: member.path
    }

    fun get(feature: NakshaFeature): ByteArray? = getByteArray(feature)
    fun set(feature: NakshaFeature, value: ByteArray): Any? = setPath(feature, path, value)
}
