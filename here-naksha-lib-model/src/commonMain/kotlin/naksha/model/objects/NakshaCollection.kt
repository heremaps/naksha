@file:Suppress("OPT_IN_USAGE", "LeakingThis")

package naksha.model.objects

import naksha.base.*
import naksha.geo.SpBoundingBox
import naksha.geo.SpGeometry
import naksha.geo.SpPoint
import naksha.model.Naksha
import naksha.base.NakshaError.NakshaErrorCompanion.ILLEGAL_ARGUMENT
import naksha.base.NakshaError.NakshaErrorCompanion.ILLEGAL_STATE
import naksha.base.NakshaException
import naksha.model.NakshaIdType
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.js.JsStatic
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic

/**
 * The logical representation of a collection in Naksha.
 *
 * A collection stores features and describes which parts of the features should be separated as [members][Member]. The [members][Member] can be indexed using [Index], and they can be queried using the [queryMembers][naksha.model.request.ReadFeatures.queryMembers] operation of the [ReadFeatures][naksha.model.request.ReadFeatures] request.
 * @since 3.0
 */
@JsExport
open class NakshaCollection() : NakshaFeature() {

    /**
     * Create a Naksha collection with settings.
     * @param id the collection-identifier.
     * @param catalogId the catalog-identifier of the map in which the collection should be created
     * @param partitions the partitions to create; defaults to `1`
     * @param storageClass the [storage-class][storageClass] to create; defaults to `null`
     * @param storeDeleted if [deleted states should be stored][storeDeleted], defaults to [StoreMode.ON]
     * @param storeHistory if [historic states should be stored][storeHistory], defaults to [StoreMode.ON]
     * @param storeMeta if [statistics should be stored][storeMeta], defaults to [StoreMode.ON]
     */
    @JsName("of")
    @JvmOverloads
    constructor(
        id: String,
        catalogId: String? = null,
        partitions: Int = 1,
        storageClass: String? = null,
        storeDeleted: StoreMode = StoreMode.ON,
        storeHistory: StoreMode = StoreMode.ON,
        storeMeta: StoreMode = StoreMode.ON,
    ) : this() {
        this.id = id
        this.catalogId = catalogId
        this.storageClass = storageClass
        this.partitions = partitions
        this.storeDeleted = storeDeleted
        this.storeHistory = storeHistory
        this.storeMeta = storeMeta
    }

    override fun featureTypeDefaultValue(): String = FEATURE_TYPE
    override fun withId(value: String): NakshaCollection = super.withId(value) as NakshaCollection
    override fun withType(value: String): NakshaCollection = super.withType(value) as NakshaCollection
    override fun withFeatureType(value: String): NakshaCollection = super.withFeatureType(value) as NakshaCollection
    override fun withBbox(value: SpBoundingBox?): NakshaCollection = super.withBbox(value) as NakshaCollection
    override fun withGeometry(value: SpGeometry?): NakshaCollection = super.withGeometry(value) as NakshaCollection
    override fun withReferencePoint(value: SpPoint?): NakshaCollection = super.withReferencePoint(value) as NakshaCollection
    override fun withProperties(value: NakshaProperties): NakshaCollection = super.withProperties(value) as NakshaCollection
    override fun withMomType(value: String?): NakshaCollection = super.withMomType(value) as NakshaCollection

    /**
     * The database-number of the collection; the collection-feature itself is stored in the same database as the collection it describes.
     * @since 3.0
     * @throws NakshaException with error [ILLEGAL_STATE], when the collection does not have a valid [tupleNumber].
     */
    val databaseNumber: Long
        get() = tupleNumber?.databaseNumber ?: throw NakshaException(ILLEGAL_STATE, "The collection has no tuple-number")

    /**
     * The database-id of the collection; the collection-feature itself is stored in the same database as the collection it describes.
     * @since 3.0
     */
    var databaseId: String? by DATABASE_ID

    /**
     * @see [databaseId]
     */
    fun withDatabaseId(value: String): NakshaCollection {
        databaseId = value
        return this
    }

    /**
     * The catalog-number of the collection; the collection-feature itself is stored in the same catalog as the collection it describes.
     * @since 3.0
     * @throws NakshaException with error [ILLEGAL_STATE], when the collection does not have a valid [tupleNumber].
     */
    val catalogNumber: Int
        get() {
            val number = tupleNumber?.catalogNumber
            if (number != null) return number
            val catalogId = this.catalogId
            if (catalogId != null) return Id.numberToInt(Id.textToNumber(catalogId))
            throw illegalState("The collection has no tuple-number")
        }

