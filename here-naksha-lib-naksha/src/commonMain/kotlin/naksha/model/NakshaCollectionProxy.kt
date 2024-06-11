@file:Suppress("OPT_IN_USAGE")

package naksha.model

import naksha.model.Flags
import naksha.base.Int64
import naksha.base.NotNullProperty
import naksha.base.NullableProperty
import kotlin.js.JsExport
import kotlin.jvm.JvmStatic

@JsExport
open class NakshaCollectionProxy : com.here.naksha.lib.base.GeoFeature() {

    /**
     * If partitions is given, then collection is internally partitioned in the storage and optimised for large quantities of features.
     * The default is one partition (so no partitioning), for around every 10 to 20 million features expected to be stored in a collection one more partition should be requested.
     * Note that lib-psql will only allow values 1, 2, 4, 8, 16 and 32 (given number will be rounded up or down).
     * <br>
     * {Create-Only} - after collection creation, modification of this parameter takes no effect.
     */
    var partitions: Int by NakshaCollectionProxy.Companion.PARTITIONS

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
    var geoIndex: String? by NakshaCollectionProxy.Companion.GEO_INDEX

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
    var storageClass: String? by NakshaCollectionProxy.Companion.STORAGE_CLASS

    /**
     * The protectionClass defines how collections should be protected.
     * The default is FULL, which means that triggers are installed that prevent any manual change, so changed are only allow through the lib-psql.
     * Next to this, two alternatives are there: SAVE, which installs triggers that automatically apply fixes, so write the history and transaction logs.
     * The disadvantage of these are, that they slow down the processing, but allow to actually do any kind of SQL query.
     * The final ones are NONE, which removes all protecting triggers and allow any kind of manual change, but this can easily break the history and/or transaction logs.
     */
    var protectionClass: String? by NakshaCollectionProxy.Companion.PROTECTION_CLASS

    /**
     * Default value of `null` in `feature.type` column.
     *
     * {Create-Only} - after collection creation, modification of this parameter takes no effect.
     */
    var defaultType: String by NakshaCollectionProxy.Companion.DEFAULT_TYPE

    /**
     * Default value of `feature.flags`.
     *
     * {Create-Only} - after collection creation, modification of this parameter takes no effect.
     */
    var defaultFlags: Int by NakshaCollectionProxy.Companion.DEFAULT_FLAGS

    /**
     * true - disables history of features' modifications.
     */
    var disableHistory: Boolean by NakshaCollectionProxy.Companion.DISABLE_HISTORY

    /**
     * If autoPurge is enabled, deleted features are automatically purged and no shadow state is kept available.
     * Note that if disableHistory is false, the deleted features will still be around in the history. This mainly effects lib-view.
     */
    var autoPurge: Boolean by NakshaCollectionProxy.Companion.AUTO_PURGE

    /**
     * The indices list contains the list of indices to add to the collection.
     * If set to null, default indices are created.
     * The available indices are exposed through the virtual table naksha~indices.
     */
    var indices: naksha.model.IndicesListProxy by NakshaCollectionProxy.Companion.INDICES

    /**
     * The maxAge decides about the maximum age of features in the history in days.
     * Note that there is no guarantee that features are deleted exactly after having reached their max-age.
     * However, they are eligible to be deleted at as soon as possible.
     */
    var maxAge: Int64 by NakshaCollectionProxy.Companion.MAX_AGE

    /**
     * The quadPartitionSize decides (for the optimal partitioning algorithm) how many features should be placed into each "optimal" tile.
     */
    var quadPartitionSize: Int by NakshaCollectionProxy.Companion.QUAD_PARTITION_SIZE

    var estimatedFeatureCount: Int64? by NakshaCollectionProxy.Companion.ESTIMATED_FEATURE_COUNT

    var estimatedDeletedFeatures: Int64? by NakshaCollectionProxy.Companion.ESTIMATED_DELETED_FEATURES

    companion object {
        private val PARTITIONS = NotNullProperty<Any, com.here.naksha.lib.base.GeoFeature, Int>(Int::class, 1)
        private val GEO_INDEX = NullableProperty<Any, com.here.naksha.lib.base.GeoFeature, String>(String::class, defaultValue = "gist")
        private val STORAGE_CLASS = NullableProperty<Any, com.here.naksha.lib.base.GeoFeature, String>(String::class)
        private val PROTECTION_CLASS = NullableProperty<Any, com.here.naksha.lib.base.GeoFeature, String>(String::class)
        private val DEFAULT_TYPE = NotNullProperty<Any, com.here.naksha.lib.base.GeoFeature, String>(String::class, defaultValue = "Feature")
        private val DEFAULT_FLAGS =
            NotNullProperty<Any, com.here.naksha.lib.base.GeoFeature, Int>(Int::class, defaultValue = naksha.model.Flags.DEFAULT_FLAGS)
        private val DISABLE_HISTORY = NotNullProperty<Any, com.here.naksha.lib.base.GeoFeature, Boolean>(Boolean::class, false)
        private val AUTO_PURGE = NotNullProperty<Any, com.here.naksha.lib.base.GeoFeature, Boolean>(Boolean::class, false)
        private val INDICES = NotNullProperty<Any, com.here.naksha.lib.base.GeoFeature, naksha.model.IndicesListProxy>(
            naksha.model.IndicesListProxy::class)
        private val MAX_AGE = NotNullProperty<Any, com.here.naksha.lib.base.GeoFeature, Int64>(Int64::class, defaultValue = Int64(-1))
        private val QUAD_PARTITION_SIZE = NotNullProperty<Any, com.here.naksha.lib.base.GeoFeature, Int>(Int::class, defaultValue = 10_485_760)
        private val ESTIMATED_FEATURE_COUNT = NullableProperty<Any, com.here.naksha.lib.base.GeoFeature, Int64>(Int64::class)
        private val ESTIMATED_DELETED_FEATURES = NullableProperty<Any, com.here.naksha.lib.base.GeoFeature, Int64>(Int64::class)


        @JvmStatic
        fun buildCollection(
            id: String,
            partitions: Int,
            geoIndex: String? = null,
            storageClass: String? = null,
            autoPurge: Boolean,
            disableHistory: Boolean
        ): NakshaCollectionProxy {
            val collection = NakshaCollectionProxy()
            collection.id = id
            collection.geoIndex = geoIndex
            collection.storageClass = storageClass
            collection.partitions = partitions
            collection.autoPurge = autoPurge
            collection.disableHistory = disableHistory

            return collection
        }
    }
}