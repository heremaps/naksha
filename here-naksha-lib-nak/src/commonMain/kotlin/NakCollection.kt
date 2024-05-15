@file:Suppress("OPT_IN_USAGE")

package com.here.naksha.lib.base

import com.here.naksha.lib.nak.Flags
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.jvm.JvmStatic

@JsExport
open class NakCollection(vararg args: Any?) : NakFeature(*args) {

    @JsName("NakCollectionWithValues")
    constructor(id: String, partitions: Int, geoIndex: String? = null, storageClass: String? = null, autoPurge: Boolean, disableHistory: Boolean) : this() {
        setId(id)
        setGeoIndex(geoIndex)
        setStorageClass(storageClass)
        setPartitions(partitions)
        setAutoPurge(autoPurge)
        setDisableHistory(disableHistory)
    }

    companion object {
        @JvmStatic
        val klass = object : BaseObjectKlass<NakCollection>() {
            override fun isInstance(o: Any?): Boolean = o is NakCollection

            override fun newInstance(vararg args: Any?): NakCollection = NakCollection(*args)
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
    }

    fun setPartitions(value: Int) = set(PARTITIONS, value)

    fun getPartitions(): Int = toElement(get(PARTITIONS), Klass.intKlass, 1)!!

    fun hasPartitions(): Boolean = getPartitions() > 1

    fun setGeoIndex(value: String?) = set(GEO_INDEX, value)

    fun getGeoIndex(): String = toElement(get(GEO_INDEX), Klass.stringKlass, "gist")!!

    fun setStorageClass(value: String?) = set(STORAGE_CLASS, value)

    fun getStorageClass(): String? = toElement(get(STORAGE_CLASS), Klass.stringKlass)

    fun setProtectionClass(value: String?) = set(PROTECTION_CLASS, value)

    fun getProtectionClass(): String? = toElement(get(PROTECTION_CLASS), Klass.stringKlass)

    fun setDefaultType(value: String) = set(DEFAULT_TYPE, value)

    fun getDefaultType(): String = toElement(get(DEFAULT_TYPE), Klass.stringKlass, "Feature")!!

    fun setDefaultFlags(value: Int) = set(DEFAULT_FLAGS, value)

    fun getDefaultFlags(): Int = toElement(get(DEFAULT_FLAGS), Klass.intKlass, Flags().toCombinedFlags())!!

    fun isDisableHistory(): Boolean = toElement(get(DISABLE_HISTORY), Klass.boolKlass, false)!!

    fun setDisableHistory(value: Boolean) = set(DISABLE_HISTORY, value)

    fun isAutoPurge(): Boolean = toElement(get(AUTO_PURGE), Klass.boolKlass, false)!!

    fun setAutoPurge(value: Boolean) = set(AUTO_PURGE, value)

    fun setIndices(values: BaseList<String>) = set(INDICES, values)

    @Suppress("UNCHECKED_CAST")
    fun getIndices(): BaseList<String> = toElement(get(INDICES), BaseList.klass, BaseList.klass.newInstance())!! as BaseList<String>

    fun setMaxAge(value: Int64) = set(MAX_AGE, value)

    fun getMaxAge(): Int64 = toElement(get(MAX_AGE), Klass.int64Klass, Int64(-1))!!

    fun setQuadPartitionSize(value: Int) = set(QUAD_PARTITION_SIZE, value)

    fun getQuadPartitionSize(): Int = toElement(get(QUAD_PARTITION_SIZE), Klass.intKlass, 10_485_760)!!

    fun getEstimatedFeatureCount(): Int64? = toElement(get(ESTIMATED_FEATURE_COUNT), Klass.int64Klass)

    fun getEstimatedDeletedFeatures(): Int64? = toElement(get(ESTIMATED_DELETED_FEATURES), Klass.int64Klass)

}