    /**
     * The custom identifier of the catalog in which the collection is located; the collection-feature itself is stored in the same catalog as the collection it describes.
     * @since 3.0
     */
    var catalogId: String? by CATALOG_ID

    /**
     * @see [catalogId]
     */
    fun withCatalogId(value: String): NakshaCollection {
        catalogId = value
        return this
    }

    /**
     * The collection-number of the collection represented by this feature.
     *
     * This is actually derived from the `id` using [Id.textToNumber] and then [Id.numberToInt]:
     *
     * ```kotlin
     * Id.numberToInt(Id.textToNumber(id))
     * ```
     *
     * #### Warning
     * It is **NOT** the collection-number of where this collection-feature is stored, which has always `0`, because all collection features are always stored in the collection `naksha~collections` which has by definition the feature-number `0`.
     * @since 3.0
     * @see [Id.intValue]
     */
    val collectionNumber: Int
        get() = Id.numberToInt(Id.textToNumber(id))

    /**
     * If partitions is given, then the collection is internally partitioned in the storage, optimised for large quantities of features. The default is no partitions; as a rule of thumb, add one more partition for every 10 to 20 million features expected.
     *
     * Valid values are between `1` and `256` _(inclusive)_, the values `undefined`, `null` and `0` are interpreted as one partition (`1`), all other values will be rejected.
     *
     * **{Create-Only}** - after collection creation, modification of this parameter takes no effect.
     * @since 3.0
     */
    var partitions: Int by PARTITIONS

    /**
     * @see [partitions]
     */
    open fun withPartitions(value: Int): NakshaCollection {
        require(value in 0..256) { "partitions must be in 0..256, got $value" }
        this.partitions = value
        return this
    }

    /** The amount of completed history partition ranges retained by pg_partman. */
    var retain: Int by RETAIN

    fun withRetain(value: Int): NakshaCollection {
        require(value > 1) { "retain must be greater than 1, got $value" }
        require(-value < backward && forward < value) { "retain must exceed backward magnitude and forward" }
        retain = value
        return this
    }

    /** The negative number of history ranges created before the current range. */
    var backward: Int by BACKWARD

    fun withBackward(value: Int): NakshaCollection {
        require(value < 0 && -retain < value) { "backward must be in ${-retain + 1}..-1, got $value" }
        backward = value
        return this
    }

    /** The number of future history ranges pg_partman keeps prepared. */
    var forward: Int by FORWARD

    fun withForward(value: Int): NakshaCollection {
        require(value >= 0 && value < retain) { "forward must be in 0..${retain - 1}, got $value" }
        forward = value
        return this
    }

    /**
     * The bit-shift applied to a transaction number to derive the history partition key.
     *
     * History is partitioned by range over `(txn >> shift)`. Each partition covers one contiguous range of the shifted value, defaults to `37`.
     *
     * The values with a special meaning for dated versions are:
     * - `41`: Partitions history by year.
     * - `37`: Partitions history by month _(default)_.
     * - `32`: Partitions history by day.
     *
     * Values being smaller than 32 will generate less than daily history partitions and are rarely useful. It is strongly recommended to leave this setting to the default value of `37`.
     *
     * Valid values: `12..41` _(inclusive)_. Any other value is rejected.
     *
     * **{Create-Only}** — changing this after collection creation has no effect.
     * @since 3.0
     */
    var shift: Int by SHIFT

    /**
     * @see [shift]
     */
    open fun withShift(value: Int): NakshaCollection {
        require(value in 12..41) { "shift must be in 12..41, got $value" }
        this.shift = value
        return this
    }

    /**
     * Tests if this collection has multiple partitions.
     * @return _true_ if this collection has multiple partitions.
     * @see hasPartitions
     */
    fun hasPartitions(): Boolean = partitions > 1

