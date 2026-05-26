@file:Suppress("NOTHING_TO_INLINE", "unused")

package naksha.model

import naksha.model.FlagsBits.FlagsBitsCompanion.ACTION_CLEAR
import naksha.model.FlagsBits.FlagsBitsCompanion.ACTION_MASK
import naksha.model.FlagsBits.FlagsBitsCompanion.OP_CLEAR
import naksha.model.FlagsBits.FlagsBitsCompanion.OP_MASK
import naksha.model.FlagsBits.FlagsBitsCompanion.FEATURE_CLEAR
import naksha.model.FlagsBits.FlagsBitsCompanion.FEATURE_GZIP_BIT
import naksha.model.FlagsBits.FlagsBitsCompanion.FEATURE_MASK
import naksha.model.FlagsBits.FlagsBitsCompanion.GEO_CLEAR
import naksha.model.FlagsBits.FlagsBitsCompanion.GEO_GZIP_BIT
import naksha.model.FlagsBits.FlagsBitsCompanion.GEO_MASK
import naksha.model.FlagsBits.FlagsBitsCompanion.TAGS_CLEAR
import naksha.model.FlagsBits.FlagsBitsCompanion.TAGS_GZIP_BIT
import naksha.model.FlagsBits.FlagsBitsCompanion.TAGS_MASK

/**
 * Collection of bits making the `flags`. The flags store the encoding in the storage, so how the binaries are encoded:
 * ```
 *      RESERVED       AC    OP    TE     FE    GE
 * [0000-0000-0000-00][00]-[0000][0000]-[0000][0000]
 * ```
 * - GE: geometry encoding - bits: 0-3 (4-bit)
 * - FE: feature encoding - bits: 4-7 (4-bit)
 * - TE: tags encoding - bits: 8-11 (4-bit)
 * - OP: operation - bits: 12-15 (4-bit)
 * - AC: action - bits: 16-17 (2-bit)
 * - RESERVED: reserved - bit: 18-31 (14-bit)
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
 * @param geoEncoding the geometry encoding.
 * @param featureEncoding the feature encoding.
 * @param tagsEncoding the tags encoding.
 * @return the flags binary.
 * @since 3.0.0
 */
inline fun Flags(geoEncoding: Int, featureEncoding: Int, tagsEncoding: Int): Flags =
    geoEncoding or featureEncoding or tagsEncoding

/**
 * Decodes the geometry encoding from flags.
 * @return the geometry encoding from flags.
 * @since 3.0.0
 */
inline fun Flags.geoEncoding(): Int = this and GEO_MASK

/**
 * Updates the geometry encoding in the given flags.
 * @param encoding the encoding to set.
 * @return the new flags.
 * @since 3.0.0
 */
inline fun Flags.withGeoEncoding(encoding: Int): Flags = (this and GEO_CLEAR) or (encoding and GEO_MASK)

/**
 * Tests if the geometry is GZIP compressed.
 * @return _true_ when the geometry is GZIP compressed; _false_ otherwise.
 * @since 3.0.0
 */
inline fun Flags.geoGzip(): Boolean = (this and GEO_GZIP_BIT) == GEO_GZIP_BIT

/**
 * Enable GZIP compression for geometry.
 * @return the new flags.
 * @since 3.0.0
 */
inline fun Flags.geoGzipOn(): Flags = this or GEO_GZIP_BIT

/**
 * Disable GZIP compression for geometry.
 * @return the new flags.
 * @since 3.0.0
 */
inline fun Flags.geoGzipOff(): Flags = this and GEO_GZIP_BIT.inv()

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

/**
 * Returns the tags encoding.
 * @return the tags encoding.
 * @since 3.0.0
 */
inline fun Flags.tagsEncoding(): Int = this and TAGS_MASK

/**
 * Updates the tags encoding in the given flags.
 * @param encoding the encoding to set.
 * @return the new flags.
 * @since 3.0.0
 */
inline fun Flags.withTagsEncoding(encoding: Int): Flags = (this and TAGS_CLEAR) or (encoding and TAGS_MASK)

/**
 * Tests if the tags is GZIP compressed.
 * @return _true_ when the tags is GZIP compressed; _false_ otherwise.
 * @since 3.0.0
 */
inline fun Flags.tagsGzip(): Boolean = (this and TAGS_GZIP_BIT) == TAGS_GZIP_BIT

/**
 * Enable GZIP compression for the tags.
 * @return the new flags.
 * @since 3.0.0
 */
inline fun Flags.tagsGzipOn(): Flags = this or TAGS_GZIP_BIT

/**
 * Disable GZIP compression for the tags.
 * @return the new flags.
 * @since 3.0.0
 */
inline fun Flags.tagsGzipOff(): Flags = this and TAGS_GZIP_BIT.inv()

/**
 * Returns the operation encoding (raw bit value). The operation bits are reserved in the flags for future use.
 * @return the operation encoding.
 * @since 3.0.0
 */
inline fun Flags.operation(): Int = this and OP_MASK

/**
 * Updates the operation encoding.
 * @param value the encoding to set.
 * @return the new flags.
 * @since 3.0.0
 */
inline fun Flags.withOperation(value: Int): Flags = (this and OP_CLEAR) or (value and OP_MASK)

/**
 * Returns the action encoding.
 * @return the action encoding.
 * @since 3.0.0
 */
inline fun Flags.action(): Int = this and ACTION_MASK

/**
 * Returns the action-enumeration value from the encoding.
 * @return the action-enumeration value from the encoding.
 * @since 3.0.0
 */
inline fun Flags.actionEnum(): Action = Action.fromValue(action())

/**
 * Updates the action encoding.
 * @param value the encoding to set.
 * @return the new flags.
 * @since 3.0.0
 */
inline fun Flags.withAction(value: Int): Flags = (this and ACTION_CLEAR) or (value and ACTION_MASK)

/**
 * Updates the action encoding.
 * @param action the action-enumeration value to set.
 * @return the new flags.
 * @since 3.0.0
 */
inline fun Flags.withAction(action: Action): Flags = (this and ACTION_CLEAR) or (action.intValue)


