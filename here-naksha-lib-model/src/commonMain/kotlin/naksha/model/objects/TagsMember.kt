package naksha.model.objects

import naksha.model.illegalArg
import naksha.model.illegalState
import naksha.model.objects.MemberType.MemberType_C.TAGS
import kotlin.js.JsName

class TagsMember() : TypedMember<TagsMember>() {
    override fun verify(): TagsMember {
        if (dataType != TAGS) {
            throw illegalState("The member was illegally cast, expected subtype: $TAGS, found: $dataType")
        }
        return this
    }

    @JsName("of")
    constructor(name: String, path: JsonPath? = null) : this() {
        this.name = name
        this.dataType = TAGS
        this.path = path ?: JsonPath(listOf("properties", name))
        this.path.validate()
    }

    @JsName("from")
    constructor(member: Member, path: JsonPath? = null) : this() {
        if (member.dataType != TAGS) throw illegalArg("The given member is not of tags type")
        this.name = member.name
        this.dataType = TAGS
        this.path = path?.validate() ?: member.path
    }

    fun get(feature: NakshaFeature): TagMap? = getTagMap(feature)
    fun set(feature: NakshaFeature, value: TagMap): Any? = setPath(feature, path, value)
}
