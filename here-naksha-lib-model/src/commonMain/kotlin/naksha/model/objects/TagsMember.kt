package naksha.model.objects

import naksha.model.TagMap
import naksha.model.Tuple
import naksha.base.illegalArg
import naksha.base.illegalState
import naksha.model.objects.MemberType.MemberType_C.TAG_MAP
import kotlin.js.JsName

class TagsMember() : TypedMember<TagsMember>() {
    override fun verify(): TagsMember {
        if (dataType != TAG_MAP) {
            throw illegalState("The member was illegally cast, expected subtype: $TAG_MAP, found: $dataType")
        }
        return this
    }

    /** Creates a new tag map member with the given name and an optional custom JSON path. */
    @JsName("of")
    constructor(name: String, path: JsonPath? = null) : this() {
        this.name = name
        this.dataType = TAG_MAP
        this.path = path ?: JsonPath("properties", name)
        this.path.validate()
    }

    /** Creates a tag map member from an existing [Member], validating its type. */
    @JsName("from")
    constructor(member: Member, path: JsonPath? = null) : this() {
        if (member.dataType != TAG_MAP) throw illegalArg("The given member is not of tags type")
        this.name = member.name
        this.dataType = TAG_MAP
        this.path = path?.validate() ?: member.path
    }

    /** Retrieves the tag map value of this member from the given feature. */
    fun get(feature: NakshaFeature): TagMap? = getTagMap(feature)

    /**
     * Retrieves the tag map value of this member from the given tuple.
     * TODO: When no such member exists in membersBook, should search along [path] in [tuple.featureBytes], but currently cannot due to JbDecoder2 limits.
     */
    @JsName("getFromTuple")
    fun get(tuple: Tuple): TagMap? = getTagMap(tuple)

    /** Sets the tag map value of this member on the given feature. */
    fun set(feature: NakshaFeature, value: TagMap): Any? = setPath(feature, path, value)
}
