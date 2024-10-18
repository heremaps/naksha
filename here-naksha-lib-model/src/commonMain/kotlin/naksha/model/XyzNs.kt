@file:Suppress("OPT_IN_USAGE")

package naksha.model

import naksha.base.*
import naksha.model.TagNormalizer.normalizeTag
import kotlin.DeprecationLevel.WARNING
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmStatic

/**
 * The XYZ namespace stored in [properties.@ns:com:here:xyz][NakshaProperties.XYZ] of the [NakshaFeature].
 */
@JsExport
class XyzNs : AnyObject() {

    companion object XyzNsCompanion {
        private val ACTION = NotNullEnum<XyzNs, Action>(Action::class) { _, _ -> Action.CREATED }
        private val STRING = NotNullProperty<XyzNs, String>(String::class) { _, name ->
            throw IllegalStateException("The field $name must have a value")
        }
        private val STRING_NULL = NullableProperty<XyzNs, String>(String::class)
        private val INT = NotNullProperty<XyzNs, Int>(Int::class) { _, name ->
            throw IllegalStateException("The field $name must have a value")
        }
        private val INT64 = NotNullProperty<XyzNs, Int64>(Int64::class) { _, name ->
            throw IllegalStateException("The field $name must have a value")
        }
        private val INT64_NULL = NullableProperty<XyzNs, Int64>(Int64::class)
        private val TAGS = NullableProperty<XyzNs, TagList>(TagList::class)


    }

    /**
     * The universal unique identifier of the state of a feature.
     *
     * This field is populated by Interactive API, Data Hub, XYZ Hub and Naksha. Any values provided by the user are overwritten.
     * - **Interactive API**: This field is set, when history is enabled for the layer.
     * - **Data Hub**: This field is set, when history or UUID is enabled for the space.
     * - **XYZ Hub**: This field is set when history or UUID is enabled for the space.
     * - **Naksha**: This field is always set, but does not store a real UUID, but rather a [Guid] (global unique identifier).
     */
    val uuid: String by STRING

    /**
     * Returns the [uuid] as [Guid].
     * @return the [uuid] as [Guid].
     */
    val guid: Guid by lazy { Guid.fromString(uuid) }

    /**
     * The universal unique identifier of the previous state of a feature.
     *
     * This field is populated by Interactive API, Data Hub, XYZ Hub and Naksha. Any values provided by the user are overwritten.
     * - **Interactive API**: This field is set, when history is enabled for the layer.
     * - **Data Hub**: This field is set, when history or UUID is enabled for the space.
     * - **XYZ Hub**: This field is set when history or UUID is enabled for the space.
     * - **Naksha**: This field is always set, but does not store a real UUID, but rather a GUID (global unique identifier).
     */
    val puuid: String? by STRING_NULL

    /**
     * Returns the [puuid] as [Guid].
     * @return the [puuid] as [Guid].
     */
    val pguid: Guid?
        get() {
            val puuid = this.puuid
            return if (puuid == null) null else Guid.fromString(puuid)
        }

    /**
     * The universal unique identifier of the state of the feature that was used to merge with the previous state to produce this state.
     *
     * This happens when concurrent modifications are done, but an automatic merge was possible. This field is populated by Interactive
     * API, Data Hub or XYZ Hub. Any values provided by the user are overwritten.
     * - **Interactive API**: This field is set when history is enabled for the layer.
     * - **Data Hub**: This field is set when history or UUID is enabled for the space.
     * - **XYZ Hub**: This field is set when history or UUID is enabled for the space.
     * - **Naksha**: Does not support this field, it will always be _null_.
     */
    @Deprecated("This field is not supported by Naksha, but part of MOM specification", level = WARNING)
    val muuid: String? by STRING_NULL

