@file:Suppress("NOTHING_TO_INLINE", "unused")

package naksha.model

import naksha.model.FlagsBits.FlagsBitsCompanion.ACTION_CLEAR
import naksha.model.FlagsBits.FlagsBitsCompanion.ACTION_MASK
import naksha.model.FlagsBits.FlagsBitsCompanion.CV_SHIFT
import naksha.model.FlagsBits.FlagsBitsCompanion.OP_CLEAR
import naksha.model.FlagsBits.FlagsBitsCompanion.OP_MASK
import naksha.model.FlagsBits.FlagsBitsCompanion.FEATURE_CLEAR
import naksha.model.FlagsBits.FlagsBitsCompanion.FEATURE_GZIP_BIT
import naksha.model.FlagsBits.FlagsBitsCompanion.FEATURE_MASK
import naksha.model.FlagsBits.FlagsBitsCompanion.GEO_CLEAR
import naksha.model.FlagsBits.FlagsBitsCompanion.GEO_GZIP_BIT
import naksha.model.FlagsBits.FlagsBitsCompanion.GEO_MASK
import naksha.model.FlagsBits.FlagsBitsCompanion.HAS_AUTHOR_TS_BIT
import naksha.model.FlagsBits.FlagsBitsCompanion.HAS_AUTHOR_TS_CLEAR
import naksha.model.FlagsBits.FlagsBitsCompanion.HAS_BASE_TN_BIT
import naksha.model.FlagsBits.FlagsBitsCompanion.HAS_BASE_TN_CLEAR
import naksha.model.FlagsBits.FlagsBitsCompanion.HAS_CREATED_AT_BIT
import naksha.model.FlagsBits.FlagsBitsCompanion.HAS_CREATED_AT_CLEAR
import naksha.model.FlagsBits.FlagsBitsCompanion.HAS_PREV_TN_BIT
import naksha.model.FlagsBits.FlagsBitsCompanion.HAS_PREV_TN_CLEAR
import naksha.model.FlagsBits.FlagsBitsCompanion.TAGS_CLEAR
import naksha.model.FlagsBits.FlagsBitsCompanion.TAGS_GZIP_BIT
import naksha.model.FlagsBits.FlagsBitsCompanion.TAGS_MASK

/**
 * Collection of bits making the `flags`. The flags store the encoding in the storage, so how the binaries are encoded:
 * ```
 *   RSV    CAB-PN  V3210  AC    OP    TE     FE    GE
 * [0000-0][000-00][00-00][00]-[0000][0000]-[0000][0000]
 * ```
 * - GE: geometry encoding - bits: 0-3 (4-bit)
 * - FE: feature encoding - bits: 4-7 (4-bit)
 * - TE: tags encoding - bits: 8-11 (4-bit)
 * - OP: operation - bits: 12-15 (4-bit)
 * - AC: action - bits: 16-17 (2-bit)
 * - V3210: has-custom-value-0 - bit 18 _(1-bit, 262144)_
 * - V3210: has-custom-value-1 - bit 19 _(1-bit, 524288)_
 * - V3210: has-custom-value-2 - bit 20 _(1-bit, 1048576)_
 * - V3210: has-custom-value-3 - bit 21 _(1-bit, 2097152)_
 * - N: has-next-version - bit 22 _(1-bit, 4194304)_
 * - P: has-previous-tuple-number - bit 23 _(1-bit, 8388608)_
 * - B: has-base-tuple-number - bit 24 _(1-bit, 16777216)_
 * - A: has-author-ts - bit 25 _(1-bit, 33554432)_
 * - C: has-created-at - bit 26 _(1-bit, 67108864)_
 * - RSV: reserved - bit: 27-31 (5-bit)
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
 * Returns the operation encoding.
 * @return the operation encoding.
 * @since 3.0.0
 */
inline fun Flags.operation(): Int = this and OP_MASK

/**
 * Returns the operation-enumeration value from the encoding.
 * @return the operation-enumeration value from the encoding.
 * @since 3.0.0
 */
inline fun Flags.operationEnum(): Operation = Operation.fromValue(operation())

/**
 * Updates the operation encoding.
 * @param value the encoding to set.
 * @return the new flags.
 * @since 3.0.0
 * @see [Operation]
 */
inline fun Flags.withOperation(value: Int): Flags = (this and OP_CLEAR) or (value and OP_MASK)

