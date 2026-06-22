package naksha.model.objects

import naksha.base.AnyList
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

    @JsName("of")
    constructor(name: String, path: JsonPath? = null) : this() {
        this.name = name
        this.dataType = TAG_LIST
        this.path = path ?: JsonPath(listOf("properties", name))
        this.path.validate()
    }

    @JsName("from")
    constructor(member: Member, path: JsonPath? = null) : this() {
        if (member.dataType != TAG_LIST) throw illegalArg("The given member is not of tag_list type")
        this.name = member.name
        this.dataType = TAG_LIST
        this.path = path?.validate() ?: member.path
    }

    fun get(feature: NakshaFeature): AnyList? = getTagList(feature)
    fun set(feature: NakshaFeature, value: AnyList): Any? = setPath(feature, path, value)
}