    /**
     * The time when this feature was created.
     *
     * The value is a valid Unix timestamp which is the number of milliseconds since January 1st, 1970, leap seconds are ignored. This
     * field is populated by Interactive API, Data Hub, XYZ Hub and Naksha. Any values provided by the user are overwritten.
     * - **Interactive API**: Always sets this field.
     * - **Data Hub**: Always sets this field.
     * - **XYZ Hub**: Always sets this field.
     * - **Naksha**: Always sets this field.
     */
    val createdAt: Int64 by INT64

    /**
     * The last time when this feature was modified.
     *
     * The value is a valid Unix timestamp which is the number of milliseconds since January 1st, 1970, leap seconds are ignored. This
     * field is populated by Interactive API, Data Hub, XYZ Hub and Naksha. Any values provided by the user are overwritten.
     * - **Interactive API**: Always sets this field.
     * - **Data Hub**: Always sets this field.
     * - **XYZ Hub**: Always sets this field.
     * - **Naksha**: Always sets this field.
     */
    val updatedAt: Int64 by INT64

    /**
     * The space in which this feature is located.
     *
     * This field is populated by Interactive API, Data Hub and XYZ Hub. It always represents the current space where the feature resides
     * and is automatically set when persisting a feature. Any values provided by the user are overwritten.
     * - **Interactive API**: Always sets this field.
     * - **Data Hub**: Always sets this field.
     * - **XYZ Hub**: Always sets this field.
     * - **Naksha**: Does not support this field, it will always be _null_.
     */
    @Deprecated("This field is not supported by Naksha, but part of MOM specification", level = WARNING)
    val space: String? by STRING_NULL

    /**
     * Customer defined tags for this feature.
     *
     * This field is populated by the client.
     * - **Interactive API**: Does not change the value of this field.
     * - **Data Hub**: Can add or remove some values, depending on the use of query parameters addTags and removeTags.
     * - **XYZ Hub**: Can add or remove some values, depending on the use of the query parameters addTags and removeTags.
     * - **Naksha**: Allows event-handlers in the pipeline to modify the values. The values are interpreted, they have an intrinsic
     * specific encoding and are split for indexing, so they encode a key-value pair, and the value can be searched (e.g. `name=Foo` or
     * `age:=5`). The server guarantees that when two tags have the same key, they are collapsed, by the later version overriding the
     * previous one.
     */
    var tags: TagList? by TAGS

    /**
     * The version of the feature - temporarily we use `txn` as name, because version is expected to be Int, not Int64.
     *
     * Multiple features could be part of a single version if they have been edited in one transaction. This field is populated by
     * Interactive API, Data Hub, XYZ Hub and Naksha. Any values provided by the user are overwritten.
     * - **Interactive API**: This field is set when history is enabled for the layer.
     * - **Data Hub**: This field is set when history or UUID is enabled for the space.
     * - **XYZ Hub**: This field is set when history or UUID is enabled for the space.
     * - **Naksha**: This field stores the transaction-number (`txn`).
     */
    val txn: Int64 by INT64

    /**
     * The change-count, so how often the feature has been changed since it was created. The value starts with 1.
     *
     * This field is populated only by **Naksha**. Any values provided by the user are overwritten.
     */
    val changeCount: Int by INT

    /**
     * The change that was applied to the feature.
     *
     * This field is populated only by **Naksha**. Any values provided by the user are overwritten.
     */
    val action: Action by ACTION

    /**
     * The identifier of the application that modified the feature the last.
     *
     * This field is populated only by **Naksha**. Any values provided by the user are overwritten.
     */
    val appId: String by STRING

    /**
     * The author of the feature. Not every change of feature is done by intention, the author is only set, when the change of the
     * feature was done by intention and not as a side effect. For example, repair bots will not claim authorship, but cause the [appId]
     * to change.
     *
     * This field is populated only by **Naksha**. Any values provided by the user are overwritten.
     */
    val author: String? by STRING_NULL

    /**
     * The time when this author of the feature was modified.
     *
     * The value is a valid Unix timestamp which is the number of milliseconds since January 1st, 1970, leap seconds are ignored. This
     * field is populated only by **Naksha**. Any values provided by the user are overwritten.
     */
    val authorTs: Int64? by INT64_NULL