    /**
     * The storageClass decides where the collection is created.
     *
     * The possible values are implementation specific, but the general ones are:
     * - `consistent`: The default storage type, which should be perfectly safe; used as well when `null` is given.
     * - `ephemeral`: Force all the tables of the collection to be created on ephemeral storage, optionally distributed across multiple local disks. This drastically improves the read and write performance, but no backups are done, no read-replicas are available. The data normally survives a server crash, unless the physical instance on which the data is stored is lost.
     * - `brittle`: Force all the tables of the collection to be created on ephemeral storage, and to be unlogged, optionally distributed across multiple local SSDs. Any server crash can corrupt the data. This is the fastest way to store data, but the least reliable.
     * - `temporary`: The collection is created in temporary space _(when possible, on ephemeral storage)_, it is unlogged, and automatically deleted when the session is closed. The weakest form to store data.
     *
     * **{Create-Only}** - after collection creation, modification of this parameter takes no effect.
     */
    var storageClass: String? by STORAGE_CLASS

    /**
     * @see [storageClass]
     */
    open fun withStorageClass(value: String?): NakshaCollection {
        this.storageClass = value
        return this
    }

    /**
     * The protectionClass defines how collections should be protected against uncoordinated external modification.
     *
     * The possible values are implementation-specific. A storage may use this hint to install
     * protective guards (e.g. triggers, constraints, or access controls) that prevent direct
     * modifications that would bypass the Naksha write path and could corrupt history or
     * transaction logs. If _null_, the storage will use whatever protection level is best for
     * the storage.
     * @since 3.0
     */
    var protectionClass by PROTECTION_CLASS

    /**
     * @see [protectionClass]
     */
    open fun withProtectionClass(value: String): NakshaCollection {
        this.protectionClass = value
        return this
    }

    /**
     * If [StoreMode.OFF] the storage will not retain historic states of features in this collection, which boosts performance in certain operations.
     */
    var storeHistory by STORE_HISTORY

    /**
     * @see [storeHistory]
     */
    open fun withStoreHistory(value: StoreMode): NakshaCollection {
        this.storeHistory = value
        return this
    }

    /**
     * If [StoreMode.OFF] the storage will not retain deleted states of features from this collection, which boosts performance in certain operations, but impacts views as provided by `lib-view`.
     */
    //TODO cleanup
    var storeDeleted by STORE_DELETED

    /**
     * @see [storeDeleted]
     */
    open fun withStoreDeleted(value: StoreMode): NakshaCollection {
        this.storeDeleted = value
        return this
    }

    /**
     * If [StoreMode.OFF] the storage will not maintain statistical metadata for features in this collection. This can reduce storage cost, but may prevent certain use cases like optimal tile distribution queries.
     */
    var storeMeta by STORE_META

    /**
     * @see [storeMeta]
     */
    open fun withStoreMeta(value: StoreMode): NakshaCollection {
        this.storeMeta = value
        return this
    }

    /**
     * The materialized parts of the features stored in the collection.
     *
     * Each [Member] declares a name, a [MemberType], an optional [JsonPath] to extract the value from, and a [retain strategy][MemberRetain]. At write time, the storage walks the feature using the path, coerces the value to the declared type, and stores it as a dedicated member alongside the encoded feature blob. The value also remains in the encoded feature blob.
     *
     * When `null` _(key absent)_, the storage uses the [Xyz members definition][XyzMembers] — this is a backward-compatible mode. When explicitly set _(even to an empty list)_, the storage uses only the mandatory members plus the declared members.
     *
     * The mandatory members [feature-number][StandardMembers.FeatureNumber], [id][StandardMembers.Id], [version][StandardMembers.FeatureVersion], and [next-version][StandardMembers.NextVersion] are injected by the storage, if not explicitly declared as copy with relocation. It is not allowed to change the type of mandatory members, and they can't be removed _(as the storage will always inject them, when they are missing)_. They can only be relocated to another [JsonPath].
     *
     * @since 3.0
     * @see Member
     * @see NakshaCollection.indices
     */
    var members: MemberList? by MEMBERS

    private var _validatedMembers: MemberList? = null

    /**
     * Clears the current [members] and copies the members from the given list into this [members].
     * @since 3.0
     * @throws NakshaException with [ILLEGAL_ARGUMENT][NakshaError.ILLEGAL_ARGUMENT] if the maximum number of members is exceeded.
     */
    @JsName("withMemberList")
    open fun withMembers(values: List<Member?>): NakshaCollection {
        if (values.size > MemberList.MAX_MEMBERS) {
            throw illegalArg("Cannot add more than ${MemberList.MAX_MEMBERS} members to a collection")
        }
        val list = MemberList()
        list.setCapacity(values.size)
        members = list
        _validatedMembers = null
        for (member in values) {
            if (member != null) list.add(member)
        }
        return this
    }

