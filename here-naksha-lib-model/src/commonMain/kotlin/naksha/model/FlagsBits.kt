@file:Suppress("OPT_IN_USAGE", "MemberVisibilityCanBePrivate")

package naksha.model

import kotlin.js.JsExport

/**
 * Collection of bit definitions for the `flags`. The flags only encode the feature serialization format now:
 * ```
 *      RESERVED            FE
 * [0000-0000-0000-0000-0000-0000][0000]-[0000]
 * ```
 * - FE: feature encoding - bits: 4-7 (4-bit)
 * - RESERVED: all other bits
 *
 * Geometries are always stored as raw `TWKB`, tags as `JBON_GZIP`, and the [action][Action] lives in the lower two bits of the [Version.txn]. Only the feature encoding is configurable here.
 * @since 3.0.0
 */
@JsExport
open class FlagsBits {
    companion object FlagsBitsCompanion {
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
    }
}
