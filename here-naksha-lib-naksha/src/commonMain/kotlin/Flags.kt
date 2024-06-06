@file:OptIn(ExperimentalJsExport::class)

package com.here.naksha.lib.naksha

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlin.math.pow

/**
 * Keeps flags on different bits of integer
 * GE - geometry encoding - bits: 0-4
 * FE - feature encoding - bits: 5-8
 * TE - tags encoding - bits: 9-12
 * AE - action - bits: 13-14
 * RE - reserved for future - bits: 15-31
 *
 *     15-31 RE    AE   TE    FE    GE
 * [00000000000000[00][0000][0000][0000]
 */
@JsExport
object Flags {

    val GEOMETRY_FLAG_ENCODER = FlagsReader(4, 0)
    val FEATURE_FLAG_ENCODER = FlagsReader(4, 4)
    val TAGS_FLAG_ENCODER = FlagsReader(4, 8)
    val ACTION_FLAG_ENCODER = FlagsReader(2, 12)

    const val GEO_TYPE_NULL: Int = 0
    const val GEO_TYPE_WKB: Int = 1
    const val GEO_TYPE_EWKB: Int = 2
    const val GEO_TYPE_TWKB: Int = 3

    const val FEATURE_ENCODING_JBON = 1
    const val FEATURE_ENCODING_JBON_GZIP = 2
    const val FEATURE_ENCODING_JSON = 3
    const val FEATURE_ENCODING_JSON_GZIP = 4

    const val DEFAULT_FEATURE_ENCODING = FEATURE_ENCODING_JBON
    const val DEFAULT_GEOMETRY_ENCODING = GEO_TYPE_NULL

    fun readGeometryEncoding(flags: Int) = GEOMETRY_FLAG_ENCODER.read(flags)

    /**
     * Returns new flags value with geometry encoding partset to new value.
     * @param flags - current flags value
     * @param ge new geometry encoding value
     * @return flags with new value included.
     */
    fun encodeGeometryFlag(flags: Int, ge: Int) = GEOMETRY_FLAG_ENCODER.encodeNew(flags, ge)

    fun readFeatureEncoding(flags: Int) = FEATURE_FLAG_ENCODER.read(flags)

    /**
     * Returns new flags value with feature encoding part set to new value.
     * @param flags - current flags value
     * @param fe - new feature encoding value
     * @return flags with new value included.
     */
    fun encodeFeatureFlag(flags: Int, fe: Int) = FEATURE_FLAG_ENCODER.encodeNew(flags, fe)

    fun readTagsEncoding(flags: Int) = TAGS_FLAG_ENCODER.read(flags)

    /**
     * Returns new flags value with tags encoding part set to new value.
     * @param flags - current flags value
     * @param te - new tags encoding value
     * @return flags with new value included.
     */
    fun encodeTagsFlag(flags: Int, te: Int) = TAGS_FLAG_ENCODER.encodeNew(flags, te)

    fun readAction(flags: Int) = ACTION_FLAG_ENCODER.read(flags)

    /**
     * Returns new flags value with `action` part set to new value.
     * @param flags - current flags value
     * @param action - new tags encoding value
     * @return flags with new value included.
     */
    fun encodeAction(flags: Int, action: Int) = ACTION_FLAG_ENCODER.encodeNew(flags, action)

    /**
     * Returns new flags with feature encoding set to _GZIP.
     */
    fun forceGzipOnFeatureEncoding(flags: Int): Int {
        return when (readFeatureEncoding(flags)) {
            FEATURE_ENCODING_JSON -> encodeFeatureFlag(flags, FEATURE_ENCODING_JSON_GZIP)
            FEATURE_ENCODING_JBON -> encodeFeatureFlag(flags, FEATURE_ENCODING_JBON_GZIP)
            else -> flags
        }
    }

    /**
     * Returns new flags with feature encoding without _GZIP.
     */
    fun turnOffGzipOnFeatureEncoding(flags: Int): Int {
        return when (readFeatureEncoding(flags)) {
            FEATURE_ENCODING_JSON_GZIP -> encodeFeatureFlag(flags, FEATURE_ENCODING_JSON)
            FEATURE_ENCODING_JBON_GZIP -> encodeFeatureFlag(flags, FEATURE_ENCODING_JBON)
            else -> flags
        }
    }

    fun isFeatureEncodedWithGZip(flags: Int): Boolean {
        val featureEncoding = readFeatureEncoding(flags)
        return featureEncoding == FEATURE_ENCODING_JSON_GZIP || featureEncoding == FEATURE_ENCODING_JBON_GZIP
    }

    data class FlagsReader(
        val sizeInBits: Int,
        val shift: Int
    ) {
        private val maxFlagValue = (2.toDouble().pow(sizeInBits) - 1).toInt()
        private val minFlagValue = 0
        private val mask = maxFlagValue.shl(shift)

        fun read(flags: Int): Int = flags.and(mask).shr(shift)

        fun encodeNew(flags: Int, newFlagValue: Int): Int {
            check(newFlagValue in minFlagValue..maxFlagValue)
            // clear space for new flag
            val flagsWithClearedSpace = mask.inv().and(flags)
            // position flag to it's bits
            val flagPositioned = newFlagValue.shl(shift)
            // combine and return old flags with new flag
            return flagsWithClearedSpace.or(flagPositioned)
        }
    }
}