    /**
     * Clears the current [members] and replaces them with the given ones.
     * @since 3.0
     * @throws NakshaException with [ILLEGAL_ARGUMENT][NakshaError.ILLEGAL_ARGUMENT] if the maximum number of members is exceeded.
     */
    open fun withMembers(vararg values: Member): NakshaCollection {
        if (values.size > MemberList.MAX_MEMBERS) {
            throw illegalArg("Cannot add more than ${MemberList.MAX_MEMBERS} members to a collection")
        }
        val list = MemberList()
        list.setCapacity(values.size)
        for (v in values) list.add(v)
        this.members = list
        this._validatedMembers = null
        return this
    }

    /**
     * Safely adds the given member to [members].
     *
     * This method clears the validated members.
     * @param value the member to add.
     * @return this.
     * @since 3.0
     * @throws NakshaException with [ILLEGAL_ARGUMENT][NakshaError.ILLEGAL_ARGUMENT] if the maximum number of members has been reached or another member with the same name exists already.
     */
    open fun addMember(value: Member): NakshaCollection {
        NakshaIdType.INTERNAL_MEMBER.verify(value.name)
        var list = this.members
        if (list == null) {
            list = MemberList()
            list.add(value)
            this.members = list
        } else {
            list.plus(value)
        }
        this._validatedMembers = null
        return this
    }

    /**
     * Initializes the [members] to the bare minimum, therefore [mandatory members][StandardMembers.MANDATORY].
     *
     * The method should be called, if next to the minimal members additional proprietary members should be added.
     * @since 3.0
     */
    fun withMinimalMembers(): NakshaCollection {
        members = MemberList(StandardMembers.MANDATORY)
        return this
    }

    /**
     * Initializes the [members] to the [standard XYZ members][XyzMembers.ALL].
     *
     * The method should be called, if next to the standard XYZ members, additional proprietary members should be added.
     * @since 3.0
     */
    fun withXyzMembers(): NakshaCollection {
        members = MemberList(XyzMembers.ALL)
        return this
    }

    /**
     * The validated members list.
     *
     * If the member list is currently `null`, the collection is in backward-compatible default mode: the members are created from [XyzMembers.ALL], and — so the persisted collection stays consistent across reloads — a still-`null` [indices] list is defaulted to [XyzIndices.ALL] as well. If the list does not contain the mandatory members, they will be added.
     * @return the members list of this collection.
     * @since 3.0
     * @throws NakshaException with [ILLEGAL_STATE]
     */
    open val validatedMembers: MemberList
        get() {
            val current = this.members
            val cached = _validatedMembers
            if (cached != null && cached === current) return cached
            var write = false
            var list = this.members
            if (list == null) {
                list = MemberList(XyzMembers.ALL)
                write = true
                if (this.indices == null) this.indices = IndexList(XyzIndices.ALL)
            }
            // Ensure that the list does not contain null or duplicates.
            list.validateAndSort()
            // Ensure that the mandatory members are in.
            for (mandatory in StandardMembers.MANDATORY) {
                val found: Member? = list.get(mandatory.name)
                if (found != null) {
                    mandatory.asOther(found, comparePath = false)
                } else {
                    // Collection Tn lives at xyz.uuid (the tupleNumber accessor), so backfill XyzTn not the generic Tn.
                    list.add(if (mandatory === StandardMembers.Tn) XyzMembers.XyzTn else mandatory)
                    write = true
                }
            }
            if (write) this.members = list
            _validatedMembers = list
            return list
        }

    /**
     * The indices to maintain on this collection.
     *
     * Each [Index] declares a `name`, the column(s) to index, an optional included columns.
     *
     * ### Warning
     * Do not create an index above [id][StandardMembers.Id], the [feature-number][StandardMembers.FeatureNumber] can be derived from the [id][StandardMembers.Id] and the storage will know this. Therefore, searching for [id][StandardMembers.Id] will automatically be redirected into the primary key _(which includes the [feature-number][StandardMembers.FeatureNumber])_.
     *
     * ### Primary Key
     * There is always one index applied automatically by all storages above the primary key. The primary key consists out of [next-version][StandardMembers.NextVersion], [feature-number][StandardMembers.FeatureNumber], and [version][StandardMembers.FeatureVersion], in that exact order. All indices defined will always at least include these three values, they are necessary for index-only scans _(no matter how exactly this done by the implementation)_.
     * @since 3.0
     * @see Index
     * @see NakshaCollection.members
     */
    var indices: IndexList? by INDICES

