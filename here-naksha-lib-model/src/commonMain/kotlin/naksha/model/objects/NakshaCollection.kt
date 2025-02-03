@file:Suppress("OPT_IN_USAGE")

package naksha.model.objects

import naksha.base.Int64
import naksha.base.NotNullProperty
import naksha.base.NullableProperty
import naksha.base.StringList
import naksha.model.Flags
import naksha.model.NakshaContext
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.js.JsStatic
import kotlin.jvm.JvmStatic

/**
 * A Naksha collection.
 * @since 3.0.0
 */
@JsExport
open class NakshaCollection() : NakshaFeature() {
    /**
     * Create a new collection object with a specific identifier and all other properties being their default.
     * @param id the identifier of the collection.
     * @since 3.0.0
     */
    @Suppress("LeakingThis")
    @JsName("of")
    constructor(id: String) : this() {
        this.id = id
    }

    override fun defaultFeatureType(): String = FEATURE_TYPE

    /**
     * The map-id of the map in which the collection is located, defaults to [NakshaContext.mapId].
     * @since 3.0.0
     */
    var mapId by MAP_ID

    /**
     * @see [mapId]
     */
    fun withMapId(value: String): NakshaCollection {
        this.mapId = value
        return this
    }

    /**
     * Sets the [mapId] to the one of the given map.
     * @param map the map in which the collection is/should be located.
     * @return this
     * @since 3.0.0
     */
    fun inMap(map: NakshaMap): NakshaCollection {
        this.mapId = map.id
        return this
    }

    /**
     * The collection-number, _null_ if the collection does not yet exist.
     * @since 3.0.0
     */
    var number by INT_NULL

    /**
     * @see [number]
     */
    fun withNumber(value: Int?): NakshaCollection {
        this.number = value
        return this
    }

    /**
     * If partitions is given, then collection is internally partitioned in the storage, and optimised for large quantities of features. The default is no partitions, for around every 10 to 20 million features expected to be stored in a collection, one more partition should be requested.
     *
     * Valid values are between 1 and 256, the value _undefined_, _null_ and `0` are interpreted as one partition (`1`), all other values will be rejected.
     *
     * Beware that in AWS ever point-to-point connection is generally limited to 5 Gbps. To reach the full throughput when reading features from a database with a 200 Gbps bandwidth, at least 40 partitions are needed, so 40 * 5 Gbps = 200 Gbps throughput.
     *
     * {Create-Only} - after collection creation, modification of this parameter takes no effect.
     * @since 3.0.0
     */
    var partitions: Int by PARTITIONS

