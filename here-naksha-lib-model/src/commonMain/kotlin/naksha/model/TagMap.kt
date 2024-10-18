@file:Suppress("OPT_IN_USAGE")

package naksha.model

import naksha.base.MapProxy
import kotlin.js.JsExport
import kotlin.js.JsName

// TODO: Document me!
//       Improve me!

@JsExport
open class TagMap() : MapProxy<String, Any>(String::class, Any::class) {

    @Suppress("LeakingThis")
    @JsName("of")
    constructor(tagList: TagList) : this() {
        tagList.filterNotNull()
            .map { TagNormalizer.splitNormalizedTag(it) }
            .forEach { tag -> put(tag.key, tag.value) }
    }

    /**
     * Convert this map into a list.
     * @return this map as tag-list.
     */
    fun toTagList(): TagList {
        val list = TagList()
        forEach { (key, value) ->
            list.add(Tag.of(key, value).tag)
        }
        return list
    }
}