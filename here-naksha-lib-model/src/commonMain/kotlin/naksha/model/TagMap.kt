@file:Suppress("OPT_IN_USAGE")

package naksha.model

import naksha.base.MapProxy
import naksha.model.objects.NakshaFeature
import kotlin.js.JsExport
import kotlin.js.JsName

/**
 * Map of tags persisted as (key, value) pairs where values are nullable.
 * This class represents the persisted form of [TagList] / [Tag].
 * It is stored as byte_array and can be accessed in PG via `naksha_tags` function.
 *
 * It is advised to only construct it in one of two ways:
 * 1) Via [TagList]-based constructor
 * 2) By deserializing byte array fetched from DB
 *
 * If for some reason, one would like to use it otherwise, it is advised to properly prepare tags upfront
 * with use of [TagNormalizer] (that is used for example by [TagList])
 */
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