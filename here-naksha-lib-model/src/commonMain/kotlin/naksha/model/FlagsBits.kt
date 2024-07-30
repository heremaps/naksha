@file:Suppress("OPT_IN_USAGE", "MemberVisibilityCanBePrivate")

package naksha.model

import kotlin.js.JsExport

/**
 * Collection of bits definitions for the `flags`. The flags store the encoding in the storage, it stores how the binaries are encoded:
 * ```
 *       Reserved         R1  AE   TE    FE    GE
 * [0000-0000-0000-0000]-[00][00][0000][0000][0000]
 * ```
 * - GE: geometry (and reference point) encoding - bits: 0-3
 * - FE: feature encoding - bits: 4-7
 * - TE: tags encoding - bits: 8-11
 * - AE: action - bits: 12+13
 * - R1: reserved - bits: 14+15
 * - ---
 * - Reserved - bits: 16-31
*/
@JsExport
open class FlagsBits {
    companion object FlagsBitsCompanion {
        // --------------------------------------< Geometry >-------------------------------------

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

        // --------------------------------------< ACTION >---------------------------------------

        /**
         * The bits to shift the value in the [Flags].
         */
        const val ACTION_SHIFT = 12

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

        // -------------------------------< PARTITION NUMBER >------------------------------------

        /**
         * The bits to shift the value in the [Flags].
         */
        const val PARTITION_NUMBER_SHIFT = 14

        /**
         * The bits used to encode the value in [Flags].
         */
        const val PARTITION_NUMBER_BITS = 8

        /**
         * The bitmask to AND combine with [Flags] to read the value from [Flags].
         */
        const val PARTITION_NUMBER_MASK = ((1 shl PARTITION_NUMBER_BITS) - 1) shl PARTITION_NUMBER_SHIFT

        /**
         * The bitmask to AND combine with [Flags] to clear the value from the [Flags].
         */
        const val PARTITION_NUMBER_CLEAR = PARTITION_NUMBER_MASK.inv()
    }
}