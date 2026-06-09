@file:Suppress("OPT_IN_USAGE")

package naksha.model

import naksha.base.*
import naksha.model.objects.StandardMembers
import kotlin.DeprecationLevel.WARNING
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmStatic

/**
 * The XYZ namespace stored in [properties.@ns:com:here:xyz][naksha.model.object.NakshaProperties.XYZ] of the [NakshaFeature][naksha.model.objects.NakshaFeature].
 *
 * This represents the external Naksha view of tuple metadata. When a [Tuple] is returned by a storage, and then converted for example by Naksha-Hub into a [NakshaFeature][naksha.model.objects.NakshaFeature], the [uuid] is set to the stringified [Guid] of the [Tuple], so to the [tuple-number][TupleNumber] combined with the [feature id][naksha.model.objects.StandardMembers.Id].
 *
 * If a client wants to change a feature, the following concepts should be followed:
 *
 * - **Create**: Clients should create features without an XYZ namespace, except for the [tags].
 * - **Delete**: The content of the feature is ignored for deletes.
 * - **Update**: If the client wants to update a feature, it should read the feature, then modify it, and then send the modified feature back, without changing the XYZ namespace, except for the [tags]. When it does operate like this, the change is performed atomically safe, because the [uuid] will hint the server which version was modified by the client, and is expected to be current _HEAD_. If the feature was updated meanwhile by another client, the server can try to perform an auto-merge, otherwise it will respond with a conflict (which is what the low-level storage will do).
 * - **Fork**: If the client reads a feature, and then writes it into another storage, map, or collection, or when the client modifies the ID of the feature, and then sends the feature to a service, without modifying the XYZ namespace, the storage will be able to detect that this is a **fork**. Forking means, that a feature is moved between storages, maps, or collections, or is re-identified. The storage will turn the action into [CREATED][Action.CREATED], and copy the [uuid] (which refers to the modified foreign state) into the [origin]. When the feature, that was forked, is modified later, it is possible to find all forks in all storages, maps, and collections, and to update them doing a [three-way-merge](https://en.wikipedia.org/wiki/Merge_(version_control)#Three-way_merge). This process is called rebase.
 * - **Split**: If the client need to split a feature into parts, for example a Topology into two, it is required that it clones the original feature, and then modifies the copies, while deleting the original feature that was split. All features being part of the split will have the same [uuid]. The feature that was split is expected to be deleted with [action] set to [DELETED][Action.DELETED], and the new parts are created with [action] set to [CREATED][Action.CREATED].
 * - **Join**: If the client need to join multiple features into a single one, it is required to create a new (_merged_) feature, and to delete all features joined into this new one. It is important that the client set the [target] of all features being part of the join to the [_HEAD_ Guid][Guid.headOf] of the _created_ (_new_) feature. The [_HEAD_ Guid][Guid.headOf] is simply the [Guid] without the [tuple-number][TupleNumber], so basically `urn:here:naksha:guid:{feature-id}`.
 * @since 3.0
 */
@JsExport
class XyzNs : AnyObject() {

