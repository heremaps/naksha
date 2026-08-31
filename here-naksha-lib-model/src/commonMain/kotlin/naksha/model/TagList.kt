@file:Suppress("OPT_IN_USAGE")

package naksha.model

import naksha.base.StringList
import naksha.base.illegalArg
import naksha.model.TagNormalizer.TagNormalizer_C.normalizeTag
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.js.JsStatic
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic

/**
 * A list of tags.
 */
@JsExport
class TagList() : StringList() {

    /**
     * Create a tag list from the given arguments; the tags are normalized.
     * @param tags the tags.
     * @param skipNormalize if normalization should be skipped; expects then that the given values are already normalized.
     */
    @JvmOverloads
    @JsName("of")
    constructor(vararg tags: String, skipNormalize: Boolean = false): this() {
        setCapacity(tags.size)
        for (tag in tags) addTag(tag, !skipNormalize)
    }

    /**
     * Create a tag list from the given list; the tags are normalized.
     * @param tags the tags.
     * @param skipInvalid if invalid values in the given should be skipped; otherwise an exception is raised.
     * @param skipNormalize if normalization should be skipped; expects then that the given values are already normalized.
     * @throws naksha.base.NakshaException with error [ILLEGAL_ARGUMENT][naksha.base.NakshaError.ILLEGAL_ARGUMENT] if the given list contains `null` or valus not being `String` and [skipInvalid] is _false_.
     */
    @JvmOverloads
    @JsName("ofList")
    constructor(tags: List<*>, skipInvalid: Boolean = false, skipNormalize: Boolean = false): this() {
        setCapacity(tags.size)
        for (i in 0 until tags.size) {
            val tag = tags[i]
            if (tag is String) addTag(tag, skipInvalid)
            else if (tag is Char || tag is CharSequence) addTag(tag.toString(), !skipNormalize)
            else if (!skipInvalid) throw illegalArg("The tag $i is no string: $tag")
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

    companion object TagList_C {
        /**
         * Create a tag list from the given array. Values being `null` or no [String] are ignored.
         * @param tags the tags.
         * @param skipInvalid if invalid values in the given should be skipped; otherwise an exception is raised.
         * @param skipNormalize if normalization should be skipped; expects then that the given values are already normalized.
         * @throws naksha.base.NakshaException with error [ILLEGAL_ARGUMENT][naksha.base.NakshaError.ILLEGAL_ARGUMENT] if the given list contains `null` or valus not being `String` and [skipInvalid] is _false_.
         * @return the tag-list.
         * @since 3.0
         */
        @JvmStatic
        @JsStatic
        @JvmOverloads
        fun fromArray(tags: Array<*>, skipInvalid: Boolean = false, skipNormalize: Boolean = false): TagList = TagList().apply {
            setCapacity(tags.size)
            for (i in 0 until tags.size) {
                val tag = tags[i]
                if (tag is String) addTag(tag, !skipNormalize)
                else if (tag is Char || tag is CharSequence) addTag(tag.toString(), !skipNormalize)
                else if (!skipInvalid) throw illegalArg("The tag #$i is no string: $tag")
            }
        }

        /**
         * A method to normalize a list of tags.
         *
         * @param tags a list of tags.
         * @return the same list, just that the content is normalized.
         */
        @JvmStatic
        @JsStatic
        fun normalizeTags(tags: TagList?): TagList? {
            if (!tags.isNullOrEmpty()) {
                for ((idx, tag) in tags.withIndex()) {
                    if (tag != null) {
                        tags[idx] = normalizeTag(tag)
                    }
                }
            }
            return tags
        }
    }
}