/**
 * Updates the operation encoding.
 * @param op the operation to set.
 * @return the new flags.
 * @since 3.0.0
 * @see [Operation]
 */
inline fun Flags.withOperation(op: Operation): Flags = (this and OP_CLEAR) or (op.intValue)

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

/**
 * Updates the previous-tuple-number bit.
 * @param value _true_ if the [MetadataBinary] encodes the previous-tuple-number.
 * @return the new flags.
 * @since 3.0.0
 */
inline fun Flags.withPrevTupleNumber(value: Boolean): Flags = if (value) (this or HAS_PREV_TN_BIT) else (this and HAS_PREV_TN_CLEAR)

/**
 * Tests if the previous-tuple-number is encoded in the [MetadataBinary].
 * @return _true_ if the previous-tuple-number is encoded in the [MetadataBinary].
 * @since 3.0.0
 */
inline fun Flags.hasPrevTupleNumber(): Boolean = (this and HAS_PREV_TN_CLEAR) == HAS_PREV_TN_BIT

/**
 * Updates the base-tuple-number bit.
 * @param value _true_ if the [MetadataBinary] encodes the base-tuple-number.
 * @return the new flags.
 * @since 3.0.0
 */
inline fun Flags.withBaseTupleNumber(value: Boolean): Flags = if (value) (this or HAS_BASE_TN_BIT) else (this and HAS_BASE_TN_CLEAR)

/**
 * Tests if the base-tuple-number is encoded in the [MetadataBinary].
 * @return _true_ if the base-tuple-number is encoded in the [MetadataBinary].
 * @since 3.0.0
 */
inline fun Flags.hasBaseTupleNumber(): Boolean = (this and HAS_BASE_TN_CLEAR) == HAS_BASE_TN_BIT

/**
 * If the `createdAt` value is encoded explicitly, otherwise the value is read from `updatedAt`.
 * @param value _true_ if the [MetadataBinary] encodes the `createdAt` value.
 * @return the new flags.
 * @since 3.0.0
 */
inline fun Flags.withCreateAt(value: Boolean): Flags = if (value) (this or HAS_CREATED_AT_BIT) else (this and HAS_CREATED_AT_CLEAR)

/**
 * Tests if the `createdAt` value is encoded in the [MetadataBinary], if not, the value should be read from `updatedAt`.
 * @return _true_ if the `createdAt` value is encoded in the [MetadataBinary].
 * @since 3.0.0
 */
inline fun Flags.hasCreatedAt(): Boolean = (this and HAS_CREATED_AT_CLEAR) == HAS_CREATED_AT_BIT

/**
 * If the `authorTs` value is encoded explicitly, otherwise the value is read from `updatedAt`.
 * @param value _true_ if the [MetadataBinary] encodes the `authorTs` value.
 * @return the new flags.
 * @since 3.0.0
 */
inline fun Flags.withAuthorTs(value: Boolean): Flags = if (value) (this or HAS_AUTHOR_TS_BIT) else (this and HAS_AUTHOR_TS_CLEAR)

/**
 * Tests if the `authorTs` value is encoded in the [MetadataBinary], if not, the value should be read from `updatedAt`.
 * @return _true_ if the `authorTs` value is encoded in the [MetadataBinary].
 * @since 3.0.0
 */
inline fun Flags.hasAuthorTs(): Boolean = (this and HAS_AUTHOR_TS_CLEAR) == HAS_AUTHOR_TS_BIT

/**
 * Updates the custom-value bit.
 * @param i the custom value to test (`0 to 3`).
 * @param value _true_ if the [MetadataBinary] encodes the custom-value.
 * @return the new flags.
 * @since 3.0.0
 */
inline fun Flags.withCustomValue(i: Int, value: Boolean = true): Flags {
    val BIT = 1 shl (CV_SHIFT + (i and 3))
    return if (value) (this or BIT) else (this and BIT.inv())
}

/**
 * Tests if the custom-value is encoded in the [MetadataBinary].
 * @param i the custom value to test (`0 to 3`).
 * @return _true_ if the custom-value is encoded in the [MetadataBinary].
 * @since 3.0.0
 */
inline fun Flags.hasCustomValue(i: Int): Boolean {
    val BIT = 1 shl (CV_SHIFT + (i and 3))
    return (this and BIT) == BIT
}

