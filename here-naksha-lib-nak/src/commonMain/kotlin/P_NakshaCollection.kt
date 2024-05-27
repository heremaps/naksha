@file:Suppress("OPT_IN_USAGE")

package com.here.naksha.lib.base

import com.here.naksha.lib.nak.Flags
import kotlin.js.JsExport
import kotlin.jvm.JvmStatic

@JsExport
open class P_NakshaCollection() : GeoFeature() {

    companion object {
        @JvmStatic
        val klass = object : BaseObjectKlass<P_NakshaCollection>() {
            override fun isInstance(o: Any?): Boolean = o is P_NakshaCollection

            override fun newInstance(vararg args: Any?): P_NakshaCollection = P_NakshaCollection()
        }

        @JvmStatic
        val PARTITIONS = "partitions"

        @JvmStatic
        val GEO_INDEX = "geoIndex"

        @JvmStatic
        val STORAGE_CLASS = "storageClass"

        @JvmStatic
        val PROTECTION_CLASS = "protectionClass"

        @JvmStatic
        val DEFAULT_TYPE = "defaultType"

        @JvmStatic
        val DEFAULT_FLAGS = "defaultFlags"

        @JvmStatic
        val DISABLE_HISTORY = "disableHistory"

        @JvmStatic
        val AUTO_PURGE = "autoPurge"

        @JvmStatic
        val INDICES = "indices"

        @JvmStatic
        val MAX_AGE = "maxAge"

        @JvmStatic
        val QUAD_PARTITION_SIZE = "quadPartitionSize"

        @JvmStatic
        val ESTIMATED_FEATURE_COUNT = "estimatedFeatureCount"

        @JvmStatic
        val ESTIMATED_DELETED_FEATURES = "estimatedDeletedFeatures"

        @JvmStatic
        fun buildCollection(id: String, partitions: Int, geoIndex: String? = null, storageClass: String? = null, autoPurge: Boolean, disableHistory: Boolean): P_NakshaCollection {
            val collection = P_NakshaCollection()
            collection.setId(id)
            collection.setGeoIndex(geoIndex)
            collection.setStorageClass(storageClass)
            collection.setPartitions(partitions)
            collection.setAutoPurge(autoPurge)
            collection.setDisableHistory(disableHistory)
            return collection
        }

    }

    /**
     * If partitions is given, then collection is internally partitioned in the storage and optimised for large quantities of features.
     * The default is one partition (so no partitioning), for around every 10 to 20 million features expected to be stored in a collection one more partition should be requested.
     * Note that lib-psql will only allow values 1, 2, 4, 8, 16 and 32 (given number will be rounded up or down).
     * <br>
     * {Create-Only} - after collection creation, modification of this parameter takes no effect.
     */
    fun setPartitions(value: Int) = set(PARTITIONS, value)

    /**
     * @see setPartitions
     */
    fun getPartitions(): Int = toElement(get(PARTITIONS), Klass.intKlass, 1)!!

    /**
     * @see hasPartitions
     */
    fun hasPartitions(): Boolean = getPartitions() > 1

    /**
     * The geoIndex to be used for this collection.
     * The possible values are implementation specific, for lib-psql there are gist, sp-gist and brin with gist being the default.
     * The virtual table naksha~indices should expose the supported values. {Create-Only}
     * <br>
     * {Create-Only} - after collection creation, modification of this parameter takes no effect.
     */
    fun setGeoIndex(value: String?) = set(GEO_INDEX, value)

    /**
     * @see setGeoIndex
     */
    fun getGeoIndex(): String = toElement(get(GEO_INDEX), Klass.stringKlass, "gist")!!

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
    fun setStorageClass(value: String?) = set(STORAGE_CLASS, value)

    /**
     * @see setStorageClass
     */
    fun getStorageClass(): String? = toElement(get(STORAGE_CLASS), Klass.stringKlass)

