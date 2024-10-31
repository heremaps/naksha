@file:Suppress("NOTHING_TO_INLINE")

package naksha.model

import naksha.base.Int64
import naksha.model.StoreNumberBits.StoreNumber_C.COL_NUM_BITS
import naksha.model.StoreNumberBits.StoreNumber_C.COL_NUM_CLEAR
import naksha.model.StoreNumberBits.StoreNumber_C.COL_NUM_SHIFT
import naksha.model.StoreNumberBits.StoreNumber_C.COL_NUM_VALUE_MASK
import naksha.model.StoreNumberBits.StoreNumber_C.MAP_NUM_CLEAR
import naksha.model.StoreNumberBits.StoreNumber_C.MAP_NUM_SHIFT
import naksha.model.StoreNumberBits.StoreNumber_C.MAP_NUM_VALUE_MASK
import naksha.model.StoreNumberBits.StoreNumber_C.PART_NUM_CLEAR
import naksha.model.StoreNumberBits.StoreNumber_C.PART_NUM_SHIFT
import naksha.model.StoreNumberBits.StoreNumber_C.PART_NUM_VALUE_MASK
import naksha.model.Naksha.NakshaCompanion.partitionNumber

/**
 * Type alias for the store-number:
 * ```
 *  R[ MN ][       CN        ][PN]
 *  0[000]-[0000-0000-0000-00][00]
 * ```
 * - PN: partition number (_8-bit_) - bits: 0-7 _(BE index 7)_
 * - CN: collection-number (_40-bit_) - bits: 8-47
 * - MN: the map-number (_12-bit_) - bits: 48-59
 * - R: Reserved (_4-bit_) - bits 60-63
 */
typealias StoreNumber = Int64

/**
 * Create a new store-number for a collection itself (partition-number is zero).
 * @param mapNum the map-number.
 * @param colNum the collection-number.
 */
inline fun StoreNumber(mapNum: Int, colNum: Int64) =
    Int64((mapNum and MAP_NUM_VALUE_MASK) shl MAP_NUM_SHIFT) or
        ((colNum and COL_NUM_VALUE_MASK) shl COL_NUM_SHIFT)

/**
 * Create a new store-number.
 * @param mapNum the map-number.
 * @param colNum the collection-number.
 * @param partitionNumber the partition-number.
 */
inline fun StoreNumber(mapNum: Int, colNum: Int64, partitionNumber: Int) =
    Int64((mapNum and MAP_NUM_VALUE_MASK) shl MAP_NUM_SHIFT) or
        ((colNum and COL_NUM_VALUE_MASK) shl COL_NUM_SHIFT) or
        Int64(partitionNumber and PART_NUM_VALUE_MASK)

/**
 * Returns the store-number in compact format, without partition-number (shifted right by 8).
 *
 * This can be used to compare store-numbers, ignoring the partition-number.
 *
 * Another use case is to expose the store-number to the browsers, because we release the 8 lower bit, the map-number is not safe. We can restore the collection-id actually pretty easily, because the partition-number can be calculated from the feature-id.
 * @return the collection-id without partition-number (shifted right by 8).
 */
inline fun StoreNumber.compact(): Int64 = this ushr PART_NUM_SHIFT

/**
 * Restores the original store-number from the [compact] format.
 * @param featureId the feature-id.
 * @return the full store-number.
 */
inline fun StoreNumber.restore(featureId: String): Int64 = (this shl PART_NUM_SHIFT) or
        Int64(partitionNumber(featureId) and PART_NUM_VALUE_MASK)

/**
 * Restores the original store-number from the [compact] format.
 * @param partNum the partition-number.
 * @return the full store-number.
 */
inline fun StoreNumber.restore(partNum: Int): Int64 = (this shl PART_NUM_SHIFT) or
        Int64(partNum and PART_NUM_VALUE_MASK)

/**
 * Create a new store-number.
 * @param mapNum the map-number.
 * @param colNum the collection-number.
 * @param featureId the feature-id for which to create a store-number.
 */
inline fun StoreNumber(colNum: Int64, mapNum: Int, featureId: String) =
    Int64((mapNum and MAP_NUM_VALUE_MASK) shl MAP_NUM_SHIFT) or
        ((colNum and COL_NUM_VALUE_MASK) shl COL_NUM_BITS) or
        Int64(partitionNumber(featureId) and PART_NUM_VALUE_MASK)

/**
 * Returns the partition-number encoded in the store-number.
 * @return the partition-number.
 */
inline fun StoreNumber.partitionNumber(): Int = this.toInt() and PART_NUM_VALUE_MASK

/**
 * Sets the partition-number in the store-number.
 * @return the store-number.
 */
inline fun StoreNumber.partitionNumber(partNum: Int): Int64 = (this and PART_NUM_CLEAR) or
        Int64(partNum and PART_NUM_VALUE_MASK)

/**
 * Returns the map-number encoded in the store-number.
 * @return the map-number encoded in the store-number.
 */
inline fun StoreNumber.mapNumber(): Int =
    (this ushr MAP_NUM_SHIFT).toInt() and MAP_NUM_VALUE_MASK

/**
 * Sets the map-number in the store-number.
 * @return the map-number.
 */
inline fun StoreNumber.mapNumber(mapNum: Int): Int64 =
    (this and MAP_NUM_CLEAR) or (Int64(mapNum and MAP_NUM_VALUE_MASK) shl MAP_NUM_SHIFT)

/**
 * Returns the collection-number encoded in the store-number.
 * @return the collection-number encoded in the store-number.
 */
inline fun StoreNumber.collectionNumber(): Int64 = (this ushr COL_NUM_SHIFT) and COL_NUM_VALUE_MASK

/**
 * Sets the collection-number encoded in the store-number.
 * @return the store-number.
 */
inline fun StoreNumber.collectionNumber(colNum: Int64): Int64 = (this and COL_NUM_CLEAR) or ((colNum and COL_NUM_VALUE_MASK) shl COL_NUM_SHIFT)