    /**
     * Initializes the [indices] to the bare minimum.
     *
     * The method should be called, if next to the minimal indices additional proprietary indices should be added.
     * @since 3.0
     */
    fun withMinimalIndices(): NakshaCollection {
        indices = IndexList()
        return this
    }

    /**
     * Initializes the [indices] to the [standard XYZ indices][XyzIndices.ALL].
     *
     * The method should be called, if next to the standard XYZ indices, additional proprietary indices should be added.
     * @since 3.0
     */
    fun withXyzIndices(): NakshaCollection {
        indices = IndexList(XyzIndices.ALL)
        return this
    }

    /**
     * @see [indices]
     */
    @JsName("withIndexList")
    open fun withIndices(values: IndexList): NakshaCollection {
        val list = IndexList()
        list.setCapacity(values.size)
        list.addAll(values.toList())
        this.indices = list
        return this
    }

    /**
     * @see [indices]
     */
    open fun withIndices(vararg values: Index): NakshaCollection {
        val list = IndexList()
        list.setCapacity(values.size)
        for (v in values) list.add(v)
        this.indices = list
        return this
    }

    /**
     * Adds the given [Index] to [indices].
     *
     * Validates that the index [Index.name] is a valid Naksha identifier (see [Naksha.verifyId]) and is unique within this collection.
     * @param value the index to add.
     * @return this.
     * @since 3.0
     */
    open fun addIndex(value: Index): NakshaCollection {
        NakshaIdType.INDEX.verify(value.name)
        var list = this.indices
        if (list == null) {
            list = IndexList()
            this.indices = list
        }
        for (existing in list) {
            if (existing != null && existing.name == value.name) {
                throw NakshaException(ILLEGAL_ARGUMENT, "Duplicate index name: '${value.name}'")
            }
        }
        list.add(value)
        return this
    }

    /**
     * The maxAge decides about the maximum age of features in the history in days.
     *
     * Note that there is no guarantee that features are deleted exactly after having reached their max-age. However, they are eligible to be deleted at as soon as possible.
     * @since 3.0
     */
    var maxAge by MAX_AGE

    /**
     * @see [maxAge]
     */
    open fun withMaxAge(value: Long): NakshaCollection {
        this.maxAge = value
        return this
    }

    /**
     * The quad-partition-size decides _(for the optimal partitioning algorithm)_ how many features should be placed into each "optimal" tile.
     * @since 3.0
     */
    var quadPartitionSize: Int by QUAD_PARTITION_SIZE

    /**
     * @see [quadPartitionSize]
     */
    open fun withQuadPartitionSize(value: Int): NakshaCollection {
        this.quadPartitionSize = value
        return this
    }

    /**
     * The estimated amount of features stored within a collection, read-only property only set by the storage.
     * @since 3.0
     */
    val estimatedFeatureCount: Long by _ESTIMATED_FEATURE_COUNT

    /**
     * The estimated amount of deleted features within a collection, read-only property only set by the storage.
     * @since 3.0
     */
    val estimatedDeletedFeatures: Long by _ESTIMATED_DELETED_FEATURES

