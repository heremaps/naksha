@file:Suppress("OPT_IN_USAGE")

package naksha.model

import naksha.base.Int64
import naksha.base.NotNullProperty
import naksha.base.NullableProperty
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.js.JsStatic
import kotlin.jvm.JvmStatic

@JsExport
open class NakshaCollectionProxy() : NakshaFeatureProxy() {

    @JsName("of")
    constructor(
        id: String,
        partitions: Int,
        geoIndex: String = DEFAULT_GEO_INDEX,
        storageClass: String? = null,
        autoPurge: Boolean,
        disableHistory: Boolean
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
     * If partitions is given, then collection is internally partitioned in the storage and optimised for large quantities of features.
     * The default is one partition (so no partitioning), for around every 10 to 20 million features expected to be stored in a collection one more partition should be requested.
     * Note that lib-psql will only allow values 1, 2, 4, 8, 16 and 32 (given number will be rounded up or down).
     * <br>
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
     * The virtual table naksha~indices should expose the supported varues. {Create-Only}
     * <br>
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
     * true - disables history of features' modifications.
     */
    var disableHistory: Boolean by DISABLE_HISTORY

    /**
     * If autoPurge is enabled, deleted features are automatically purged and no shadow state is kept available.
     * Note that if disableHistory is false, the deleted features will still be around in the history. This mainly effects lib-view.
     */
    var autoPurge: Boolean by AUTO_PURGE

    /**
     * The indices list contains the list of indices to add to the collection.
     * If set to null, default indices are created.
     * The available indices are exposed through the virtual table naksha~indices.
     */
    var indices: IndicesListProxy by INDICES

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
         * partition count = 1 -> no partitions only head
         * partition count = 2 -> head + 2 partitions
         * partition count = n -> had + n partitions
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
         * The constant for the SP-GIST geo-index.
         */
        const val GEO_INDEX_BRIN = "brin"

        /**
         * Default geo_index - may change over time.
         */
        const val DEFAULT_GEO_INDEX = GEO_INDEX_GIST

        @JvmStatic
        @JsStatic
        val BEFORE_ESTIMATION = Int64(1)

        private val PARTITIONS = NotNullProperty<Any, NakshaCollectionProxy, Int>(Int::class) { _, _ -> 1 }
        private val GEO_INDEX = NotNullProperty<Any, NakshaCollectionProxy, String>(String::class) { _, _ -> DEFAULT_GEO_INDEX }
        private val STORAGE_CLASS = NullableProperty<Any, NakshaCollectionProxy, String>(String::class)
        private val PROTECTION_CLASS = NullableProperty<Any, NakshaCollectionProxy, String>(String::class)
        private val DEFAULT_TYPE = NotNullProperty<Any, NakshaCollectionProxy, String>(String::class) { _, _ -> "Feature" }
        private val DEFAULT_FLAGS = NotNullProperty<Any, NakshaCollectionProxy, Int>(Int::class) { _, _ -> Flags.DEFAULT_FLAGS }
        private val DISABLE_HISTORY = NotNullProperty<Any, NakshaCollectionProxy, Boolean>(Boolean::class) { _, _ -> false }
        private val AUTO_PURGE = NotNullProperty<Any, NakshaCollectionProxy, Boolean>(Boolean::class) { _, _ -> false }
        private val INDICES = NotNullProperty<Any, NakshaCollectionProxy, IndicesListProxy>(IndicesListProxy::class)
        private val MAX_AGE = NotNullProperty<Any, NakshaCollectionProxy, Int64>(Int64::class) { _, _ -> Int64(-1) }
        private val QUAD_PARTITION_SIZE = NotNullProperty<Any, NakshaCollectionProxy, Int>(Int::class) { _, _ -> 10_485_760 }
        private val ESTIMATED_FEATURE_COUNT = NotNullProperty<Any, NakshaCollectionProxy, Int64>(Int64::class) { _, _ -> BEFORE_ESTIMATION }
        private val ESTIMATED_DELETED_FEATURES =
            NotNullProperty<Any, NakshaCollectionProxy, Int64>(Int64::class) { _, _ -> BEFORE_ESTIMATION }

    }
}