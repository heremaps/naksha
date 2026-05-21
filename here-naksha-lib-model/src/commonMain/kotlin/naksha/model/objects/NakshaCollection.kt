@file:Suppress("OPT_IN_USAGE", "LeakingThis")

package naksha.model.objects

import naksha.base.*
import naksha.geo.SpBoundingBox
import naksha.geo.SpGeometry
import naksha.geo.SpPoint
import naksha.model.Flags
import naksha.model.Naksha
import naksha.model.NakshaError
import naksha.model.NakshaException
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.js.JsStatic
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic

/**
 * A Naksha collection.
 * @since 3.0
 */
@JsExport
open class NakshaCollection() : NakshaFeature() {

    /**
     * Create a Naksha collection with settings.
     * @param id the collection-identifier.
     * @param mapId the map-identifier of the map in which the collection should be created
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
        mapId: String? = null,
        partitions: Int = 1,
        storageClass: String? = null,
        storeDeleted: StoreMode = StoreMode.ON,
        storeHistory: StoreMode = StoreMode.ON,
        storeMeta: StoreMode = StoreMode.ON,
    ) : this() {
        this.id = id
        this.mapId = mapId
        this.storageClass = storageClass
        this.partitions = partitions
        this.storeDeleted = storeDeleted
        this.storeHistory = storeHistory
        this.storeMeta = storeMeta
    }

    override fun featureTypeDefaultValue(): String = FEATURE_TYPE
    override fun withId(value: String): NakshaCollection = super.withId(value) as NakshaCollection
    override fun withFeatureNumber(value: Int64): NakshaCollection = super.withFeatureNumber(value) as NakshaCollection
    override fun withType(value: String): NakshaCollection = super.withType(value) as NakshaCollection
    override fun withFeatureType(value: String): NakshaCollection = super.withFeatureType(value) as NakshaCollection
    override fun withBbox(value: SpBoundingBox?): NakshaCollection = super.withBbox(value) as NakshaCollection
    override fun withGeometry(value: SpGeometry?): NakshaCollection = super.withGeometry(value) as NakshaCollection
    override fun withReferencePoint(value: SpPoint?): NakshaCollection = super.withReferencePoint(value) as NakshaCollection
    override fun withProperties(value: NakshaProperties): NakshaCollection = super.withProperties(value) as NakshaCollection
    override fun withMomType(value: String?): NakshaCollection = super.withMomType(value) as NakshaCollection

    override fun featureNumberOfId(id: String): Int64 = Naksha.collectionNumber(id).toInt64()

    /**
     * The number of the collection, which is basically [featureNumber].
     * @since 3.0
     */
    val number: Int
        get() = featureNumber.toInt()

    /**
     * Always return `0`, because all collections are always stored in `naksha~collections` collection.
     * @since 3.0
     * @see [Naksha.COLLECTIONS_COL]
     * @see [Naksha.COLLECTIONS_COL_NUMBER]
     */
    override val collectionNumber: Int
        get() = Naksha.COLLECTIONS_COL_NUMBER

    /**
     * The map-id of the map in which the collection is located; `null` if not yet known.
     * @since 3.0
     */
    var mapId by MAP_ID

    /**
     * @see [mapId]
     */
    fun withMapId(value: String?): NakshaCollection {
        mapId = value
        return this
    }

    /**
     * If partitions is given, then collection is internally partitioned in the storage, and optimised for large quantities of features. The default is no partitions, for around every 10 to 20 million features expected to be stored in a collection, one more partition should be requested.
     *
     * Valid values are between `1` and `65536` _(exclusive)_, the values `undefined`, `null` and `0` are interpreted as one partition (`1`), all other values will be rejected.
     *
     * Beware that in AWS ever point-to-point connection is generally limited to 5 Gbps. To reach the full throughput when reading features from a database with a 200 Gbps bandwidth, at least 40 partitions are needed, so 40 * 5 Gbps = 200 Gbps throughput.
     *
     * **{Create-Only}** - after collection creation, modification of this parameter takes no effect.
     * @since 3.0
     */
    var partitions: Int by PARTITIONS

