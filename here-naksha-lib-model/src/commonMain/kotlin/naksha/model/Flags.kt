@file:Suppress("NOTHING_TO_INLINE", "unused")

package naksha.model

import naksha.model.FlagsBits.FlagsBitsCompanion.FEATURE_CLEAR
import naksha.model.FlagsBits.FlagsBitsCompanion.FEATURE_GZIP_BIT
import naksha.model.FlagsBits.FlagsBitsCompanion.FEATURE_MASK

/**
 * Collection of bits making the `flags`. The flags only encode the feature serialization format now:
 * ```
 *      RESERVED            FE
 * [0000-0000-0000-0000-0000-0000][0000]-[0000]
 * ```
 * - FE: feature encoding - bits: 4-7 (4-bit)
 * - RESERVED: reserved - all other bits
 *
 * Geometries are always stored as raw `TWKB`, tags as `JBON_GZIP`, and the [action][Action] lives in the lower two bits of the [Version.txn]. Only the feature encoding is configurable here.
 * @since 3.0.0
 */
typealias Flags = Int

/**
 * Create flags from an integer value or using the defaults.
 * @param flags the integer value of flags.
 * @return the give value, cast to [Flags] alias.
 * @since 3.0.0
 */
inline fun Flags(flags: Int = 0): Flags = flags

/**
 * Create new flags from the given encoding values.
 *
 * The `geoEncoding` and `tagsEncoding` parameters are accepted for source compatibility but ignored: geometries are always stored as raw `TWKB` and tags as `JBON_GZIP`.
 * @param geoEncoding ignored; always TWKB.
 * @param featureEncoding the feature encoding.
 * @param tagsEncoding ignored; always JBON_GZIP.
 * @return the flags binary.
 * @since 3.0.0
 */
@Suppress("UNUSED_PARAMETER")
inline fun Flags(geoEncoding: Int, featureEncoding: Int, tagsEncoding: Int): Flags = featureEncoding

/**
 * Returns the feature encoding.
 * @return the feature encoding.
 * @since 3.0.0
 */
inline fun Flags.featureEncoding(): Int = this and FEATURE_MASK

/**
 * Updates the feature encoding in the given flags.
 * @param encoding the encoding to set.
 * @return the new flags.
 * @since 3.0.0
 */
inline fun Flags.withFeatureEncoding(encoding: Int): Flags = (this and FEATURE_CLEAR) or (encoding and FEATURE_MASK)

/**
 * Tests if the feature is GZIP compressed.
 * @return _true_ when the feature is GZIP compressed; _false_ otherwise.
 * @since 3.0.0
 */
inline fun Flags.featureGzip(): Boolean = (this and FEATURE_GZIP_BIT) == FEATURE_GZIP_BIT

/**
 * Enable GZIP compression for the feature.
 * @return the new flags.
 * @since 3.0.0
 */
inline fun Flags.featureGzipOn(): Flags = this or FEATURE_GZIP_BIT

/**
 * Disable GZIP compression for the feature.
 * @return the new flags.
 * @since 3.0.0
 */
inline fun Flags.featureGzipOff(): Flags = this and FEATURE_GZIP_BIT.inv()
