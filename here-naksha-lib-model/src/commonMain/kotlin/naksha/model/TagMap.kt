@file:Suppress("OPT_IN_USAGE")

package naksha.model

import naksha.base.MapProxy
import naksha.model.request.query.*
import kotlin.js.JsExport
import kotlin.js.JsName

// TODO: Document me!
//       Improve me!

@JsExport
open class TagMap() : MapProxy<String, Tag>(String::class, Tag::class) {

    @Suppress("LeakingThis")
    @JsName("of")
    constructor(tagList: TagList) : this(){
        for (s in tagList) {
            if (s == null) continue
            val tag = Tag.parse(s)
            put(tag.key, tag)
        }
    }

    /**
     * Convert this map into a list.
     * @return this map as tag-list.
     */
    fun toTagList(): TagList {
        val list = TagList()
        for (e in this) {
            val tag = e.value?.tag
            if (tag != null) list.add(tag)
        }
        return list
    }
}