    /**
     * @see [partitions]
     */
    fun withPartitions(value: Int): NakshaCollection {
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
     * The possible values are implementation specific, for lib-psql there is consistent (the default), which is a normal collection.
     * Other options are: brittle and temporary.
     * The brittle storage class force all the tables of the collection to be created on ephemeral storage and to be unlogged, distributed across multiple local SSDs.
     * This drastically improves the read and write performance, but no backups are done, no read-replicas are available, and any server crash can corrupt the data.
     * The temporary option is the same as brittle, but it will be auto-deleted when the session is closed.
     * <br>
     * {Create-Only} - after collection creation, modification of this parameter takes no effect.
     */
    var storageClass: String? by STORAGE_CLASS

    /**
     * @see [storageClass]
     */
    fun withStorageClass(value: String?): NakshaCollection {
        this.storageClass = value
        return this
    }

    /**
     * The protectionClass defines how collections should be protected.
     * The default is FULL, which means that triggers are installed that prevent any manual change, so changed are only allow through the lib-psql.
     * Next to this, two alternatives are there: SAVE, which installs triggers that automatically apply fixes, so write the history and transaction logs.
     * The disadvantage of these are, that they slow down the processing, but allow to actually do any kind of SQL query.
     * The final ones are NONE, which removes all protecting triggers and allow any kind of manual change, but this can easily break the history and/or transaction logs.
     * @since 3.0.0
     */
    var protectionClass by PROTECTION_CLASS

    /**
     * @see [protectionClass]
     */
    fun withProtectionClass(value: String): NakshaCollection {
        this.protectionClass = value
        return this
    }

    /**
     * If the feature-type in the [metadata][naksha.model.Metadata] should be set automatically, therefore indexing the feature type. When explicitly enabled, the storage will read the [feature-type][NakshaFeature.featureType], and copy it into the [metadata feature-type][naksha.model.Metadata.ft].
     * @since 3.0.0
     */
    var autoFeatureTypeIndex by BOOLEAN_FALSE

    /**
     * @see [autoFeatureTypeIndex]
     */
    fun withAutoFeatureTypeIndex(value: Boolean): NakshaCollection {
        this.autoFeatureTypeIndex = value
        return this
    }

    /**
     * The encoding flags to be used for new rows.
     *
     * - If _null_, the storage will use whatever is best for the storage.
     * @since 3.0.0
     */
    var defaultFlags by DEFAULT_FLAGS

    /**
     * @see [defaultFlags]
     */
    fun withDefaultFlags(value: Flags): NakshaCollection {
        this.defaultFlags = value
        return this
    }

    /**
     * The identifier of the global dictionary to use, when encoding new rows.
     *
     * - If _null_, the storage will use whatever is best for the storage.
     * @since 3.0.0
     */
    var encodeDict by STRING_NULL

    /**
     * @see [encodeDict]
     */
    fun withEncode(value: String?): NakshaCollection {
        this.encodeDict = value
        return this
    }

    /**
     * _true_ - disables history of features' modifications.
     * @since 3.0.0
     */
    var disableHistory by DISABLE_HISTORY

    /**
     * @see [disableHistory]
     */
    fun withDisableHistory(value: Boolean): NakshaCollection {
        this.disableHistory = value
        return this
    }

    /**
     * If autoPurge is enabled, deleted features are automatically purged and no shadow state is kept available.
     * Note that if [disableHistory] is false, the deleted features will still be around in the history. This mainly effects lib-view.
     * @since 3.0.0
     */
    var autoPurge by AUTO_PURGE

    /**
     * @see [autoPurge]
     */
    fun withAutoPurge(value: Boolean): NakshaCollection {
        this.autoPurge = value
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
     * - `cv0`, `cv1`, `cv2`, and `cv3`: cv? text_pattern_ops DESC, id text_pattern_ops DESC, txn DESC, uid DESC, txn_next DESC INCLUDE $c_tn
     * - `cs0`, `cs1`, `cs2`, and `cs3`: cs? text_pattern_ops DESC, id text_pattern_ops DESC, txn DESC, uid DESC, txn_next DESC INCLUDE $c_tn
     *
     * To use less or other indices, create an own list of indices out of the above given values, `lib-psql` will all these indices by default, using `2d` variants for the geometry index by default. Beware, that many of the indices exclude _null_ value, and therefore are not costing anything, unless the values are used.
     *
     * It is not recommended, to add multiple geometry indices, this can become extreme costly.
     * @since 3.0.0
     */
    var indices by INDICES

    /**
     * @see [indices]
     */
    fun withIndices(value: StringList): NakshaCollection {
        this.indices = value
        return this
    }

    /**
     * The maxAge decides about the maximum age of features in the history in days.
     * Note that there is no guarantee that features are deleted exactly after having reached their max-age.
     * However, they are eligible to be deleted at as soon as possible.
     * @since 3.0.0
     */
    var maxAge by MAX_AGE

    /**
     * @see [maxAge]
     */
    fun withMaxAge(value: Int64): NakshaCollection {
        this.maxAge = value
        return this
    }

    /**
     * The quadPartitionSize decides _(for the optimal partitioning algorithm)_ how many features should be placed into each "optimal" tile.
     * @since 3.0.0
     */
    var quadPartitionSize: Int by QUAD_PARTITION_SIZE

    /**
     * @see [quadPartitionSize]
     */
    fun withQuadPartitionSize(value: Int): NakshaCollection {
        this.quadPartitionSize = value
        return this
    }

    /**
     * The estimated amount of features stored within a collection, read-only property only set by the storage.
     * @since 3.0.0
     */
    val estimatedFeatureCount: Int64 by _ESTIMATED_FEATURE_COUNT

    /**
     * The estimated amount of deleted features within a collection, read-only property only set by the storage.
     * @since 3.0.0
     */
    val estimatedDeletedFeatures: Int64 by _ESTIMATED_DELETED_FEATURES

    companion object NakshaCollection_C {
        /**
         * The feature-type of this feature itself.
         * @since 3.0.0
         */
        const val FEATURE_TYPE = "naksha.Collection"

        /**
         * partition count = 0 -> no partitions only head
         * partition count = 2 -> 2 partitions
         * partition count = n -> n partitions
         * @since 3.0.0
         */
        const val NO_PARTITIONS = 0

        /**
         * To create a collection without a geometry index.
         * @since 3.0.0
         */
        const val GEO_INDEX_NONE = "none"

        /**
         * To create a collection with a GIST geometry-index.
         * @since 3.0.0
         */
        const val GEO_INDEX_GIST = "gist"

        /**
         * To create a collection with an SP-GIST geometry-index.
         * @since 3.0.0
         */
        const val GEO_INDEX_SP_GIST = "sp-gist"

        /**
         * Default geo_index - may change over time.
         * @since 3.0.0
         */
        const val DEFAULT_GEO_INDEX = GEO_INDEX_GIST

        /**
         * The value returned as [estimatedFeatureCount] and [estimatedDeletedFeatures], before the estimation was actually done, so when the number is totally unknown _(-1)_.
         * @since 3.0.0
         */
        @JvmStatic
        @JsStatic
        val UNKNOWN = Int64(-1)

        /**
         * The name of the [estimatedFeatureCount] property.
         * @since 3.0.0
         */
        const val ESTIMATED_FEATURE_COUNT = "estimatedFeatureCount"

        /**
         * The name of the [estimatedDeletedFeatures] property.
         * @since 3.0.0
         */
        const val ESTIMATED_DELETED_FEATURES = "estimatedDeletedFeatures"

        private val PARTITIONS = NotNullProperty<NakshaCollection, Int>(Int::class) { _, _ -> 0 }
        private val GEO_INDEX = NotNullProperty<NakshaCollection, String>(String::class) { _, _ -> DEFAULT_GEO_INDEX }
        private val STORAGE_CLASS = NullableProperty<NakshaCollection, String>(String::class)
        private val PROTECTION_CLASS = NullableProperty<NakshaCollection, String>(String::class)
        private val BOOLEAN_FALSE = NotNullProperty<NakshaCollection, Boolean>(Boolean::class) { _, _ -> false }
        private val DEFAULT_FLAGS = NullableProperty<NakshaCollection, Flags>(Flags::class)
        private val INT_NULL = NullableProperty<NakshaCollection, Int>(Int::class)
        private val MAP_ID = NotNullProperty<NakshaCollection, String>(String::class) { _, _ -> NakshaContext.mapId() }
        private val STRING_NULL = NullableProperty<NakshaCollection, String>(String::class)
        private val DISABLE_HISTORY = NotNullProperty<NakshaCollection, Boolean>(Boolean::class) { _, _ -> false }
        private val AUTO_PURGE = NotNullProperty<NakshaCollection, Boolean>(Boolean::class) { _, _ -> false }
        private val INDICES = NotNullProperty<NakshaCollection, StringList>(StringList::class)
        private val MAX_AGE = NotNullProperty<NakshaCollection, Int64>(Int64::class) { _, _ -> Int64(-1) }
        private val QUAD_PARTITION_SIZE = NotNullProperty<NakshaCollection, Int>(Int::class) { _, _ -> 10_485_760 }
        private val _ESTIMATED_FEATURE_COUNT = NotNullProperty<NakshaCollection, Int64>(Int64::class) { _, _ -> UNKNOWN }
        private val _ESTIMATED_DELETED_FEATURES =  NotNullProperty<NakshaCollection, Int64>(Int64::class) { _, _ -> UNKNOWN }
    }
}