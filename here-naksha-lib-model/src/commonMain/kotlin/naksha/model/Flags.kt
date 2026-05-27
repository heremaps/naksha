@file:Suppress("NOTHING_TO_INLINE", "unused")

package naksha.model

import naksha.model.FlagsBits.FlagsBitsCompanion.ACTION_CLEAR
import naksha.model.FlagsBits.FlagsBitsCompanion.ACTION_MASK
import naksha.model.FlagsBits.FlagsBitsCompanion.OP_CLEAR
import naksha.model.FlagsBits.FlagsBitsCompanion.OP_MASK
import naksha.model.FlagsBits.FlagsBitsCompanion.FEATURE_CLEAR
import naksha.model.FlagsBits.FlagsBitsCompanion.FEATURE_GZIP_BIT
import naksha.model.FlagsBits.FlagsBitsCompanion.FEATURE_MASK

/**
 * Collection of bits making the `flags`. The flags store how the binaries are encoded:
 * ```
 *      RESERVED       AC    OP    FE
 * [0000-0000-0000-00][00]-[0000][0000]-[0000]
 * ```
 * - FE: feature encoding - bits: 4-7 (4-bit)
 * - OP: operation - bits: 12-15 (4-bit)
 * - AC: action - bits: 16-17 (2-bit)
 * - RESERVED: reserved - all other bits
 *
 * Geometries are always stored as raw `TWKB` and tags as `JBON_GZIP`; only the feature encoding is configurable here.
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
