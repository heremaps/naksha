@file:OptIn(ExperimentalJsExport::class)

package com.here.naksha.lib.plv8

import com.here.naksha.lib.jbon.BigInt64
import com.here.naksha.lib.jbon.IDictManager
import com.here.naksha.lib.jbon.Jb
import com.here.naksha.lib.jbon.JbMapFeature
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
    private var _maxAge: BigInt64? = null
    private var _estimatedFeatureCount: BigInt64? = null
    private var _estimatedDeletedCount: BigInt64? = null
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
                NKC_PARTITION_COUNT -> if (value.isInt()) _partitionCount = value.readInt32()
                NKC_GEO_INDEX -> if (value.isString()) _geoIndex = value.readString()
                NKC_DISABLE_HISTORY -> if (value.isBool()) _disableHistory = value.readBoolean() ?: false
                NKC_AUTO_PURGE -> if (value.isBool()) _autoPurge = value.readBoolean() ?: false
                NKC_MAX_AGE -> if (value.isInt()) _maxAge = value.readInt64()
                NKC_ESTIMATED_FEATURE_COUNT -> if (value.isInt()) _estimatedFeatureCount = value.readInt64()
                NKC_STORAGE_CLASS -> if (value.isString()) _storageClass = value.readString()
            }
        }
    }

    fun partitionCount(): Int = _partitionCount
    fun geoIndex(): String = _geoIndex ?: Static.GEO_INDEX_DEFAULT
    fun disableHistory(): Boolean = _disableHistory
    fun autoPurge(): Boolean = _autoPurge
    fun maxAge(): BigInt64 = _maxAge ?: Jb.int64.MAX_VALUE()
    fun estimatedFeatureCount(): BigInt64 = _estimatedFeatureCount ?: Jb.int64.MINUS_ONE()
    fun storageClass(): String = _storageClass ?: Static.SC_DEFAULT
}