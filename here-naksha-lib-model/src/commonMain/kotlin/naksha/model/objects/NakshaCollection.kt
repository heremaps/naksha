@file:Suppress("OPT_IN_USAGE", "LeakingThis")

package naksha.model.objects

import naksha.base.*
import naksha.geo.SpBoundingBox
import naksha.geo.SpGeometry
import naksha.geo.SpPoint
import naksha.model.Flags
import naksha.model.Naksha
import naksha.model.NakshaContext
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
     * @param mapId the map-identifier of the map in which the collection should be created; if `null`, then [NakshaContext.mapId] is used.
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
        this.mapId = mapId ?: NakshaContext.mapId()
        this.storageClass = storageClass
        this.partitions = partitions
        this.storeDeleted = storeDeleted
        this.storeHistory = storeHistory
        this.storeMeta = storeMeta
    }

    override fun defaultFeatureType(): String = FEATURE_TYPE
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
     * If the feature-type in the [metadata][naksha.model.Metadata.ft] should be set automatically, therefore indexing the feature type. When explicitly enabled, the storage will read the [feature-type][NakshaFeature.featureType], and copy it into the [metadata feature-type][naksha.model.Metadata.ft].
     *
     * ### Note
     * The index on the [feature-type][naksha.model.Metadata.ft] is disabled, when the feature-type is `null`, therefore enabling this option, which is by default turned off, will automatically enable indexing of the feature-type and auto population of the metadata [feature-type][naksha.model.Metadata.ft] field.
     * @since 3.0
     */
    var indexFeatureType by BOOLEAN_FALSE

    /**
     * @see [indexFeatureType]
     */
    open fun withIndexFeatureType(value: Boolean): NakshaCollection {
        this.indexFeatureType = value
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
     * The index list with all indices to add to the collection; if set to _null_, default indices are created.
     *
     * For `lib-psql` the following indices are available:
     * - `id_txn_uid`: id text_pattern_ops DESC, txn DESC, uid DESC, txn_next DESC INCLUDE tn.
     * - `here_tile`: here_tile DESC, id text_pattern_ops DESC, txn DESC, uid DESC, txn_next DESC INCLUDE tn.
     * - `app_id`: app_id text_pattern_ops DESC, updated_at DESC, id text_pattern_ops DESC, txn DESC, uid DESC, txn_next DESC INCLUDE tn.
     * - `author`: naksha_author(author, app_id) text_pattern_ops DESC, naksha_author_ts(author_ts, updated_at) DESC, id text_pattern_ops DESC, txn DESC, uid DESC, txn_next DESC INCLUDE tn.
     * - `tags`: Index above tags, does not allow index-only scans or pre-ordering.
     * - `ref_point`: Index above geometry, does not allow index-only scans or pre-ordering.
     * - `gist_geo_(2d|3d|4d)` or `spgist_geo_(2d|3d|4d)`: Index above geometry, does not allow index-only scans or pre-ordering.
     * - `feature_type`: ft text_pattern_ops DESC, id text_pattern_ops DESC, txn DESC, uid DESC, txn_next DESC INCLUDE $c_tn
     * - `cv0`, `cv1`, `cv2`, and `cv3`: cvX text_pattern_ops DESC, id text_pattern_ops DESC, txn DESC, uid DESC, txn_next DESC INCLUDE $c_tn
     * - `cs0`, `cs1`, `cs2`, and `cs3`: csX text_pattern_ops DESC, id text_pattern_ops DESC, txn DESC, uid DESC, txn_next DESC INCLUDE $c_tn
     *
     * To use less or other indices, create an own list of indices out of the above given values, `lib-psql` will add all these indices by default, using `2d` variants for the geometry index by default. Beware, that many of the indices exclude _null_ values, and therefore do not costing anything, unless the values are used.
     *
     * It is not recommended, to add multiple geometry indices, this can become extreme costly.
     * @since 3.0
     */
    var indices by INDICES

    /**
     * @see [indices]
     */
    open fun withIndex(value: String): NakshaCollection {
        var indices = this.indices
        if (indices == null) {
            indices = StringList()
            this.indices = indices
        }
        if (!indices.contains(value)) indices.add(value)
        return this
    }

    /**
     * @see [indices]
     */
    open fun withIndices(vararg values: String): NakshaCollection {
        @Suppress("SENSELESS_COMPARISON")
        if (values != null && values.isNotEmpty()) {
            var indices = this.indices
            if (indices == null) {
                indices = StringList()
                this.indices = indices
            }
            for (value in values) if (!indices.contains(value)) indices.add(value)
        }
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
         * The feature-type of this feature itself _(`naksha.Collection`)_.
         * @since 3.0
         */
        const val FEATURE_TYPE = "naksha.Collection"

        /**
         * partition count = 0 -> no partitions only head
         * partition count = 2 -> 2 partitions
         * partition count = n -> n partitions
         * @since 3.0
         */
        const val NO_PARTITIONS = 0

        /**
         * To create a collection without a geometry index.
         * @since 3.0
         */
        const val GEO_INDEX_NONE = "none"

        /**
         * To create a collection with a GIST geometry-index.
         * @since 3.0
         */
        const val GEO_INDEX_GIST = "gist"

        /**
         * To create a collection with an SP-GIST geometry-index.
         * @since 3.0
         */
        const val GEO_INDEX_SP_GIST = "sp-gist"

        /**
         * Default geo_index - may change over time.
         * @since 3.0
         */
        const val DEFAULT_GEO_INDEX = GEO_INDEX_GIST

        /**
         * The value returned as [estimatedFeatureCount] and [estimatedDeletedFeatures], before the estimation was actually done, so when the number is totally unknown _(-1)_.
         * @since 3.0
         */
        @JvmStatic
        @JsStatic
        val UNKNOWN = Int64(-1)

        /**
         * The name of the [estimatedFeatureCount] property.
         * @since 3.0
         */
        const val ESTIMATED_FEATURE_COUNT = "estimatedFeatureCount"

        /**
         * The name of the [estimatedDeletedFeatures] property.
         * @since 3.0
         */
        const val ESTIMATED_DELETED_FEATURES = "estimatedDeletedFeatures"

        private val PARTITIONS = NotNullProperty<NakshaCollection, Int>(Int::class) { _, _ -> 1 }
        private val GEO_INDEX = NotNullProperty<NakshaCollection, String>(String::class) { _, _ -> DEFAULT_GEO_INDEX }
        private val STORAGE_CLASS = NullableProperty<NakshaCollection, String>(String::class)
        private val PROTECTION_CLASS = NullableProperty<NakshaCollection, String>(String::class)
        private val BOOLEAN_FALSE = NotNullProperty<NakshaCollection, Boolean>(Boolean::class) { _, _ -> false }
        private val DEFAULT_FLAGS = NullableProperty<NakshaCollection, Flags>(Flags::class)
        private val INT_NULL = NullableProperty<NakshaCollection, Int>(Int::class)
        private val MAP_ID = NullableProperty<NakshaCollection, String>(String::class)
        private val STRING_NULL = NullableProperty<NakshaCollection, String>(String::class)
        private val INDICES = NullableProperty<NakshaCollection, StringList>(StringList::class)
        private val MAX_AGE = NotNullProperty<NakshaCollection, Int64>(Int64::class) { _, _ -> Int64(-1) }
        private val QUAD_PARTITION_SIZE = NotNullProperty<NakshaCollection, Int>(Int::class) { _, _ -> 10_485_760 }
        private val _ESTIMATED_FEATURE_COUNT = NotNullProperty<NakshaCollection, Int64>(Int64::class) { _, _ -> UNKNOWN }
        private val _ESTIMATED_DELETED_FEATURES =  NotNullProperty<NakshaCollection, Int64>(Int64::class) { _, _ -> UNKNOWN }
        private val AUTO_PURGE = NotNullProperty<NakshaCollection, Boolean>(Boolean::class) { _, _ -> false }
        private val STORE_HISTORY = NotNullEnum<NakshaCollection, StoreMode>(StoreMode::class) { self, _ ->
            // For downward compatibility with Naksha version 2
            val old = self.getRaw("disableHistory")
            if (old == true) StoreMode.SUSPEND else StoreMode.ON
        }
        private val STORE_DELETED = NotNullEnum<NakshaCollection, StoreMode>(StoreMode::class) { self, _ ->
            // For downward compatibility with Naksha version 2
            val old = self.getRaw("autoPurge")
            if (old == true) StoreMode.SUSPEND else StoreMode.ON
        }
        private val STORE_META = NotNullEnum<NakshaCollection, StoreMode>(StoreMode::class) { _, _ -> StoreMode.ON }
    }
}
