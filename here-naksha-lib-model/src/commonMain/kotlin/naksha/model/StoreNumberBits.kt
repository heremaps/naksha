@file:Suppress("OPT_IN_USAGE", "MemberVisibilityCanBePrivate", "NON_EXPORTABLE_TYPE")

package naksha.model

import naksha.base.Int64
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

/**
 * Bit definition for the store-number:
 * ```
 *  R[ MN ][       CN        ][PN]
 *  0[000]-[0000-0000-0000-00][00]
 * ```
 * - PN: partition number (_8-bit_) - bits: 0-7
 * - CN: collection-number (_40-bit_) - bits: 8-47
 * - MN: the map-number (_12-bit_) - bits: 48-59
 * - R: Reserved (_4-bit_) - bits 60-63
*/
@JsExport
open class StoreNumberBits {
    companion object StoreNumber_C {
        // -------------------------------< PARTITION NUMBER >------------------------------------

        /**
         * The bits to shift the value in the [StoreNumber].
         */
        const val PART_NUM_SHIFT = 0

        /**
         * The bits used to encode the value in [StoreNumber].
         */
        const val PART_NUM_BITS = 8

        /**
         * The bitmask to AND combine the value.
         */
        const val PART_NUM_VALUE_MASK = (1 shl PART_NUM_BITS) - 1

        /**
         * The bitmask to AND combine with [StoreNumber] to read the value from [StoreNumber].
         */
        @JvmField
        @JsStatic
        val PART_NUM_MASK = ((Int64(1) shl PART_NUM_BITS) - Int64(1)) shl PART_NUM_SHIFT

        /**
         * The bitmask to AND combine with [StoreNumber] to clear the value from the [StoreNumber].
         */
        @JvmField
        @JsStatic
        val PART_NUM_CLEAR = PART_NUM_MASK.inv()

        // -------------------------------< COLLECTION ID >------------------------------------

        /**
         * The bits to shift the value in the [StoreNumber].
         */
        const val COL_NUM_SHIFT = 8

        /**
         * The bits used to encode the value in [StoreNumber].
         */
        const val COL_NUM_BITS = 40

        /**
         * The bitmask to AND combine the value.
         */
        @JvmField
        @JsStatic
        val COL_NUM_VALUE_MASK = (Int64(1) shl COL_NUM_BITS) - Int64(1)

        /**
         * The bitmask to AND combine with [StoreNumber] to read the value from [StoreNumber].
         */
        @JvmField
        @JsStatic
        val COL_NUM_MASK = ((Int64(1) shl COL_NUM_BITS) - Int64(1)) shl COL_NUM_SHIFT

        /**
         * The bitmask to AND combine with [StoreNumber] to clear the value from the [StoreNumber].
         */
        @JvmField
        @JsStatic
        val COL_NUM_CLEAR = COL_NUM_MASK.inv()

        // -------------------------------< MAP ID >------------------------------------

        /**
         * The bits to shift the value in the [StoreNumber].
         */
        const val MAP_NUM_SHIFT = 48

        /**
         * The bits used to encode the value in [StoreNumber].
         */
        const val MAP_NUM_BITS = 16

        /**
         * The bitmask to AND combine the value.
         */
        const val MAP_NUM_VALUE_MASK = (1 shl MAP_NUM_BITS) - 1

        /**
         * The bitmask to AND combine with [StoreNumber] to read the value from [StoreNumber].
         */
        @JvmField
        @JsStatic
        val MAP_NUM_MASK = ((Int64(1) shl MAP_NUM_BITS) - Int64(1)) shl MAP_NUM_SHIFT

        /**
         * The bitmask to AND combine with [StoreNumber] to clear the value from the [StoreNumber].
         */
        @JvmField
        @JsStatic
        val MAP_NUM_CLEAR = MAP_NUM_MASK.inv()
    }
}