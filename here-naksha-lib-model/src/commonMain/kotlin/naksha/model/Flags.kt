@file:Suppress("NOTHING_TO_INLINE", "unused")

package naksha.model

import naksha.model.FlagsBits.FlagsBitsCompanion.ACTION_CLEAR
import naksha.model.FlagsBits.FlagsBitsCompanion.ACTION_MASK
import naksha.model.FlagsBits.FlagsBitsCompanion.FEATURE_CLEAR
import naksha.model.FlagsBits.FlagsBitsCompanion.FEATURE_GZIP_BIT
import naksha.model.FlagsBits.FlagsBitsCompanion.FEATURE_MASK
import naksha.model.FlagsBits.FlagsBitsCompanion.GEO_CLEAR
import naksha.model.FlagsBits.FlagsBitsCompanion.GEO_GZIP_BIT
import naksha.model.FlagsBits.FlagsBitsCompanion.GEO_MASK
import naksha.model.FlagsBits.FlagsBitsCompanion.SN_CLEAR
import naksha.model.FlagsBits.FlagsBitsCompanion.SN_MASK
import naksha.model.FlagsBits.FlagsBitsCompanion.SN_OFF
import naksha.model.FlagsBits.FlagsBitsCompanion.SN_ON
import naksha.model.FlagsBits.FlagsBitsCompanion.TAGS_CLEAR
import naksha.model.FlagsBits.FlagsBitsCompanion.TAGS_GZIP_BIT
import naksha.model.FlagsBits.FlagsBitsCompanion.TAGS_MASK

/**
 * Type alias for the flags encoding in the storage, it stores how the binaries are encoded:
 * ```
 *       Reserved          SN   AE   TE    FE    GE
 * [0000-0000-0000-0000-0][0][00][0000]-[0000][0000]
 * ```
 * - GE: geometry encoding - bits: 0-3
 * - FE: feature encoding - bits: 4-7
 * - TE: tags encoding - bits: 8-11
 * - AE: action - bits: 12+13
 * - SN: storage-number - bits: 14
 * - Reserved: reserved - bit: 15-31
 */
typealias Flags = Int

/**
 * Create flags from an integer value or using the defaults.
 * @param flags the integer value of flags.
 * @return the give value, cast to [Flags] alias.
 */
inline fun Flags(flags: Int = 0): Flags = flags

/**
 * Create new flags from the given encoding values.
 * @param geoEncoding the geometry encoding.
 * @param featureEncoding the feature encoding.
 * @param tagsEncoding the tags encoding.
 * @param action the action.
 * @return the flags binary.
 */
inline fun Flags(geoEncoding: Int, featureEncoding: Int, tagsEncoding: Int, action: Int): Flags =
    geoEncoding or featureEncoding or tagsEncoding or action

/**
 * Decodes the geometry encoding from flags.
 * @return the geometry encoding from flags.
 */
inline fun Flags.geoEncoding(): Int = this and GEO_MASK

/**
 * Updates the geometry encoding in the given flags.
 * @param encoding the encoding to set.
 * @return the new flags.
 */
inline fun Flags.geoEncoding(encoding: Int): Flags = (this and GEO_CLEAR) or (encoding and GEO_MASK)

/**
 * Tests if the geometry is GZIP compressed.
 * @return _true_ when the geometry is GZIP compressed; _false_ otherwise.
 */
inline fun Flags.geoGzip(): Boolean = (this and GEO_GZIP_BIT) == GEO_GZIP_BIT

/**
 * Enable GZIP compression for geometry.
 * @return the new flags.
 */
inline fun Flags.geoGzipOn(): Flags = this or GEO_GZIP_BIT

/**
 * Disable GZIP compression for geometry.
 * @return the new flags.
 */
inline fun Flags.geoGzipOff(): Flags = this and GEO_GZIP_BIT.inv()

/**
 * Returns the feature encoding.
 * @return the feature encoding.
 */
inline fun Flags.featureEncoding(): Int = this and FEATURE_MASK

/**
 * Updates the feature encoding in the given flags.
 * @param encoding the encoding to set.
 * @return the new flags.
 */
inline fun Flags.featureEncoding(encoding: Int): Flags = (this and FEATURE_CLEAR) or (encoding and FEATURE_MASK)

/**
 * Tests if the feature is GZIP compressed.
 * @return _true_ when the feature is GZIP compressed; _false_ otherwise.
 */
inline fun Flags.featureGzip(): Boolean = (this and FEATURE_GZIP_BIT) == FEATURE_GZIP_BIT

/**
 * Enable GZIP compression for the feature.
 * @return the new flags.
 */
inline fun Flags.featureGzipOn(): Flags = this or FEATURE_GZIP_BIT

/**
 * Disable GZIP compression for the feature.
 * @return the new flags.
 */
inline fun Flags.featureGzipOff(): Flags = this and FEATURE_GZIP_BIT.inv()

/**
 * Returns the tags encoding.
 * @return the tags encoding.
 */
inline fun Flags.tagsEncoding(): Int = this and TAGS_MASK

/**
 * Updates the tags encoding in the given flags.
 * @param encoding the encoding to set.
 * @return the new flags.
 */
inline fun Flags.tagsEncoding(encoding: Int): Flags = (this and TAGS_CLEAR) or (encoding and TAGS_MASK)

/**
 * Tests if the tags is GZIP compressed.
 * @return _true_ when the tags is GZIP compressed; _false_ otherwise.
 */
inline fun Flags.tagsGzip(): Boolean = (this and TAGS_GZIP_BIT) == TAGS_GZIP_BIT

/**
 * Enable GZIP compression for the tags.
 * @return the new flags.
 */
inline fun Flags.tagsGzipOn(): Flags = this or TAGS_GZIP_BIT

/**
 * Disable GZIP compression for the tags.
 * @return the new flags.
 */
inline fun Flags.tagsGzipOff(): Flags = this and TAGS_GZIP_BIT.inv()

/**
 * Returns the action encoding.
 * @return the action encoding.
 */
inline fun Flags.action(): Int = this and ACTION_MASK

/**
 * Returns the action-enumeration value from the encoding.
 * @return the action-enumeration value from the encoding.
 */
inline fun Flags.actionEnum(): Action = when (this.action()) {
    ActionValues.CREATED -> Action.CREATED
    ActionValues.UPDATED -> Action.UPDATED
    ActionValues.DELETED -> Action.DELETED
    else -> Action.UNKNOWN
}

/**
 * Updates the action encoding in the given flags.
 * @param encoding the encoding to set.
 * @return the new flags.
 */
inline fun Flags.action(encoding: Int): Flags = (this and ACTION_CLEAR) or (encoding and ACTION_MASK)

/**
 * Updates the action encoding in the given flags.
 * @param action the action-enumeration value to set.
 * @return the new flags.
 */
inline fun Flags.action(action: Action): Flags = (this and ACTION_CLEAR) or (action.action and ACTION_MASK)

/**
 * Updates if the storage-number is encoding in a tuple-number, following the _flags_.
 * @param sn _true_ if the storage-number is encoded in a tuple-number.
 * @return the new flags.
 */
inline fun Flags.storageNumber(sn: Boolean): Flags = (this and SN_CLEAR) or (if (sn) SN_ON else SN_OFF)

/**
 * Tests if the storage-number is encoded in a tuple-number, following the _flags_.
 * @return _true_ if the storage-number is encoded in a tuple-number.
 */
inline fun Flags.storageNumber(): Boolean = (this and SN_MASK) == SN_ON
