package naksha.model.objects

import naksha.base.ListProxy
import naksha.model.TagList
import naksha.model.illegalArg
import naksha.model.illegalState
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
        this.name = name
        this.dataType = TAG_LIST
        this.path = path ?: JsonPath(listOf("properties", name))
        this.path.validate()
    }

    /** Creates a tag list member from an existing [Member], validating its type. */
    @JsName("from")
    constructor(member: Member, path: JsonPath? = null) : this() {
        if (member.dataType != TAG_LIST) throw illegalArg("The given member is not of tag_list type")
        this.name = member.name
        this.dataType = TAG_LIST
        this.path = path?.validate() ?: member.path
    }

    /** Retrieves the tag list value of this member from the given feature. */
    fun get(feature: NakshaFeature): TagList? = getTagList(feature)

    /** Sets the tag list value of this member on the given feature. */
    fun set(feature: NakshaFeature, value: ListProxy<*>): Any? = setPath(feature, path, value)
}
