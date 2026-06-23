package naksha.model.objects

import naksha.model.TagMap
import naksha.model.illegalArg
import naksha.model.illegalState
import naksha.model.objects.MemberType.MemberType_C.TAG_MAP
import kotlin.js.JsName

class TagsMember() : TypedMember<TagsMember>() {
    override fun verify(): TagsMember {
        if (dataType != TAG_MAP) {
            throw illegalState("The member was illegally cast, expected subtype: $TAG_MAP, found: $dataType")
        }
        return this
    }

    @JsName("of")
    constructor(name: String, path: JsonPath? = null) : this() {
        this.name = name
        this.dataType = TAG_MAP
        this.path = path ?: JsonPath(listOf("properties", name))
        this.path.validate()
    }

    @JsName("from")
    constructor(member: Member, path: JsonPath? = null) : this() {
        if (member.dataType != TAG_MAP) throw illegalArg("The given member is not of tags type")
        this.name = member.name
        this.dataType = TAG_MAP
        this.path = path?.validate() ?: member.path
    }

    fun get(feature: NakshaFeature): TagMap? = getTagMap(feature)
    fun set(feature: NakshaFeature, value: TagMap): Any? = setPath(feature, path, value)
}