    companion object NakshaCollection_C {
        /**
         * Index above the `id` property, includes `tn`, and `next_version` (HISTORY only).
         * @since 3.0
         */
        const val ID_IDX = "id"

        /**
         * Index above `here_tile`, includes `id`, `tn`, and `next_version` (HISTORY only).
         * @since 3.0
         */
        const val HERE_TILE_IDX = "here_tile"

        /**
         * Index above `app_id`, includes `updated_at`, `id`, `tn`, and `next_version` (HISTORY only).
         * @since 3.0
         */
        const val APP_ID_IDX = "app_id"

        /**
         * Index above `author`, includes `author_ts`, `id`, `tn`, and `tn_next`.
         * @since 3.0
         */
        const val AUTHOR_IDX = "author"

        /**
         * Index above tags, does not allow index-only scans or pre-ordering.
         * @since 3.0
         */
        const val TAGS_IDX = "tags"

        /**
         * Index above reference point geometry, does not allow index-only scans or pre-ordering.
         * @since 3.0
         */
        const val REF_POINT_IDX = "ref_point"

        /**
         * Index above geometry, does not allow index-only scans or pre-ordering.
         * @since 3.0
         */
        const val GIST_IDX = "gist_geo"

        /**
         * Index above geometry, does not allow index-only scans or pre-ordering.
         * @since 3.0
         */
        const val SP_GIST_IDX = "spgist_geo"

        /**
         * Index above geometry, does not allow index-only scans or pre-ordering.
         * @since 3.0
         */
        const val FEATURE_TYPE_IDX = "feature_type"

        /**
         * Index above custom value `0`, does not index `null` values.
         * @since 3.0
         */
        const val CV0_IDX = "cv0"

        /**
         * Index above custom value `1`, does not index `null` values.
         * @since 3.0
         */
        const val CV1_IDX = "cv1"

        /**
         * Index above custom value `2`, does not index `null` values.
         * @since 3.0
         */
        const val CV2_IDX = "cv2"

        /**
         * Index above custom value `3`, does not index `null` values.
         * @since 3.0
         */
        const val CV3_IDX = "cv3"

        /**
         * Index above custom string `0`, does not index `null` values.
         * @since 3.0
         */
        const val CS0_IDX = "cs0"

        /**
         * Index above custom string `1`, does not index `null` values.
         * @since 3.0
         */
        const val CS1_IDX = "cs1"

        /**
         * Index above custom string `2`, does not index `null` values.
         * @since 3.0
         */
        const val CS2_IDX = "cs2"

        /**
         * Index above custom string `3`, does not index `null` values.
         * @since 3.0
         */
        const val CS3_IDX = "cs3"

        /**
         * Index above `ft` _(aka feature-type)_, includes `id`, `tn`, and `next_version` (HISTORY only).
         * @since 3.0
         */
        const val FEATURE_TYPE = "naksha.Collection"

        /**
         * The value returned as [estimatedFeatureCount] and [estimatedDeletedFeatures], before the estimation was actually done, so when the number is totally unknown _(-1)_.
         * @since 3.0
         */
        @JvmStatic
        @JsStatic
        val UNKNOWN = -1L

        private val DATABASE_ID = NullableProperty<NakshaCollection, String>(String::class)
        private val CATALOG_ID = NullableProperty<NakshaCollection, String>(String::class)
        private val PARTITIONS = NotNullProperty<NakshaCollection, Int>(Int::class) { _, _ -> 1 }
        private val SHIFT = NotNullProperty<NakshaCollection, Int>(Int::class) { _, _ -> 37 }
        private val RETAIN = NotNullProperty<NakshaCollection, Int>(Int::class) { _, _ -> 24 }
        private val BACKWARD = NotNullProperty<NakshaCollection, Int>(Int::class) { _, _ -> -1 }
        private val FORWARD = NotNullProperty<NakshaCollection, Int>(Int::class) { _, _ -> 3 }
        private val STORAGE_CLASS = NullableProperty<NakshaCollection, String>(String::class)
        private val PROTECTION_CLASS = NullableProperty<NakshaCollection, String>(String::class)
        private val MEMBERS = NullableProperty<NakshaCollection, MemberList>(MemberList::class)
        private val INDICES = NullableProperty<NakshaCollection, IndexList>(IndexList::class)
        private val MAX_AGE = NotNullProperty<NakshaCollection, Long>(Long::class) { _, _ -> -1L }
        private val QUAD_PARTITION_SIZE = NotNullProperty<NakshaCollection, Int>(Int::class) { _, _ -> 10_485_760 }
        private val _ESTIMATED_FEATURE_COUNT = NotNullProperty<NakshaCollection, Long>(Long::class) { _, _ -> UNKNOWN }
        private val _ESTIMATED_DELETED_FEATURES =  NotNullProperty<NakshaCollection, Long>(Long::class) { _, _ -> UNKNOWN }
        private val STORE_HISTORY = NotNullEnum<NakshaCollection, StoreMode>(StoreMode::class) { self, _ ->
            // For downward compatibility with Naksha version 2
            val old = self.getRaw("disableHistory")
            if (old == true) StoreMode.OFF else StoreMode.ON
        }
        private val STORE_DELETED = NotNullEnum<NakshaCollection, StoreMode>(StoreMode::class) { self, _ ->
            // For downward compatibility with Naksha version 2
            val old = self.getRaw("autoPurge")
            if (old == true) StoreMode.OFF else StoreMode.ON
        }
        private val STORE_META = NotNullEnum<NakshaCollection, StoreMode>(StoreMode::class) { _, _ -> StoreMode.ON }
    }
}
