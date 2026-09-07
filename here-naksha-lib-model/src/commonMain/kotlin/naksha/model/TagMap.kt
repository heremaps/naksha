@file:Suppress("OPT_IN_USAGE")

package naksha.model

import naksha.base.MapProxy
import naksha.base.NakshaError
import naksha.base.NakshaException
import naksha.base.illegalArg
import naksha.model.TagNormalizer.TagNormalizer_C.joinTag
import naksha.model.TagNormalizer.TagNormalizer_C.splitTag
import kotlin.js.JsExport
import kotlin.js.JsName

/**
 * Map of tags with values as _(key, value)_ pairs. Beware that values are limited to the following primitives:
 * - `null`
 * - [Boolean]
 * - [Double]
 * - [String]
 * @since 3.0
 */
@JsExport
class TagMap() : MapProxy<String, Any>(String::class, Any::class) {

    /**
     * Construct a new [TagMap] from the given list of key-value pairs.
     * @param pairs a list of keys and values with the key being a [String] and the value being `null`, [Boolean], [Number] or [String].
     * @since 3.0
     * @throws NakshaException if the key is not a [String] or the value of any pair is not `null`, [Boolean], [Number] or [String].
     */
    @JsName("newTagMap")
    constructor(vararg pairs: Any?) : this() {
        var i = 0
        while (i < pairs.size) {
            val key: String = pairs[i] as? String? ?: throw illegalArg("The given key at $i is no String: ${pairs[i]}")
            val value = if (++i < pairs.size) when(val v = pairs[i++]) {
                null -> null
                is Boolean -> v
                is String -> v
                is Double -> v
                is Number -> v.toDouble()
                else -> throw illegalArg("The given value is not boolean, a number or a string: $v")
            } else null
            i++
            this[key] = value
        }
    }

    /**
     * Construct a new [TagMap] from the given list of key-value pairs.
     * @param tags the key-value pairs.
     * @since 3.0
     * @throws NakshaException if the value of any pair is not `null`, [Boolean], [Number] or [String].
     */
    @JsName("ofPairs")
    constructor(vararg tags: Pair<String, Any?>) : this() {
        for ((k, v) in tags) {
            this[k] = when (v) {
                null -> null
                is Boolean -> v
                is String -> v
                is Double -> v
                is Number -> v.toDouble()
                else -> throw illegalArg("The given value is not boolean, a number or a string: $v")
            }
        }
    }

    /**
     * Construct a [TagMap] from the given [TagList].
     * @param tagList the [TagList] to split and insert into this map.
     * @since 3.0
     */
    @JsName("ofTagList")
    constructor(tagList: TagList) : this() {
        for (tag in tagList) {
            if (tag == null) continue
            val pair = splitTag(tag)
            put(pair.first, pair.second)
        }
    }

    /**
     * Convert this map into a list.
     * @return this map as tag-list.
     */
    fun toTagList(): TagList {
        val list = TagList()
        forEach { (key, value) -> list.add(joinTag(key, value)) }
        return list
    }
}
