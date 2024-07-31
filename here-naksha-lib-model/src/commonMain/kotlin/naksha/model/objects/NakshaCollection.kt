@file:Suppress("OPT_IN_USAGE")

package naksha.model.objects

import naksha.base.Int64
import naksha.base.NotNullProperty
import naksha.base.NullableProperty
import naksha.base.StringList
import naksha.model.Flags
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.js.JsStatic
import kotlin.jvm.JvmStatic

/**
 * A Naksha collection.
 */
@JsExport
open class NakshaCollection() : NakshaFeature() {

    @JsName("of")
    constructor(
        id: String,
        partitions: Int,
        geoIndex: String = DEFAULT_GEO_INDEX,
        storageClass: String? = null,
        autoPurge: Boolean = false,
        disableHistory: Boolean = false
    ) : this() {
        this.id = id
        this.geoIndex = geoIndex
        this.storageClass = storageClass
        this.partitions = partitions
        this.autoPurge = autoPurge
        this.disableHistory = disableHistory
    }

    override fun typeDefaultValue(): String = "naksha.Collection"

    /**
     * If partitions is given, then collection is internally partitioned in the storage and optimised for large quantities of features. The default is no partitions, for around every 10 to 20 million features expected to be stored in a collection, one more partition should be requested, with a minimum of 2 partitions.
     *
     * Note that `lib-psql` will allow values between 2 and 256 and 0, to disable partitioning.
     *
     * Beware that in AWS ever point-to-point connection is generally limited to 5 Gbps. To reach the full limit of a database, the maximum number of partitions is needed, which allow 40 * 5 Gbps = 200 Gbps throughput. The database instances currently have a maximum of 200 Gbps network bandwidth, plus 100 Gbps of EBS throughput, plus a large in-memory cache, which normally allows to satisfy up to 200 Gbps for a short moment of time. As the CPU load is very high in this use-case, it is strongly recommended to use the following encodings:
     *
     * - [GeoEncoding.TWKB_GZIP]
     * - [FeatureEncoding.JBON_GZIP]
     * - [TagsEncoding.JSON] or [TagsEncoding.JSON_GZIP], if GZIP is natively supported.
     *
     * Using these values allows to avoid the usage of server side JavaScript code, when indexing the tags, while at the same time keeping the data in the smallest possible size. Using these encodings with e.g. 32-partitions and [parallel request support enabled][NakshaSessionOptions.parallel] should be able to read and write millions of features per second, with a very small risk of crashing while writing. Recovering from a crashed write is possible, if the write is idempotent, but requires a quick read of the transaction log, which partitions were written and which rolled-back, to repeat the writing of those, not being written successfully. Beware, that this requires a lock on the table or to be the only writer, otherwise conflicts can be encountered, which will make recovery not impossible, but much more complicated.
     *
     * {Create-Only} - after collection creation, modification of this parameter takes no effect.
     */
    var partitions: Int by PARTITIONS

    /**
     * @see hasPartitions
     */
    fun hasPartitions(): Boolean = partitions > 1

    /**
     * The geoIndex to be used for this collection.
     * The possible varues are implementation specific, for lib-psql there are gist, sp-gist and brin with gist being the default.
     * The virtual table naksha~indices should expose the supported values. {Create-Only}
     *
     * {Create-Only} - after collection creation, modification of this parameter takes no effect.
     */
    var geoIndex: String by GEO_INDEX

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
     * The protectionClass defines how collections should be protected.
     * The default is FULL, which means that triggers are installed that prevent any manual change, so changed are only allow through the lib-psql.
     * Next to this, two alternatives are there: SAVE, which installs triggers that automatically apply fixes, so write the history and transaction logs.
     * The disadvantage of these are, that they slow down the processing, but allow to actually do any kind of SQL query.
     * The final ones are NONE, which removes all protecting triggers and allow any kind of manual change, but this can easily break the history and/or transaction logs.
     */
    var protectionClass: String? by PROTECTION_CLASS

    /**
     * Default value of `null` in `feature.type` column.
     *
     * {Create-Only} - after collection creation, modification of this parameter takes no effect.
     */
    var defaultType: String by DEFAULT_TYPE

