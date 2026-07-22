package naksha.model.objects

import naksha.model.Tuple
import naksha.base.illegalArg
import naksha.base.illegalState
import naksha.model.objects.MemberType.MemberType_C.BYTE_ARRAY
import kotlin.js.JsName

class ByteArrayMember() : TypedMember<ByteArrayMember>() {
    override fun verify(): ByteArrayMember {
        if (dataType != BYTE_ARRAY) {
            throw illegalState("The member was illegally cast, expected subtype: $BYTE_ARRAY, found: $dataType")
        }
        return this
    }

    /** Creates a new byte array member with the given name and an optional custom JSON path. */
    @JsName("of")
    constructor(name: String, path: JsonPath? = null) : this() {
        this.name = name
        this.dataType = BYTE_ARRAY
        this.path = path ?: JsonPath("properties", name)
        this.path.validate()
    }

    /** Creates a byte array member from an existing [Member], validating its type. */
    @JsName("from")
    constructor(member: Member, path: JsonPath? = null) : this() {
        if (member.dataType != BYTE_ARRAY) throw illegalArg("The given member is not of byte_array type")
        this.name = member.name
        this.dataType = BYTE_ARRAY
        this.path = path?.validate() ?: member.path
    }

    /** Retrieves the byte array value of this member from the given feature. */
    fun get(feature: NakshaFeature): ByteArray? = readByteArray(feature)

    /**
     * Retrieves the byte array value of this member from the given tuple.
     * TODO: When no such member exists in membersBook, should search along [path] in [tuple.featureBytes], but currently cannot due to JbDecoder2 limits.
     */
    @JsName("getFromTuple")
    fun get(tuple: Tuple): ByteArray? = readByteArray(tuple)

    /** Sets the byte array value of this member on the given feature. */
    fun set(feature: NakshaFeature, value: ByteArray): Any? = setPath(feature, path, value)
}
