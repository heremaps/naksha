@file:Suppress("OPT_IN_USAGE")

package naksha.model

import naksha.base.*
import kotlin.DeprecationLevel.WARNING
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmStatic

/**
 * The XYZ namespace stored in [properties.@ns:com:here:xyz][naksha.model.object.NakshaProperties.XYZ] of the [NakshaFeature][naksha.model.object.NakshaFeature].
 */
@JsExport
class XyzNs : AnyObject() {

    companion object XyzNsCompanion {
        const val UUID = "uuid"
        const val PUUID = "puuid"
        const val CREATED_AT = "createdAt"
        const val UPDATED_AT = "updatedAt"
        const val SPACE = "space"
        const val TAGS = "tags"
        const val TXN = "txn"
        const val CHANGE_COUNT = "changeCount"
        const val ACTION = "action"
        const val APP_ID = "appId"
        const val AUTHOR = "author"
        const val AUTHOR_TS = "authorTs"
        const val HASH = "hash"
        const val ORIGIN = "origin"
        const val GEO_GRID = "geoGrid"

        private val _ACTION = NotNullEnum<XyzNs, Action>(Action::class) { _, _ -> Action.CREATED }
        private val _APP_ID = NotNullProperty<XyzNs, String>(String::class) { _, _ -> NakshaContext.appId() }
        private val _STRING_NULL = NullableProperty<XyzNs, String>(String::class)
        private val _INT_0 = NotNullProperty<XyzNs, Int>(Int::class) { _, _ -> 0 }
        private val _INT_NULL = NullableProperty<XyzNs, Int>(Int::class)
        private val _UPDATED_AT = NotNullProperty<XyzNs, Int64>(Int64::class) { _, _ -> Platform.currentMillis() }
        private val _INT64_NULL = NullableProperty<XyzNs, Int64>(Int64::class)
        private val _TAGS = NotNullProperty<XyzNs, TagList>(TagList::class) { _, _ -> TagList() }
        private var AS_IS: CharArray = CharArray(128 - 32) { (it + 32).toChar() }
        private var TO_LOWER: CharArray = CharArray(128 - 32) { (it + 32).toChar().lowercaseChar() }

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

        /**
         * A method to normalize and lower case a tag.
         *
         * @param tag the tag.
         * @return the normalized and lower cased version of it.
         */
        @JvmStatic
        @JsStatic
        fun normalizeTag(tag: String): String {
            if (tag.isEmpty()) {
                return tag
            }
            val first = tag[0]
            // All tags starting with an at-sign, will not be modified in any way.
            if (first == '@') {
                return tag
            }

            // Normalize the tag.
            val normalized: String = Platform.normalize(tag, NormalizerForm.NFD)

            // All tags starting with a tilde, sharp, or the deprecated "ref_" / "sourceID_" prefix will not
            // be lower cased.
            val MAP: CharArray =
                if (first == '~' || first == '#' || normalized.startsWith("ref_") || normalized.startsWith("sourceID_"))
                    AS_IS
                else
                    TO_LOWER
            val sb = StringBuilder(normalized.length)
            for (element in normalized) {
                // Note: This saves one branch, and the array-size check, because 0 - 32 will become 65504.
                val c = (element.code - 32).toChar()
                if (c.code < MAP.size) {
                    sb.append(MAP[c.code])
                }
            }
            return sb.toString()
        }
    }

    /**
     * The universal unique identifier of the state of a feature.
     *
     * This field is populated by Interactive API, Data Hub, XYZ Hub and Naksha. Any values provided by the user are overwritten.
     * - **Interactive API**: This field is set, when history is enabled for the layer.
     * - **Data Hub**: This field is set, when history or UUID is enabled for the space.
     * - **XYZ Hub**: This field is set when history or UUID is enabled for the space.
     * - **Naksha**: This field is always set, except when creating new features locally, it does not store a real UUID, but a [Guid] (global unique identifier).
     */
    val uuid: String? by _STRING_NULL

    private var _uuid: String? = null
    private var _guid: Guid? = null

    /**
     * Returns the [uuid] as [Guid].
     * @return the [uuid] as [Guid].
     */
    val guid: Guid?
        get() {
            var guid = _guid
            var uuid = _uuid
            if (uuid === this.uuid) return guid
            uuid = this.uuid
            guid = try { if (uuid == null) null else Guid.fromString(uuid) } catch (e: Exception) { null }
            this._uuid = uuid
            this._guid = guid
            return guid
        }

    private var _puuid: String? = null
    private var _pguid: Guid? = null

    /**
     * The universal unique identifier of the previous state of a feature.
     *
     * This field is populated by Interactive API, Data Hub, XYZ Hub and Naksha. Any values provided by the user are overwritten.
     * - **Interactive API**: This field is set, when history is enabled for the layer.
     * - **Data Hub**: This field is set, when history or UUID is enabled for the space.
     * - **XYZ Hub**: This field is set when history or UUID is enabled for the space.
     * - **Naksha**: This field is always set, but does not store a real UUID, but rather a GUID (global unique identifier).
     */
    val puuid: String? by _STRING_NULL