    /**
     * Default value of `feature.flags`.
     *
     * {Create-Only} - after collection creation, modification of this parameter takes no effect.
     */
    var defaultFlags: Int by DEFAULT_FLAGS

    /**
     * _true_ - disables history of features' modifications.
     */
    var disableHistory: Boolean by DISABLE_HISTORY

    /**
     * If autoPurge is enabled, deleted features are automatically purged and no shadow state is kept available.
     * Note that if [disableHistory] is false, the deleted features will still be around in the history. This mainly effects lib-view.
     */
    var autoPurge: Boolean by AUTO_PURGE

    /**
     * The indices list contains the list of indices to add to the collection.
     * If set to null, default indices are created.
     * The available indices are exposed through the virtual table naksha~indices.
     */
    var indices by INDICES

    /**
     * The maxAge decides about the maximum age of features in the history in days.
     * Note that there is no guarantee that features are deleted exactly after having reached their max-age.
     * However, they are eligible to be deleted at as soon as possible.
     */
    var maxAge: Int64 by MAX_AGE

    /**
     * The quadPartitionSize decides (for the optimal partitioning algorithm) how many features should be placed into each "optimal" tile.
     */
    var quadPartitionSize: Int by QUAD_PARTITION_SIZE

    var estimatedFeatureCount: Int64 by ESTIMATED_FEATURE_COUNT

    var estimatedDeletedFeatures: Int64 by ESTIMATED_DELETED_FEATURES

    companion object {

        /**
         * partition count = 0 -> no partitions only head
         * partition count = 2 -> 2 partitions
         * partition count = n -> n partitions
         */
        const val PARTITION_COUNT_NONE = 1

        /**
         * The constant for the GIST geo-index.
         */
        const val GEO_INDEX_GIST = "gist"

        /**
         * The constant for the SP-GIST geo-index.
         */
        const val GEO_INDEX_SP_GIST = "sp-gist"

        /**
         * Default geo_index - may change over time.
         */
        const val DEFAULT_GEO_INDEX = GEO_INDEX_GIST

        /**
         * The value returned as [estimatedFeatureCount] and [estimatedDeletedFeatures], before the estimation is actually done.
         */
        @JvmStatic
        @JsStatic
        val BEFORE_ESTIMATION = Int64(-1)

        private val PARTITIONS = NotNullProperty<NakshaCollection, Int>(Int::class) { _, _ -> 0 }
        private val GEO_INDEX = NotNullProperty<NakshaCollection, String>(String::class) { _, _ -> DEFAULT_GEO_INDEX }
        private val STORAGE_CLASS = NullableProperty<NakshaCollection, String>(String::class)
        private val PROTECTION_CLASS = NullableProperty<NakshaCollection, String>(String::class)
        private val DEFAULT_TYPE = NotNullProperty<NakshaCollection, String>(String::class) { _, _ -> "Feature" }
        private val DEFAULT_FLAGS = NotNullProperty<NakshaCollection, Flags>(Flags::class) { _, _ -> Flags() }
        private val DISABLE_HISTORY = NotNullProperty<NakshaCollection, Boolean>(Boolean::class) { _, _ -> false }
        private val AUTO_PURGE = NotNullProperty<NakshaCollection, Boolean>(Boolean::class) { _, _ -> false }
        private val INDICES = NotNullProperty<NakshaCollection, StringList>(StringList::class)
        private val MAX_AGE = NotNullProperty<NakshaCollection, Int64>(Int64::class) { _, _ -> Int64(-1) }
        private val QUAD_PARTITION_SIZE = NotNullProperty<NakshaCollection, Int>(Int::class) { _, _ -> 10_485_760 }
        private val ESTIMATED_FEATURE_COUNT = NotNullProperty<NakshaCollection, Int64>(Int64::class) { _, _ -> BEFORE_ESTIMATION }
        private val ESTIMATED_DELETED_FEATURES =  NotNullProperty<NakshaCollection, Int64>(Int64::class) { _, _ -> BEFORE_ESTIMATION }
    }
}