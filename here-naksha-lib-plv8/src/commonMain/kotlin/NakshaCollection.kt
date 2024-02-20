@file:OptIn(ExperimentalJsExport::class)

package com.here.naksha.lib.plv8

import com.here.naksha.lib.jbon.BigInt64
import com.here.naksha.lib.jbon.Jb
import com.here.naksha.lib.jbon.JbMapObject
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/**
 * A collection feature as defined in the Naksha architecture. This class will instantly read the well known properties.
 */
@JsExport
class NakshaCollection : JbMapObject() {
    private var _partitionHead = false
    private var _pointsOnly = false
    private var _disableHistory = false
    private var _maxAge : BigInt64? = null
    private var _estimatedFeatureCount : BigInt64? = null

    override fun parseHeader(mandatory: Boolean) {
        super.parseHeader(mandatory)
        val map = root()
        while (map.ok()) {
            val key = map.key()
            val value = map.value()
            when (key) {
                NKC_PARTITION -> if (value.isBool()) _partitionHead = value.readBoolean() ?: false
                NKC_POINTS_ONLY -> if (value.isBool()) _pointsOnly = value.readBoolean() ?: false
                NKC_DISABLE_HISTORY -> if (value.isBool()) _disableHistory = value.readBoolean() ?: false
                NKC_MAX_AGE -> if (value.isInt()) _maxAge = value.readInt64()
                NKC_ESTIMATED_FEATURE_COUNT -> if (value.isInt()) _estimatedFeatureCount = value.readInt64()
            }
            map.next()
        }
    }

    fun partitionHead() : Boolean = _partitionHead
    fun pointsOnly() : Boolean = _pointsOnly
    fun disableHistory() : Boolean = _disableHistory
    fun maxAge() : BigInt64 = _maxAge ?: Jb.int64.MAX_VALUE()
    fun estimatedFeatureCount() : BigInt64 = _estimatedFeatureCount ?: Jb.int64.MINUS_ONE()
}