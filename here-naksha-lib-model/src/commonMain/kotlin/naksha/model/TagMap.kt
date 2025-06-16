@file:Suppress("OPT_IN_USAGE")

package naksha.model

import naksha.base.*
import naksha.base.Platform.Platform_C.forKClass
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

/**
 * Map of tags persisted as (key, value) pairs where values are nullable.
 * This class represents the persisted form of [TagList].
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
open class TagMap() : MapProxy<String, Any>(String_TYPE, Any_TYPE) {

    @JsName("of")
    constructor(tagList: TagList) : this() {
        tagList.filterNotNull()
            .map { TagNormalizer.splitNormalizedTag(it) }
            .forEach { (key, value) -> put(key, value) }
    }

    companion object TagMap_C {
        /**
         * The [PlatformType] of [TagMap].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE: PlatformType<TagMap> = forKClass(TagMap::class).withPackageName(PACKAGE_NAME)
    }

    /**
     * Convert this map into a list.
     * @return this map as tag-list.
     */
    fun toTagList(): TagList {
        val list = TagList()
        forEach { (key, value) ->
            list.add(flattenTag(key, value))
        }
        return list
    }

    /**
     * Copy all tags from the given tag-map.
     * @param other The other tag-map from which to copy.
     * @return this.
     * @since 3.0
     */
    fun copyFrom(other: TagMap?): TagMap {
        if (other != null) {
            for (e in other) this[e.key] = e.value
        }
        return this
    }

    /**
     * Copy all tags from the given tag-map.
     * @param other The other tag-map from which to copy.
     * @return this.
     * @since 3.0
     */
    @JsName("copyFromTagList")
    fun copyFrom(other: TagList?): TagMap
        = if (other != null) copyFrom(other.toTagMap()) else this

    /**
     * Converts (key, value) pair to String, so it can be part of [TagList].
     * The result depends on the value:
     * - Null value is omitted: ('foo', null) -> 'foo'
     * - String value is separated with simple '=': ('foo', 'bar') -> 'foo=bar'
     * - Numbers and booleans are separated with ':=' -> 'foo:=true', 'foo:=12.34'
     */
    private fun flattenTag(key: String, value: Any?): String =
        when (value) {
            null -> key
            is String -> "$key=$value"
            is Boolean, is Long, is Int64 -> "$key:=$value"
            is Number -> "$key:=${value.toDouble()}"
            else -> throw NakshaException(
                NakshaError.ILLEGAL_ARGUMENT,
                "Tag values can only be String, Boolean or Number"
            )
        }
}