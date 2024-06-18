@file:OptIn(ExperimentalJsExport::class)

package naksha.plv8

import naksha.base.Int64
import naksha.base.Platform
import naksha.jbon.IDictManager
import naksha.jbon.JbMapFeature
import naksha.model.NakshaCollectionProxy
import naksha.model.NakshaCollectionProxy.Companion.PARTITION_COUNT_NONE
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/**
 * A collection feature as defined in the Naksha architecture. This class will instantly read the well known properties.
 */
@JsExport
class NakshaCollection(dictManager: IDictManager) : JbMapFeature(dictManager) {
    /**
     * The number of partitions. We use partitioning for tables that are expected to store more than
     * ten million features. With eight partitions we can split 10 million features into partitions
     * of each 1.25 million, 100 million into 12.5 million per partition and for the supported maximum
     * of 1 billion features, each partition holds 125 million features.
     *
     * This value must be a value of 2^n with n between 1 and 8 (2, 4, 8, 16, 32, 64, 128).
     */
    private var _partitionCount = PARTITION_COUNT_NONE
    private var _geoIndex: String? = null
    private var _disableHistory = false
    private var _autoPurge = false
    private var _maxAge: Int64? = null
    private var _estimatedFeatureCount: Int64? = null
    private var _estimatedDeletedCount: Int64? = null
    private var _storageClass: String? = null

    override fun clear(): NakshaCollection {
        super.clear()
        _partitionCount = PARTITION_COUNT_NONE
        _geoIndex = null
        _disableHistory = false
        _autoPurge = false
        _maxAge = null
        _estimatedFeatureCount = null
        _estimatedDeletedCount = null
        _storageClass = null
        return this
    }

    override fun parseHeader() {
        super.parseHeader()
        val map = root()
        while (map.next() && map.ok()) {
            val key = map.key()
            val value = map.value()
            when (key) {
                NKC_PARTITION_COUNT -> if (value.isInt()) _partitionCount = value.decodeInt32()
                NKC_GEO_INDEX -> if (value.isString()) _geoIndex = value.decodeString()
                NKC_DISABLE_HISTORY -> if (value.isBool()) _disableHistory = value.readBoolean() ?: false
                NKC_AUTO_PURGE -> if (value.isBool()) _autoPurge = value.readBoolean() ?: false
                NKC_MAX_AGE -> if (value.isInt()) _maxAge = value.decodeInt64()
                NKC_ESTIMATED_FEATURE_COUNT -> if (value.isInt()) _estimatedFeatureCount = value.decodeInt64()
                NKC_STORAGE_CLASS -> if (value.isString()) _storageClass = value.decodeString()
            }
        }
    }

    fun partitionCount(): Int = _partitionCount
    fun geoIndex(): String = _geoIndex ?: NakshaCollectionProxy.DEFAULT_GEO_INDEX
    fun disableHistory(): Boolean = _disableHistory
    fun autoPurge(): Boolean = _autoPurge
    fun maxAge(): Int64 = _maxAge ?: Platform.INT64_MAX_VALUE
    fun estimatedFeatureCount(): Int64 = _estimatedFeatureCount ?: NakshaCollectionProxy.BEFORE_ESTIMATION
    fun storageClass(): String = _storageClass ?: Static.SC_DEFAULT
}