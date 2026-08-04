package naksha.model.objects

import naksha.base.PTypedArray
import naksha.model.TagList
import naksha.model.Tuple
import naksha.base.PTypedMap
import naksha.base.Base.BaseCompanion.UNDEFINED
import naksha.base.illegalArg
import naksha.base.illegalState
import naksha.model.objects.MemberType.MemberType_C.TAG_LIST
import kotlin.js.JsName

class TagListMember() : TypedMember<TagListMember>() {
    override fun verify(): TagListMember {
        if (dataType != TAG_LIST) {
            throw illegalState("The member was illegally cast, expected subtype: $TAG_LIST, found: $dataType")
        }
        return this
    }

    /** Creates a new tag list member with the given name and an optional custom JSON path. */
    @JsName("of")
    constructor(name: String, path: JsonPath? = null) : this() {
        this.id = name
        this.dataType = TAG_LIST
        this.path = path ?: JsonPath("properties", name)
        this.path.validate()
    }

    /** Creates a tag list member from an existing [Member], validating its type. */
    @JsName("from")
    constructor(member: Member, path: JsonPath? = null) : this() {
        if (member.dataType != TAG_LIST) throw illegalArg("The given member is not of tag_list type")
        this.id = member.id
        this.dataType = TAG_LIST
        this.path = path?.validate() ?: member.path
    }

    /** Retrieves the tag list value of this member from the given feature. */
    fun get(feature: NakshaFeature): TagList? = readTagList(feature)

    /**
     * Retrieves the tag list value of this member from the given tuple.
     * TODO: When no such member exists in membersBook, should search along [path] in [tuple.featureBytes], but currently cannot due to JbDecoder2 limits.
     */
    @JsName("getFromTuple")
    fun get(tuple: Tuple): TagList? = readTagList(tuple)

    /** Sets the tag list value of this member on the given object. */
    fun set(feature: PTypedMap<*, *>, value: PTypedArray<*>?): Any? = if (value == null) feature.setPath(UNDEFINED, path) else feature.setPath(value, path)
}
