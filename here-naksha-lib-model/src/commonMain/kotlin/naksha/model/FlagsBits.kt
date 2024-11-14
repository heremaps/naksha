@file:Suppress("OPT_IN_USAGE", "MemberVisibilityCanBePrivate")

package naksha.model

import kotlin.js.JsExport

/**
 * Collection of bit definitions for the `flags`. The flags store the encoding in the storage, it stores how the binaries are encoded:
 * ```
 *     Reserved    NPBAC     OP    TE     FE    GE
 * [0000-0000-000][0-0000]-[0000][0000]-[0000][0000]
 * ```
 * - GE: geometry encoding - bits: 0-3 (4-bit)
 * - FE: feature encoding - bits: 4-7 (4-bit)
 * - TE: tags encoding - bits: 8-11 (4-bit)
 * - OP: operation - bits: 12-15 (4-bit)
 * - N: has-next-version - bit 16 _(1-bit, 131072)_
 * - P: has-previous-tuple-number - bit 17 _(1-bit, 262144)_
 * - B: has-base-tuple-number - bit 18 _(1-bit, 524288)_
 * - A: has-author-ts - bit 19 _(1-bit, 1048576)_
 * - C: has-created-at - bit 20 _(1-bit, 2097152)_
 * - Reserved: reserved - bit: 21-31 (31-bit)
 * @since 3.0.0
 */
@JsExport
open class FlagsBits {
    companion object FlagsBitsCompanion {
        // --------------------------------------< Geometry >-------------------------------------
        // NOTE: We keep geometry encoding in the lowest bits by intention!
        //       This allows us to test if the geometry need to be unzipped by just masking the
        //       lowest bit, and if it is set, it is compressed!

        /**
         * The bits to shift the value in the [Flags].
         */
        const val GEO_SHIFT = 0

        /**
         * The bits used to encode the value in [Flags].
         */
        const val GEO_BITS = 4

        /**
         * The bitmask to AND combine with [Flags] to read the value from [Flags].
         */
        const val GEO_MASK = ((1 shl GEO_BITS) - 1) shl GEO_SHIFT

        /**
         * The bit that signals geometry compression.
         */
        const val GEO_GZIP_BIT = 1 shl GEO_SHIFT

        /**
         * The bitmask to AND combine with [Flags] to clear the value from the [Flags].
         */
        const val GEO_CLEAR = GEO_MASK.inv()

        // --------------------------------------< Feature >--------------------------------------

        /**
         * The bits to shift the value in the [Flags].
         */
        const val FEATURE_SHIFT = 4

        /**
         * The bits used to encode the value in [Flags].
         */
        const val FEATURE_BITS = 4

        /**
         * The bitmask to AND combine with [Flags] to read the value from [Flags].
         */
        const val FEATURE_MASK = ((1 shl FEATURE_BITS) - 1) shl FEATURE_SHIFT

        /**
         * The bit that signals feature compression.
         */
        const val FEATURE_GZIP_BIT = 1 shl FEATURE_SHIFT

        /**
         * The bitmask to AND combine with [Flags] to clear the value from the [Flags].
         */
        const val FEATURE_CLEAR = FEATURE_MASK.inv()

        // --------------------------------------< TAGS >-----------------------------------------

        /**
         * The bits to shift the value in the [Flags].
         */
        const val TAGS_SHIFT = 8

        /**
         * The bits used to encode the value in [Flags].
         */
        const val TAGS_BITS = 4

        /**
         * The bitmask to AND combine with [Flags] to read the value from [Flags].
         */
        const val TAGS_MASK = ((1 shl TAGS_BITS) - 1) shl TAGS_SHIFT

        /**
         * The bit that signals tags compression.
         */
        const val TAGS_GZIP_BIT = 1 shl TAGS_SHIFT

        /**
         * The bitmask to AND combine with [Flags] to clear the value from the [Flags].
         */
        const val TAGS_CLEAR = TAGS_MASK.inv()

        // --------------------------------------< OPERATION >------------------------------------

        /**
         * The bits to shift the value in the [Flags].
         */
        const val OP_SHIFT = 12

        /**
         * The bits used to encode the value in [Flags].
         */
        const val OP_BITS = 4

        /**
         * The bitmask to AND combine with [Flags] to read the value from [Flags].
         */
        const val OP_MASK = ((1 shl OP_BITS) - 1) shl OP_SHIFT

        /**
         * The bitmask to AND combine with [Flags] to clear the value from the [Flags].
         */
        const val OP_CLEAR = OP_MASK.inv()

        // --------------------------------------< ACTION >---------------------------------------

        /**
         * The bits to shift the value in the [Flags].
         */
        const val ACTION_SHIFT = 16

        /**
         * The bits used to encode the value in [Flags].
         */
        const val ACTION_BITS = 2

        /**
         * The bitmask to AND combine with [Flags] to read the value from [Flags].
         */
        const val ACTION_MASK = ((1 shl ACTION_BITS) - 1) shl ACTION_SHIFT

        /**
         * The bitmask to AND combine with [Flags] to clear the value from the [Flags].
         */
        const val ACTION_CLEAR = ACTION_MASK.inv()

        // --------------------------------------< BITS >-----------------------------------------

        /**
         * The bit to set, when the [MetadataBinary] encodes a next-version.
         * @since 3.0.0
         */
        const val HAS_NEXT_VERSION_BIT = 524288

        /**
         * The bitmask to clear the next-version bit from the [Flags].
         * @since 3.0.0
         */
        const val HAS_NEXT_VERSION_CLEAR = HAS_NEXT_VERSION_BIT.inv()

        /**
         * The bit to set, when the [MetadataBinary] encodes a previous tuple-number.
         * @since 3.0.0
         */
        const val HAS_PREV_TN_BIT = 1048576

        /**
         * The bitmask to clear the prev_tn bit from the [Flags].
         * @since 3.0.0
         */
        const val HAS_PREV_TN_CLEAR = HAS_PREV_TN_BIT.inv()

        /**
         * The bit to set, when the [MetadataBinary] encodes a base tuple-number.
         * @since 3.0.0
         */
        const val HAS_BASE_TN_BIT = 2097152

        /**
         * The bitmask to clear the base_tn bit from the [Flags].
         * @since 3.0.0
         */
        const val HAS_BASE_TN_CLEAR = HAS_BASE_TN_BIT.inv()

        /**
         * The bit to set, when the [MetadataBinary] encodes the author-ts value.
         * @since 3.0.0
         */
        const val HAS_AUTHOR_TS_BIT = 4194304

        /**
         * The bitmask to clear the author-ts bit from the [Flags].
         * @since 3.0.0
         */
        const val HAS_AUTHOR_TS_CLEAR = HAS_AUTHOR_TS_BIT.inv()

        /**
         * The bit to set, when the [MetadataBinary] encodes a create-at value.
         * @since 3.0.0
         */
        const val HAS_CREATED_AT_BIT = 8388608

        /**
         * The bitmask to clear the create-at bit from the [Flags].
         * @since 3.0.0
         */
        const val HAS_CREATED_AT_CLEAR = HAS_CREATED_AT_BIT.inv()
    }
}