    /**
     * Returns the [puuid] as [Guid].
     * @return the [puuid] as [Guid].
     */
    val pguid: Guid?
        get() {
            var pguid = _pguid
            var puuid = _puuid
            if (puuid === this.puuid) return pguid
            puuid = this.puuid
            pguid = try { if (puuid == null) null else Guid.fromString(puuid) } catch (e: Exception) { null }
            this._puuid = puuid
            this._pguid = pguid
            return pguid
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
    val muuid: String? by _STRING_NULL

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
    val createdAt: Int64
        get() {
            val raw = getRaw("createdAt")
            if (raw is Int64) return raw
            return updatedAt
        }

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
    val updatedAt: Int64 by _UPDATED_AT

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
    val space: String? by _STRING_NULL

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
    var tags: TagList by _TAGS

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
    val txn: Int64? by _INT64_NULL

    /**
     * The change-count, so how often the feature has been changed since it was created. The value starts with 1.
     *
     * This field is populated only by **Naksha**. Any values provided by the user are overwritten.
     *
     * If the value is `0`, this is a new feature not yet stored anywhere.
     */
    val changeCount: Int by _INT_0

    /**
     * The change that was applied to the feature.
     *
     * This field is populated only by **Naksha**. Any values provided by the user are overwritten.
     */
    val action: Action by _ACTION

    /**
     * The identifier of the application that modified the feature the last.
     *
     * This field is populated only by **Naksha**. Any values provided by the user are overwritten.
     */
    val appId: String by _APP_ID

    /**
     * The author of the feature. Not every change of feature is done by intention, the author is only set, when the change of the
     * feature was done by intention and not as a side effect. For example, repair bots will not claim authorship, but cause the [appId]
     * to change.
     *
     * This field is populated only by **Naksha**. Any values provided by the user are overwritten.
     */
    val author: String? by _STRING_NULL

    /**
     * The time when this author of the feature was modified.
     *
     * The value is a valid Unix timestamp which is the number of milliseconds since January 1st, 1970, leap seconds are ignored. This
     * field is populated only by **Naksha**. Any values provided by the user are overwritten.
     */
    val authorTs: Int64? by _INT64_NULL

    /**
     * The hash above the feature, calculated server side.
     *
     * This field is populated only by **Naksha**. Any values provided by the user are overwritten.
     */
    val hash: Int? by _INT_NULL

    /**
     * The origin of the feature.
     *
     * The value is a [Guid] as defined by **Naksha**, and describes from where the feature comes originally. The field is only set, when the feature was forked, for example, when a topology is split, the children will all have the `origin` set to the GUID of the feature that was originally split. If the children are split again, their `origin` will again refer to the feature that was split, effectively creating a tree of changes.
     *
     * This field is populated only by **Naksha**. Any values provided by the user are overwritten.
     */
    val origin: String? by _STRING_NULL

    /**
     * The HERE tile-id in which the reference-point of the feature is located at level 15.
     *
     * This field is populated only by **Naksha**. Any values provided by the user are overwritten.
     */
    val geoGrid: Int? by _INT_NULL

    /**
     * Returns 'true' if the tag was removed, 'false' if it was not present.
     *
     * @param tag       The normalized tag to remove.
     * @param normalize `true` if the tag should be normalized before trying to remove; `false` if the tag is normalized.
     * @return true if the tag was removed; false otherwise.
     */
    @Deprecated(message = "Directly use tags property instead", replaceWith = ReplaceWith("tags.removeTag(tag, normalize)"))
    fun removeTag(tag: String, normalize: Boolean): Boolean {
        return this.tags.removeTag(tag, normalize)
    }

    /**
     * Removes the given tags.
     *
     * @param tags      The tags to remove.
     * @param normalize `true` if the tags should be normalized before trying to remove; `false` if the tags are normalized.
     * @return this.
     */
    fun removeTags(tags: List<String>?, normalize: Boolean): XyzNs {
        this.tags.removeTags(tags, normalize)
        return this
    }

    /**
     * Removes tags starting with prefix
     *
     * @param prefix string prefix.
     * @return this.
     */
    fun removeTagsWithPrefix(prefix: String?): XyzNs {
        this.tags.removeTagsWithPrefix(prefix)
        return this
    }

    /**
     * Removes tags starting with given list of prefixes
     *
     * @param prefixes list of tag prefixes
     * @return this.
     */
    fun removeTagsWithPrefixes(prefixes: List<String?>?): XyzNs {
        this.tags.removeTagsWithPrefixes(prefixes)
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
        this.tags = tags ?: TagList()
        return this
    }

    /**
     * Returns 'true' if the tag added, 'false' if it was already present.
     *
     * @param tag       The tag to add.
     * @param normalize `true` if the tag should be normalized; `false` otherwise.
     * @return true if the tag added; false otherwise.
     */
    @Deprecated(message = "Directly use tags property instead", replaceWith = ReplaceWith("tags.addTag(tag, normalize)"))
    fun addTag(tag: String, normalize: Boolean): Boolean = this.tags.addTag(tag, normalize)

    /**
     * Add the given tags.
     *
     * @param tags      The tags to add.
     * @param normalize `true` if the given tags should be normalized; `false`, if they are already normalized.
     * @return this.
     */
    fun addTags(tags: List<String>?, normalize: Boolean): XyzNs {
        this.tags.addTags(tags, normalize)
        return this
    }

    /**
     * Add and normalize all given tags.
     *
     * @param tags The tags to normalize and add.
     * @return this.
     */
    fun addAndNormalizeTags(vararg tags: String): XyzNs {
        this.tags.addAndNormalizeTags(*tags)
        return this
    }
}