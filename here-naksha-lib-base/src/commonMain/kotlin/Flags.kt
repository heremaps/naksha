@file:OptIn(ExperimentalJsExport::class)

package com.here.naksha.lib.nak

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/**
 * Keeps flags on different bits of integer
 * GE - geometry encoding - bits: 0-5
 * FE - feature encoding - bits: 6-11
 * RE - reserved for future - bits: 12-31
 *
 *       12-31 RE        6-11 FE  0-5 GE
 * [00000000000000000000][000000][00000]
 */
@JsExport
class Flags(initialFlags: Int? = null) {
    private var featureEncoding = initialFlags?.let { (it and 0b0000000000000000000_111111_000000).shr(6) } ?: DEFAULT_FEATURE_ENCODING
    private var geometryEncoding = initialFlags?.let { it and 0b0000000000000000000_000000_111111 } ?: DEFAULT_GEOMETRY_ENCODING
    private var reserved = initialFlags?.ushr(12) ?: 0

    fun getReserved() = reserved

    fun getGeometryEncoding() = geometryEncoding

    fun getFeatureEncoding() = featureEncoding

    fun setGeometryEncoding(ge: Int) {
        check(ge in 0..63) // 2^6-1
        geometryEncoding = ge
    }

    /**
     * Temporarily only internal, in future we might allow to set encoding.
     */
    internal fun setFeatureEncoding(fe: Int) {
        check(fe in 0..63) // 2^6-1
        featureEncoding = fe
    }

    fun forceGzipOnFeatureEncoding() {
        featureEncoding = when (featureEncoding) {
                FEATURE_ENCODING_JSON -> FEATURE_ENCODING_JSON_GZIP
                FEATURE_ENCODING_JBON -> FEATURE_ENCODING_JBON_GZIP
                else -> featureEncoding
            }
    }

    fun turnOffGzipOnFeatureEncoding() {
        featureEncoding = when (featureEncoding) {
            FEATURE_ENCODING_JSON_GZIP -> FEATURE_ENCODING_JSON
            FEATURE_ENCODING_JBON_GZIP -> FEATURE_ENCODING_JBON
            else -> featureEncoding
        }
    }

    fun isFeatureEncodedWithGZip(): Boolean {
        return featureEncoding == FEATURE_ENCODING_JSON_GZIP || featureEncoding == FEATURE_ENCODING_JBON_GZIP
    }

    fun toCombinedFlags(): Int = featureEncoding.shl(6) or geometryEncoding

    companion object {
        const val GEO_TYPE_NULL : Int = 0
        const val GEO_TYPE_WKB : Int = 1
        const val GEO_TYPE_EWKB : Int = 2
        const val GEO_TYPE_TWKB : Int = 3

        const val FEATURE_ENCODING_JBON = 1
        const val FEATURE_ENCODING_JBON_GZIP = 2
        const val FEATURE_ENCODING_JSON = 3
        const val FEATURE_ENCODING_JSON_GZIP = 4

        const val DEFAULT_FEATURE_ENCODING = FEATURE_ENCODING_JBON
        const val DEFAULT_GEOMETRY_ENCODING = GEO_TYPE_NULL
    }
}