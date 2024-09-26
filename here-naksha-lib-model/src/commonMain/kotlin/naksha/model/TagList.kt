@file:Suppress("OPT_IN_USAGE")

package naksha.model

import naksha.base.ListProxy
import naksha.model.XyzNs.XyzNsCompanion.normalizeTag
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.js.JsStatic
import kotlin.jvm.JvmStatic

/**
 * A list of tags.
 */
@JsExport
open class TagList() : ListProxy<String>(String::class) {

    /**
     * Create a tag list from the given arguments; the tags are normalized.
     * @param tags the tags.
     */
    @JsName("of")
    constructor(vararg tags: String): this() {
        addTags(listOf(*tags), false)
    }

    companion object TagList_C {
        /**
         * Create a tag list from the given array; the tags are normalized.
         * @param tags the tags.
         * @return the tag-list.
         */
        @JvmStatic
        @JsStatic
        fun fromArray(tags: Array<String>): TagList {
            val list = TagList()
            list.addAndNormalizeTags(*tags)
            return list
        }
    }

    /**
     * Returns 'true' if the tag was removed, 'false' if it was not present.
     *
     * @param tag       The normalized tag to remove.
     * @param normalize `true` if the tag should be normalized before trying to remove; `false` if the tag is normalized.
     * @return true if the tag was removed; false otherwise.
     */
    fun removeTag(tag: String, normalize: Boolean): Boolean {
        val tagToRemove = if (normalize) normalizeTag(tag) else tag
        return this.remove(tagToRemove)
    }

    /**
     * Removes the given tags.
     *
     * @param tags      The tags to remove.
     * @param normalize `true` if the tags should be normalized before trying to remove; `false` if the tags are normalized.
     * @return this.
     */
    fun removeTags(tags: List<String>?, normalize: Boolean): TagList {
        if (tags.isNullOrEmpty()) {
            return this
        }
        if (normalize) {
            for (tag in tags) {
                val normalizedTag = normalizeTag(tag)
                remove(normalizedTag)
            }
        } else {
            removeAll(tags)
        }
        return this
    }

    /**
     * Removes tags starting with prefix
     *
     * @param prefix string prefix.
     * @return this.
     */
    fun removeTagsWithPrefix(prefix: String?): TagList {
        if (isEmpty() || prefix == null) {
            return this
        }
        val tagsToRemove = this.filter { tag -> tag?.startsWith(prefix) ?: false }
        removeAll(tagsToRemove)
        return this
    }

    /**
     * Removes tags starting with given list of prefixes
     *
     * @param prefixes list of tag prefixes
     * @return this.
     */
    fun removeTagsWithPrefixes(prefixes: List<String?>?): TagList {
        if (prefixes != null) {
            for (prefix in prefixes) {
                if (prefix != null) removeTagsWithPrefix(prefix)
            }
        }
        return this
    }

    /**
     * Returns 'true' if the tag added, 'false' if it was already present.
     *
     * @param tag the tag to add.
     * @param normalize `true` if the tag should be normalized; `false` otherwise.
     * @return true if the tag added; false otherwise.
     */
    fun addTag(tag: String, normalize: Boolean = true): Boolean {
        val tagToAdd = if (normalize) normalizeTag(tag) else tag

        if (!contains(tagToAdd)) {
            add(tagToAdd)
            return true
        }
        return false
    }

    /**
     * Add the given tags.
     *
     * @param tags the tags to add.
     * @param normalize `true` if the given tags should be normalized; `false`, if they are already normalized.
     * @return this.
     */
    fun addTags(tags: List<String>?, normalize: Boolean = true): TagList {
        if (!tags.isNullOrEmpty()) {
            if (normalize) {
                for (s in tags) {
                    val tag: String = normalizeTag(s)
                    if (!contains(tag)) {
                        add(tag)
                    }
                }
            } else {
                addAll(tags)
            }
        }
        return this
    }

    /**
     * Add and normalize all given tags.
     *
     * @param tags the tags to normalize and add.
     * @return this.
     */
    fun addAndNormalizeTags(vararg tags: String): TagList {
        if (tags.isNotEmpty()) {
            for (s in tags) {
                val tag: String = normalizeTag(s)
                if (!contains(tag)) {
                    add(tag)
                }
            }
        }
        return this
    }

    /**
     * Convert this tag-list into a tag-map.
     * @return this tag-list as tag-map.
     */
    fun toTagMap(): TagMap = TagMap(this)
}