    /**
     * The hash above the feature, calculated server side.
     *
     * This field is populated only by **Naksha**. Any values provided by the user are overwritten.
     */
    val hash: Int by INT

    /**
     * The origin of the feature.
     *
     * The value is a [Guid] as defined by **Naksha**, and describes from where the feature comes originally. The field is only set, when the feature was forked, for example, when a topology is split, the children will all have the `origin` set to the GUID of the feature that was originally split. If the children are split again, their `origin` will again refer to the feature that was split, effectively creating a tree of changes.
     *
     * This field is populated only by **Naksha**. Any values provided by the user are overwritten.
     */
    val origin: String? by STRING_NULL

    /**
     * The HERE tile-id in which the reference-point of the feature is located at level 15.
     *
     * This field is populated only by **Naksha**. Any values provided by the user are overwritten.
     */
    val geoGrid: Int by INT

    /**
     * Returns 'true' if the tag was removed, 'false' if it was not present.
     *
     * @param tag       The normalized tag to remove.
     * @param normalize `true` if the tag should be normalized before trying to remove; `false` if the tag is normalized.
     * @return true if the tag was removed; false otherwise.
     */
    fun removeTag(tag: String, normalize: Boolean): Boolean {
        val thisTags: TagList = tags ?: return false
        return thisTags.removeTag(tag, normalize)
    }

    /**
     * Removes the given tags.
     *
     * @param tags      The tags to remove.
     * @param normalize `true` if the tags should be normalized before trying to remove; `false` if the tags are normalized.
     * @return this.
     */
    fun removeTags(tags: List<String>?, normalize: Boolean): XyzNs {
        this.tags?.removeTags(tags, normalize)
        return this
    }

    /**
     * Removes tags starting with prefix
     *
     * @param prefix string prefix.
     * @return this.
     */
    fun removeTagsWithPrefix(prefix: String?): XyzNs {
        this.tags?.removeTagsWithPrefix(prefix)
        return this
    }

    /**
     * Removes tags starting with given list of prefixes
     *
     * @param prefixes list of tag prefixes
     * @return this.
     */
    fun removeTagsWithPrefixes(prefixes: List<String?>?): XyzNs {
        this.tags?.removeTagsWithPrefixes(prefixes)
        return this
    }

    /**
     * Set the tags to the given array.
     *
     * @param tags      The tags to set.
     * @param normalize `true` if the given tags should be normalized; `false`, if they are already normalized.
     */
    fun setTags(tags: TagList?, normalize: Boolean): XyzNs {
        if (normalize) {
            if (tags != null ) {
                for ((i, tag) in tags.withIndex()) {
                    if (tag != null)
                        tags[i] = normalizeTag(tag)
                }
            }
        }
        this.tags = tags
        return this
    }

    /**
     * Returns 'true' if the tag added, 'false' if it was already present.
     *
     * @param tag       The tag to add.
     * @param normalize `true` if the tag should be normalized; `false` otherwise.
     * @return true if the tag added; false otherwise.
     */
    fun addTag(tag: String, normalize: Boolean): Boolean {
        val thisTags = this.tags?: TagList().also { this.tags = it }
        return thisTags.addTag(tag, normalize)
    }

    /**
     * Add the given tags.
     *
     * @param tags      The tags to add.
     * @param normalize `true` if the given tags should be normalized; `false`, if they are already normalized.
     * @return this.
     */
    fun addTags(tags: List<String>?, normalize: Boolean): XyzNs {
        val thisTags = this.tags?: TagList().also { this.tags = it }
        thisTags.addTags(tags, normalize)
        return this
    }

    /**
     * Add and normalize all given tags.
     *
     * @param tags The tags to normalize and add.
     * @return this.
     */
    fun addAndNormalizeTags(vararg tags: String): XyzNs {
        val thisTags = this.tags?: TagList().also { this.tags = it }
        thisTags.addAndNormalizeTags(*tags)
        return this
    }
}