    /**
     * The protectionClass defines how collections should be protected.
     * The default is FULL, which means that triggers are installed that prevent any manual change, so changed are only allow through the lib-psql.
     * Next to this, two alternatives are there: SAVE, which installs triggers that automatically apply fixes, so write the history and transaction logs.
     * The disadvantage of these are, that they slow down the processing, but allow to actually do any kind of SQL query.
     * The final ones are NONE, which removes all protecting triggers and allow any kind of manual change, but this can easily break the history and/or transaction logs.
     */
    fun setProtectionClass(value: String?) = set(PROTECTION_CLASS, value)

    /**
     * @see setProtectionClass
     */
    fun getProtectionClass(): String? = toElement(get(PROTECTION_CLASS), Klass.stringKlass)

    /**
     * Default value of `null` in `feature.type` column.
     *
     * {Create-Only} - after collection creation, modification of this parameter takes no effect.
     */
    fun setDefaultType(value: String) = set(DEFAULT_TYPE, value)

    /**
     * @see setDefaultType
     */
    fun getDefaultType(): String = toElement(get(DEFAULT_TYPE), Klass.stringKlass, "Feature")!!

    /**
     * Default value of `feature.flags`.
     *
     * {Create-Only} - after collection creation, modification of this parameter takes no effect.
     */
    fun setDefaultFlags(value: Int) = set(DEFAULT_FLAGS, value)

    /**
     * @see setDefaultFlags
     */
    fun getDefaultFlags(): Int = toElement(get(DEFAULT_FLAGS), Klass.intKlass, Flags().toCombinedFlags())!!

    /**
     * @see setDisableHistory
     */
    fun isDisableHistory(): Boolean = toElement(get(DISABLE_HISTORY), Klass.boolKlass, false)!!

    /**
     * true - disables history of features' modifications.
     */
    fun setDisableHistory(value: Boolean) = set(DISABLE_HISTORY, value)

    /**
     * @see setAutoPurge
     */
    fun isAutoPurge(): Boolean = toElement(get(AUTO_PURGE), Klass.boolKlass, false)!!

    /**
     * If autoPurge is enabled, deleted features are automatically purged and no shadow state is kept available.
     * Note that if disableHistory is false, the deleted features will still be around in the history. This mainly effects lib-view.
     */
    fun setAutoPurge(value: Boolean) = set(AUTO_PURGE, value)

    /**
     * The indices list contains the list of indices to add to the collection.
     * If set to null, default indices are created.
     * The available indices are exposed through the virtual table naksha~indices.
     */
    fun setIndices(values: BaseList<String>) = set(INDICES, values)

    /**
     * @see setIndices
     */
    @Suppress("UNCHECKED_CAST")
    fun getIndices(): BaseList<String> = toElement(get(INDICES), BaseList.klass, BaseList.klass.newInstance())!! as BaseList<String>

    /**
     * The maxAge decides about the maximum age of features in the history in days.
     * Note that there is no guarantee that features are deleted exactly after having reached their max-age.
     * However, they are eligible to be deleted at as soon as possible.
     */
    fun setMaxAge(value: Int64) = set(MAX_AGE, value)

    /**
     * @see setMaxAge
     */
    fun getMaxAge(): Int64 = toElement(get(MAX_AGE), Klass.int64Klass, Int64(-1))!!

    /**
     * The quadPartitionSize decides (for the optimal partitioning algorithm) how many features should be placed into each "optimal" tile.
     */
    fun setQuadPartitionSize(value: Int) = set(QUAD_PARTITION_SIZE, value)

    /**
     * @see setQuadPartitionSize
     */
    fun getQuadPartitionSize(): Int = toElement(get(QUAD_PARTITION_SIZE), Klass.intKlass, 10_485_760)!!

    fun getEstimatedFeatureCount(): Int64? = toElement(get(ESTIMATED_FEATURE_COUNT), Klass.int64Klass)

    fun getEstimatedDeletedFeatures(): Int64? = toElement(get(ESTIMATED_DELETED_FEATURES), Klass.int64Klass)

}