    companion object XyzNsCompanion {
        const val TAGS_KEY = "tags"
        /**
         * The key of the [uuid] property.
         * @since 3.0
         */
        const val UUID = "uuid"

        /**
         * The key of the [muuid] property.
         * @since 3.0
         */
        const val MUUID = "muuid"

        /**
         * The key of the [nuuid] property.
         * @since 3.0
         */
        const val NUUID = "nuuid"

        /**
         * The key of the [createdAt] property.
         * @since 3.0
         */
        const val CREATED_AT = "createdAt"
        /**
         * The key of the [updatedAt] property.
         * @since 3.0
         */
        const val UPDATED_AT = "updatedAt"

        @Deprecated(message = "The space property is no longer supported", level = WARNING)
        const val SPACE = "space"

        /**
         * The key of the [tags] property.
         * @since 3.0
         */
        const val TAGS = "tags"

         /**
         * The key of the [changeCount] property.
         * @since 3.0
         */
        const val CHANGE_COUNT = "changeCount"

        /**
         * The key of the [action] property.
         * @since 3.0
         */
        const val ACTION = "action"

        /**
         * The key of the [appId] property.
         * @since 3.0
         */
        const val APP_ID = "appId"

        /**
         * The key of the [author] property.
         * @since 3.0
         */
        const val AUTHOR = "author"

        /**
         * The key of the [authorTs] property.
         * @since 3.0
         */
        const val AUTHOR_TS = "authorTs"

        /**
         * The key of the [dataEncoding] property.
         * @since 3.0
         */
        const val DATA_ENCODING = "dataEncoding"

        /**
         * The key of the [hash] property.
         * @since 3.0
         */
        const val HASH = "hash"

        /**
         * The key of the [origin] property.
         * @since 3.0
         */
        const val ORIGIN = "origin"

        /**
         * The key of the [target] property.
         * @since 3.0
         */
        const val TARGET = "target"

        /**
         * The key of the [hereTile] property.
         * @since 3.0
         */
        const val HERE_TILE = "hereTile"

        /**
         * The key of the [cv0] property.
         * @since 3.0
         */
        const val CV0 = "cv0"

        /**
         * The key of the [cv1] property.
         * @since 3.0
         */
        const val CV1 = "cv1"

        /**
         * The key of the [cv2] property.
         * @since 3.0
         */
        const val CV2 = "cv2"

        /**
         * The key of the [cv3] property.
         * @since 3.0
         */
        const val CV3 = "cv3"

        /**
         * The key of the [cs0] property.
         * @since 3.0
         */
        const val CS0 = "cs0"

        /**
         * The key of the [cs1] property.
         * @since 3.0
         */
        const val CS1 = "cs1"

        /**
         * The key of the [cs2] property.
         * @since 3.0
         */
        const val CS2 = "cs2"

        /**
         * The key of the [cs3] property.
         * @since 3.0
         */
        const val CS3 = "cs3"

        private val _ACTION = NotNullEnum<XyzNs, Action>(Action::class) { _, _ -> Action.CREATED }
        private val _DATA_ENCODING_NULL = NullableEnum<XyzNs, DataEncoding>(DataEncoding::class)
        private val _APP_ID = NotNullProperty<XyzNs, String>(String::class) { _, _ -> NakshaContext.appId() }
        private val _STRING_NULL = NullableProperty<XyzNs, String>(String::class, autoRemove = true)
        private val _INT_0 = NotNullProperty<XyzNs, Int>(Int::class) { _, _ -> 0 }
        private val _INT_NULL = NullableProperty<XyzNs, Int>(Int::class, autoRemove = true)
        private val _UPDATED_AT = NotNullProperty<XyzNs, Int64>(Int64::class) { _, _ -> Platform.currentMillis() }
        private val _DOUBLE_NULL = NullableProperty<XyzNs, Double>(Double::class, autoRemove = true)
        private val _TAGS = NotNullProperty<XyzNs, TagList>(TagList::class) { _, _ -> TagList() }
        private var AS_IS: CharArray = CharArray(128 - 32) { (it + 32).toChar() }
        private var TO_LOWER: CharArray = CharArray(128 - 32) { (it + 32).toChar().lowercaseChar() }

        /**
         * Create the XYZ-namespace from the given [Tuple].
         * @param tuple the [Tuple]
         * @return the [XYZ namespace][XyzNs].
         */
        @JvmStatic
        @JsStatic
        fun fromTuple(tuple: Tuple): XyzNs {
            val tn = tuple.tupleNumber
            val members = tuple.members
            val id = members?.getByName("id") as? String ?: tuple.featureNumber.toString()
            val guid = Guid(id, tn)
            val updatedAt = tuple.getLongMember(StandardMembers.UpdatedAt)
            val createdAt = tuple.getLongMember(StandardMembers.CreatedAt).let {
                if (it == Int64(0L)) updatedAt else it
            }
            val authorTs = tuple.getLongMember(StandardMembers.AuthorTimestamp)?.let {
                if (it == Int64(0)) updatedAt else it
            } ?: updatedAt
            val nextVersion = tuple.nextVersion
            val nextTn = if (nextVersion != Int64(-1L)) TupleNumber(
                tn.storageNumber, tn.mapNumber, tn.collectionNumber, tn.featureNumber, Version(nextVersion)
            ) else null
            val base_tn = members?.getByName("base_tn")?.let {
                if (it is ByteArray) TupleNumber.fromByteArray(it, 0, TupleNumberVariant.TupleNumberVariant_C.B128,
                    tn.storageNumber, tn.mapNumber, tn.collectionNumber)
                else null
            }
            return AnyObject().apply {
                setRaw(UUID, guid.toString())
                if (nextTn != null) setRaw(NUUID, Guid(id, nextTn).toString())
                if (base_tn != null) setRaw(MUUID, Guid(id, base_tn).toString())
                if (createdAt != updatedAt) setRaw(CREATED_AT, createdAt)
                if (authorTs != updatedAt) setRaw(AUTHOR_TS, authorTs)
                setRaw(UPDATED_AT, updatedAt)
                setRaw(CHANGE_COUNT, tuple.getIntMember(StandardMembers.ChangeCount))
                setRaw(APP_ID, tuple.getStringMember(StandardMembers.AppId))
                val author = tuple.getStringMember(StandardMembers.Author)
                if (author != null) setRaw(AUTHOR, author)
                setRaw(DATA_ENCODING, tuple.getStringMember(StandardMembers.DataEncoding))
                setRaw(ACTION, tn.action.toString())
                setRaw(HASH, tuple.getIntMember(StandardMembers.Hash))
                setRaw(HERE_TILE, tuple.getIntMember(StandardMembers.HereTile))
                val origin = tuple.getStringMember(StandardMembers.Origin)
                if (origin != null) setRaw(ORIGIN, origin)
                val target = tuple.getStringMember(StandardMembers.Target)
                if (target != null) setRaw(TARGET, target)
                val cv0 = members?.getByName("cv0")
                if (cv0 != null) setRaw(CV0, cv0 as? Double)
                val cv1 = members?.getByName("cv1")
                if (cv1 != null) setRaw(CV1, cv1 as? Double)
                val cv2 = members?.getByName("cv2")
                if (cv2 != null) setRaw(CV2, cv2 as? Double)
                val cv3 = members?.getByName("cv3")
                if (cv3 != null) setRaw(CV3, cv3 as? Double)
                val cs0 = tuple.getStringMember(StandardMembers.CustomString0)
                if (cs0 != null) setRaw(CS0, cs0)
                val cs1 = tuple.getStringMember(StandardMembers.CustomString1)
                if (cs1 != null) setRaw(CS1, cs1)
                val cs2 = tuple.getStringMember(StandardMembers.CustomString2)
                if (cs2 != null) setRaw(CS2, cs2)
                val cs3 = tuple.getStringMember(StandardMembers.CustomString3)
                if (cs3 != null) setRaw(CS3, cs3)
            }.proxy(XyzNs::class)
        }

        /**
         * Create the XYZ-namespace from the given [Metadata].
         * @param meta the [Metadata]
         * @return the [XYZ namespace][XyzNs].
         * @see [Metadata.fromXyzNs]
         * @deprecated Use [fromTuple] instead.
         */
        @Deprecated("Use fromTuple instead", ReplaceWith("fromTuple(tuple)"))
        @JvmStatic
        @JsStatic
        fun fromMetadata(meta: Metadata): XyzNs {
            val tn = meta.tupleNumber
            val guid = Guid(meta.id, tn)
            val nextVersion = meta.nextVersion
            val nextTn = if (nextVersion != null) TupleNumber(
                tn.storageNumber, tn.mapNumber, tn.collectionNumber, tn.featureNumber, Version(nextVersion)
            ) else null
            val base_tn = meta.baseTupleNumber
            return AnyObject().apply {
                setRaw(UUID, guid.toString())
                if (nextTn != null) setRaw(NUUID, Guid(meta.id, nextTn).toString())
                if (base_tn != null) setRaw(MUUID, Guid(meta.id, base_tn).toString())
                if (meta.createdAt != meta.updatedAt) setRaw(CREATED_AT, meta.createdAt)
                if (meta.authorTs != meta.updatedAt) setRaw(AUTHOR_TS, meta.authorTs)
                setRaw(UPDATED_AT, meta.updatedAt)
                setRaw(CHANGE_COUNT, meta.changeCount)
                setRaw(APP_ID, meta.appId)
                if (meta.author != null) setRaw(AUTHOR, meta.author)
                setRaw(DATA_ENCODING, meta.dataEncoding.toString())
                setRaw(ACTION, meta.action().toString())
                setRaw(HASH, meta.hash)
                setRaw(HERE_TILE, meta.hereTile)
                if (meta.origin != null) setRaw(ORIGIN, meta.origin)
                if (meta.target != null) setRaw(TARGET, meta.target)
                if (meta.cv0 != null) setRaw(CV0, meta.cv0)
                if (meta.cv1 != null) setRaw(CV1, meta.cv1)
                if (meta.cv2 != null) setRaw(CV2, meta.cv2)
                if (meta.cv3 != null) setRaw(CV3, meta.cv3)
                if (meta.cs0 != null) setRaw(CS0, meta.cs0)
                if (meta.cs1 != null) setRaw(CS1, meta.cs1)
                if (meta.cs2 != null) setRaw(CS2, meta.cs2)
                if (meta.cs3 != null) setRaw(CS3, meta.cs3)
            }.proxy(XyzNs::class)
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
                        tags[idx] = TagNormalizer.normalizeTag(tag)
                    }
                }
            }
            return tags
        }

    }

    /**
     * The universal unique identifier of the state of a feature.
     *
     * This field is populated by Interactive API, Data Hub, XYZ Hub and Naksha.
     * - **Interactive API**: This field is set, when history is enabled for the layer.
     * - **Data Hub**: This field is set, when history or UUID is enabled for the space.
     * - **XYZ Hub**: This field is set when history or UUID is enabled for the space.
     * - **Naksha**: This field is always set, it does not store a real [UUID](https://en.wikipedia.org/wiki/Universally_unique_identifier), but a [Guid] (global unique identifier).
     *
     * This field is populated only by **Naksha**. Any values provided by the user will be overwritten.
     * @since 1.0
     */
    val uuid by _STRING_NULL
    private var _uuid: String? = null
    private var _guid: Guid? = null

    /**
     * Returns the [uuid] parsed as [Guid].
     * @return the [uuid] parsed as [Guid].
     * @since 3.0
     */
    val guid: Guid?
        get() {
            var guid = _guid
            var uuid = _uuid
            if (uuid === this.uuid) return guid
            uuid = this.uuid
            guid = try { if (uuid == null) null else Guid.fromString(uuid) } catch (_: Exception) { null }
            this._uuid = uuid
            this._guid = guid
            return guid
        }

    /**
     * The universal unique identifier of the state of the feature that was used to merge with the previous state to produce this state.
     *
     * This happens when concurrent modifications are done, but an automatic merge was possible. This field is populated by Interactive API, Data Hub or XYZ Hub.
     * - **Interactive API**: This field is set when history is enabled for the layer.
     * - **Data Hub**: This field is set when history or UUID is enabled for the space.
     * - **XYZ Hub**: This field is set when history or UUID is enabled for the space.
     * - **Naksha**: Set when an auto-merge is done, stores the [Guid] of the base-version that the previous version and this version share as base.
     *
     * In **Naksha** the [muuid] can be used to calculate the changes the client originally did, which are not persisted anywhere in the case of an auto-merge. This is done by first creating a total difference, so what was changed between the current version ([uuid]), and the _base_ version ([muuid]). Then the changes that other clients did can be calculated as difference between the previous state and the _base_ state ([muuid]). Now this difference need to be subtracted from the total difference. The resulting difference is what the client originally modified, when being applied as patch to the _base_ state ([muuid]), then the feature, that originally was created by the client, can be calculated, even while it was not persisted anywhere.
     *
     * This field is populated only by **Naksha**. Any values provided by the user will be overwritten.
     * @since 1.0
     */
    val muuid by _STRING_NULL
    private var _muuid: String? = null
    private var _mguid: Guid? = null

    /**
     * Returns the [muuid] as [Guid].
     * @return the [muuid] as [Guid].
     * @since 3.0
     */
    val mguid: Guid?
        get() {
            var mguid = _mguid
            var muuid = _muuid
            if (muuid === this.muuid) return mguid
            muuid = this.muuid
            mguid = try { if (muuid == null) null else Guid.fromString(muuid) } catch (e: Exception) { null }
            this._muuid = muuid
            this._mguid = mguid
            return mguid
        }

    /**
     * The [Guid] of the next state, if known.
     *
     * - If this value is available, it is **guaranteed** that the current feature state is historic.
     * - If the value is not available (`null`), there is no guarantee if this is still the latest _HEAD_; it is only likely.
     * @since 3.0
     */
    val nuuid by _STRING_NULL
    private var _nuuid: String? = null
    private var _nguid: Guid? = null

    /**
     * Returns the [nuuid] as [Guid].
     * @return the [nuuid] as [Guid].
     * @since 3.0
     */
    val nguid: Guid?
        get() {
            var nguid = _nguid
            var nuuid = _nuuid
            if (nuuid === this.nuuid) return nguid
            nuuid = this.nuuid
            nguid = try { if (nuuid == null) null else Guid.fromString(nuuid) } catch (e: Exception) { null }
            this._nuuid = nuuid
            this._nguid = nguid
            return nguid
        }

    /**
     * The origin of the feature.
     *
     * The value is a [Guid] as defined by **Naksha**, and describes from where the feature originates.
     *
     * The field is automatically set, if the [uuid] refers to a different storage, map, collection, or the `id` of the feature changes. This happens in simple cases, for example when the feature was forked, and inserted using a new feature-id, or when a topology is split, the new children will all have the `origin` set to the [Guid] of the feature that was originally split. If the children are split again, their `origin` will again refer to the feature that was split, effectively creating a tree of changes.
     *
     * This field is populated only by **Naksha**. Any values provided by the user will be overwritten.
     * @since 1.0
     */
    val origin by _STRING_NULL
    private var _origin: String? = null
    private var _originGuid: Guid? = null

    /**
     * Returns the [origin] as [Guid].
     * @return the [origin] as [Guid].
     * @since 3.0
     */
    val originGuid: Guid?
        get() {
            var oguid = _originGuid
            var origin = _origin
            if (origin === this.origin) return oguid
            origin = this.origin
            oguid = try { if (origin == null) null else Guid.fromString(origin) } catch (e: Exception) { null }
            this._origin = origin
            this._originGuid = oguid
            return oguid
        }

    /**
     * The target of a join operation.
     *
     * The value is a [Guid] as defined by **Naksha**, and refers to the outcome of a join.
     *
     * This field **must** be set by clients, when joining features into a new one, all features involved into the join require the [target] to be set to the [Guid] of the new feature, **including** the new feature itself! As the client may not know the real [Guid] of the new feature, it is okay, when it just inserts the _HEAD_ [Guid], so `urn:here:naksha:guid:{feature-id}`.
     * @since 3.0
     */
    val target by _STRING_NULL
    private var _target: String? = null
    private var _targetGuid: Guid? = null

    /**
     * Returns the [target] as [Guid].
     * @return the [target] as [Guid].
     * @since 3.0
     */
    val targetGuid: Guid?
        get() {
            var tguid = _targetGuid
            var target = _target
            if (target === this.target) return tguid
            target = this.target
            tguid = try { if (target == null) null else Guid.fromString(target) } catch (e: Exception) { null }
            this._target = target
            this._targetGuid = tguid
            return tguid
        }

    /**
     * The time when this feature was created.
     *
     * The value is a valid Unix timestamp which is the number of milliseconds since January 1st, 1970, leap seconds are ignored. This
     * field is populated by Interactive API, Data Hub, XYZ Hub and Naksha.
     * - **Interactive API**: Always sets this field.
     * - **Data Hub**: Always sets this field.
     * - **XYZ Hub**: Always sets this field.
     * - **Naksha**: Always sets this field.
     *
     * This field is populated only by **Naksha**. Any values provided by the user will be overwritten.
     * @since 1.0
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
     * field is populated by Interactive API, Data Hub, XYZ Hub and Naksha.
     * - **Interactive API**: Always sets this field.
     * - **Data Hub**: Always sets this field.
     * - **XYZ Hub**: Always sets this field.
     * - **Naksha**: Always sets this field.
     *
     * This field is populated only by **Naksha**. Any values provided by the user will be overwritten.
     * @since 1.0
     */
    val updatedAt by _UPDATED_AT

    /**
     * The space in which this feature is located.
     *
     * This field is populated by Interactive API, Data Hub and XYZ Hub. It always represents the current space where the feature resides
     * and is automatically set when persisting a feature.
     * - **Interactive API**: Always sets this field.
     * - **Data Hub**: Always sets this field.
     * - **XYZ Hub**: Always sets this field.
     * - **Naksha**: Does not support this field, it will always be _null_.
     *
     * This field is populated only by **Naksha**. Any values provided by the user will be overwritten.
     * @since 1.0
     */
    @Deprecated("This field is not supported by Naksha, but part of MOM specification", level = WARNING)
    val space by _STRING_NULL

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
     * @since 1.0
     */
    var tags by _TAGS

    /**
     * The version of the feature.
     *
     * Multiple features could be part of a single version if they have been edited in one transaction. This field is populated by
     * Interactive API, Data Hub, XYZ Hub and Naksha. Any values provided by the user are overwritten.
     * - **Interactive API**: This field is set when history is enabled for the layer.
     * - **Data Hub**: This field is set when history or UUID is enabled for the space.
     * - **XYZ Hub**: This field is set when history or UUID is enabled for the space.
     * - **Naksha**: This field stores the transaction-number (`txn`), and is a virtual property read from [uuid].
     *
     * **Note**: Currently MOM defines `version` as 32-bit integer, which is wrong, and not sufficient for Naksha, therefore this property is not set currently.
     * @since 1.0
     */
    val version: Version?
        get() {
            // Downward compatibility hack.
            val raw = getRaw("version")
            if (raw is Int64 && raw >= Version.MIN) return Version(raw)
            return guid?.tupleNumber?.version
        }

    /**
     * The transaction-number of the feature, basically the same as [version], just as 64-bit integer.
     * @since 2.0
     */
    val txn: Int64?
        get() = guid?.tupleNumber?.txn

    /**
     * The action of the [Tuple], encoded as the lower 2 bits of the transaction number.
     * @since 3.0
     */
    val uid: Int?
        get() = guid?.tupleNumber?.action?.intValue

    /**
     * The change-count, so how often the feature has been changed since it was created. The value starts with 1.
     *
     * This field is populated only by **Naksha**. Any values provided by the user will be overwritten.
     *
     * If the value is `0`, this is a new feature not yet stored anywhere.
     * @since 3.0
     */
    val changeCount by _INT_0

    /**
     * The action that was done.
     *
     * This field is populated only by **Naksha**. Any values provided by the user will be overwritten.
     * @since 1.0
     * @see [Action]
     */
    val action by _ACTION

    /**
     * The identifier of the application that modified the feature the last.
     *
     * This field is populated only by **Naksha**. Any values provided by the user will be overwritten.
     */
    val appId by _APP_ID

    /**
     * The author of the feature. Not every change of feature is done by intention, the author is only set, when the change of the
     * feature was done by intention and not as a side effect. For example, repair bots will not claim authorship, but cause the [appId]
     * to change.
     *
     * This field is populated only by **Naksha**. Any values provided by the user will be overwritten.
     * @since 3.0
     */
    val author by _STRING_NULL

    /**
     * The time when this author of the feature was modified.
     *
     * The value is a valid Unix timestamp which is the number of milliseconds since January 1st, 1970, leap seconds are ignored.
     *
     * This field is populated only by **Naksha**. Any values provided by the user will be overwritten.
     * @since 3.0
     */
    val authorTs: Int64
        get() {
            val raw = getRaw("authorTs")
            if (raw is Int64) return raw
            return updatedAt
        }

    /**
     * The serialization format of the feature payload, calculated server-side from the collection's [naksha.model.objects.NakshaCollection.dataEncoding].
     *
     * This field is populated only by **Naksha**. Any values provided by the user will be overwritten.
     * @since 3.0
     */
    val dataEncoding by _DATA_ENCODING_NULL

    /**
     * The hash above the feature, calculated server side.
     *
     * This field is populated only by **Naksha**. Any values provided by the user will be overwritten.
     * @since 3.0
     */
    val hash by _INT_NULL

    /**
     * The binary [HERE tile][naksha.geo.HereTile] in which the reference-point of the feature is located at level 15.
     *
     * This field is populated only by **Naksha**. Any values provided by the user will be overwritten.
     * @since 3.0
     */
    val hereTile by _INT_NULL

    /**
     * A custom feature-type.
     *
     * This field is populated only by **Naksha**. Any values provided by the user will be overwritten.
     * @since 3.0
     */
    val featureType by _STRING_NULL

    /**
     * A customer value that is indexed, and which can be searched, _null_ or _undefined_ if not used.
     * @since 3.0
     */
    val cv0 by _DOUBLE_NULL

    /**
     * A customer value that is indexed, and which can be searched, _null_ or _undefined_ if not used.
     * @since 3.0
     */
    val cv1 by _DOUBLE_NULL

    /**
     * A customer value that is indexed, and which can be searched, _null_ or _undefined_ if not used.
     * @since 3.0
     */
    val cv2 by _DOUBLE_NULL

    /**
     * A customer value that is indexed, and which can be searched, _null_ or _undefined_ if not used.
     * @since 3.0
     */
    val cv3 by _DOUBLE_NULL

    /**
     * A customer string that is indexed, and which can be searched, _null_ or _undefined_ if not used.
     * @since 3.0
     */
    val cs0 by _STRING_NULL

    /**
     * A customer string that is indexed, and which can be searched, _null_ or _undefined_ if not used.
     * @since 3.0
     */
    val cs1 by _STRING_NULL

    /**
     * A customer string that is indexed, and which can be searched, _null_ or _undefined_ if not used.
     * @since 3.0
     */
    val cs2 by _STRING_NULL

    /**
     * A customer string that is indexed, and which can be searched, _null_ or _undefined_ if not used.
     * @since 3.0
     */
    val cs3 by _STRING_NULL

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
                        tags[i] = TagNormalizer.normalizeTag(tag)
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