    /**
     * @see [partitions]
     */
    open fun withPartitions(value: Int): NakshaCollection {
        this.partitions = value
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
     * The protectionClass defines how collections should be protected.
     *
     * Values supported by `lib-psql` are:
     * - `FULL`: Install triggers to prevent any manual change in collections, so that changed are only allowed using `lib-psql`, reading the data is possible.
     * - `SAVE`: Installs triggers that automatically apply fixes, so write the history and transaction logs. The disadvantage is that the triggers will slow down the processing, but they allow to actually execute any kind of SQL query without breaking the internal structures.
     * - `NONE`: Removes all protecting triggers and allow any kind of manual change, but this can easily break the history and/or transaction logs, as well allows the creation of invalid table entries that can break `lib-psql`.
     *
     * If _null_, the storage will use whatever is best for the storage.
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
     * If the `featureType`as returned by [NakshaFeature.featureType] equals to this value, then the [metadata feature-type][naksha.model.Metadata.ft] will be `null`, otherwise [metadata feature-type][naksha.model.Metadata.ft] is set to the [NakshaFeature.featureType].
     *
     * ### Note
     * The index on the [feature-type][naksha.model.Metadata.ft] is partial, features are only indexed when `ft` is not `null`, what is always the case, when it matches the [defaultFeatureType]. This is based upon the assumption, that in most cases all features within a collection do have the same feature-type. If this assumption holds true, and index would be a big waste, even when only a few features differ from the [defaultFeatureType], adding all values into the index would be a waste. So, this property is for the query planner to take advantage of this fact, when searching for feature-type. If the feature-type is the [defaultFeatureType], this means most of the time a full collection scan, so usage of the feature-type index is not helpful.
     * @since 3.0
     */
    var defaultFeatureType by DEFAULT_FEATURE_TYPE

    /**
     * @see [defaultFeatureType]
     */
    open fun withDefaultFeatureType(value: String?): NakshaCollection {
        removeRaw("defaultFeatureType")
        return this
    }

    /**
     * The encoding flags to be used for new rows.
     *
     * - If _null_, the [defaultFlags][NakshaMap.defaultFlags] of the [map][NakshaMap] will be used.
     * @since 3.0
     */
    var defaultFlags by DEFAULT_FLAGS

    /**
     * @see [defaultFlags]
     */
    open fun withDefaultFlags(value: Flags): NakshaCollection {
        this.defaultFlags = value
        return this
    }

    /**
     * The identifier of the global dictionary to use, when encoding new rows.
     *
     * - If _null_, the storage will use whatever is best for the storage.
     * @since 3.0
     */
    var encodeDict by STRING_NULL

    /**
     * @see [encodeDict]
     */
    open fun withEncodeDict(value: String?): NakshaCollection {
        this.encodeDict = value
        return this
    }

    /**
     * If [StoreMode.OFF] there will be no history table in the database for features in this collection, which boosts performance in certain operations.
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
     * If [StoreMode.OFF] there will be no table in the database for deleted features from this collection, which boosts performance in certain operations, but impact views as provided by `lib-view`.
     */
    var storeDeleted by STORE_DELETED

    /**
     * @see [storeDeleted]
     */
    open fun withStoreDeleted(value: StoreMode): NakshaCollection {
        this.storeDeleted = value
        return this
    }

    /**
     * If [StoreMode.OFF] there will be no meta table in the database for statistics of features in this collection, this can save money and storage cost, by not generating statistical data, but may avoid certain use cases, like optimal tile distribution queries.
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
     * The user-defined columns to materialize on this collection.
     *
     * Each [CustomMember] declares a name, a [CustomMemberType] (defaults to [CustomMemberType.STRING]), and an optional [JsonPath] to extract the value from the feature. At write time, the storage walks the feature using the path, coerces the value to the declared type, and stores it as a real column on the underlying table. The value also remains in the encoded feature blob.
     *
     * Members are immutable in shape: changing a member's [CustomMember.dataType] after creation is not supported. To remove a member, the upsert must explicitly opt in to dropping the column (storage-specific `force` flag).
     *
     * The [name] of a member must be a valid Naksha identifier (see [Naksha.verifyId]); storages reserve a separate namespace for user columns to avoid collisions with built-ins.
     *
     * @since 3.0
     */
    var members: CustomMemberList? by MEMBERS

    /**
     * @see [members]
     */
    @JsName("withMemberList")
    open fun withMembers(values: CustomMemberList): NakshaCollection {
        val list = CustomMemberList()
        list.setCapacity(values.size)
        list.addAll(values.toList())
        this.members = list
        return this
    }

    /**
     * @see [members]
     */
    open fun withMembers(vararg values: CustomMember): NakshaCollection {
        val list = CustomMemberList()
        list.setCapacity(values.size)
        for (v in values) list.add(v)
        this.members = list
        return this
    }

    /**
     * Adds the given [CustomMember] to [members].
     *
     * Validates that the [CustomMember.name] is a valid Naksha identifier (see [Naksha.verifyId]) and does not already exist on this collection. Caps the list at [CustomMemberList.MAX_MEMBERS] entries.
     * @param value the member to add.
     * @return this.
     * @since 3.0
     */
    open fun addMember(value: CustomMember): NakshaCollection {
        Naksha.verifyId(value.name)
        var list = this.members
        if (list == null) {
            list = CustomMemberList()
            this.members = list
        }
        for (existing in list) {
            if (existing != null && existing.name == value.name) {
                throw NakshaException(NakshaError.ILLEGAL_ARGUMENT, "Duplicate member name: '${value.name}'")
            }
        }
        if (list.size >= CustomMemberList.MAX_MEMBERS) {
            throw NakshaException(NakshaError.ILLEGAL_ARGUMENT, "Cannot add more than ${CustomMemberList.MAX_MEMBERS} members to a collection")
        }
        list.add(value)
        return this
    }

    /**
     * The user-defined indices to maintain on this collection.
     *
     * Each [CustomIndex] declares a name, a [CustomIndexType] ([CustomIndexType.BTREE] / [CustomIndexType.SPATIAL] / [CustomIndexType.FLAT_MAP]), the column(s) to index, an optional include-list (for [CustomIndexType.BTREE]), and a `unique` flag.
     *
     * Indices are applied to every variant of the collection (HEAD, HISTORY, DELETED, META) by the storage.
     * @since 3.0
     */
    var indices: CustomIndexList? by INDICES

    /**
     * @see [indices]
     */
    @JsName("withIndexList")
    open fun withIndices(values: CustomIndexList): NakshaCollection {
        val list = CustomIndexList()
        list.setCapacity(values.size)
        list.addAll(values.toList())
        this.indices = list
        return this
    }

    /**
     * @see [indices]
     */
    open fun withIndices(vararg values: CustomIndex): NakshaCollection {
        val list = CustomIndexList()
        list.setCapacity(values.size)
        for (v in values) list.add(v)
        this.indices = list
        return this
    }

    /**
     * Adds the given [CustomIndex] to [indices].
     *
     * Validates that the index [CustomIndex.name] is a valid Naksha identifier (see [Naksha.verifyId]) and is unique within this collection.
     * @param value the index to add.
     * @return this.
     * @since 3.0
     */
    open fun addCustomIndex(value: CustomIndex): NakshaCollection {
        Naksha.verifyId(value.name)
        var list = this.indices
        if (list == null) {
            list = CustomIndexList()
            this.indices = list
        }
        for (existing in list) {
            if (existing != null && existing.name == value.name) {
                throw NakshaException(NakshaError.ILLEGAL_ARGUMENT, "Duplicate index name: '${value.name}'")
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
    open fun withMaxAge(value: Int64): NakshaCollection {
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
    val estimatedFeatureCount: Int64 by _ESTIMATED_FEATURE_COUNT

    /**
     * The estimated amount of deleted features within a collection, read-only property only set by the storage.
     * @since 3.0
     */
    val estimatedDeletedFeatures: Int64 by _ESTIMATED_DELETED_FEATURES

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
        val UNKNOWN = Int64(-1)

        private val PARTITIONS = NotNullProperty<NakshaCollection, Int>(Int::class) { _, _ -> 1 }
        private val STORAGE_CLASS = NullableProperty<NakshaCollection, String>(String::class)
        private val PROTECTION_CLASS = NullableProperty<NakshaCollection, String>(String::class)
        private val DEFAULT_FLAGS = NullableProperty<NakshaCollection, Flags>(Flags::class)
        private val MAP_ID = NullableProperty<NakshaCollection, String>(String::class)
        private val STRING_NULL = NullableProperty<NakshaCollection, String>(String::class)
        private val DEFAULT_FEATURE_TYPE = NotNullProperty<NakshaCollection, String>(String::class) { _, _ -> TYPE }
        private val MEMBERS = NullableProperty<NakshaCollection, CustomMemberList>(CustomMemberList::class)
        private val INDICES = NullableProperty<NakshaCollection, CustomIndexList>(CustomIndexList::class)
        private val MAX_AGE = NotNullProperty<NakshaCollection, Int64>(Int64::class) { _, _ -> Int64(-1) }
        private val QUAD_PARTITION_SIZE = NotNullProperty<NakshaCollection, Int>(Int::class) { _, _ -> 10_485_760 }
        private val _ESTIMATED_FEATURE_COUNT = NotNullProperty<NakshaCollection, Int64>(Int64::class) { _, _ -> UNKNOWN }
        private val _ESTIMATED_DELETED_FEATURES =  NotNullProperty<NakshaCollection, Int64>(Int64::class) { _, _ -> UNKNOWN }
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
