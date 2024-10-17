@file:Suppress("NOTHING_TO_INLINE")

package naksha.model

import naksha.base.Int64
import naksha.base.Platform
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
 * The undefined store-number used for new tuples.
 */
val UNDEFINED_STORE_NUMBER = Int64(0)

/**
 * Type alias for the store-number, which encodes where a [Tuple] is stored within a storage (storage local unique identifier):
 * ```
 *  [    MN  ][   CN    ][PN]
 *  [0000-000][0-0000-00][00]
 * ```
 * - PN: partition number (_8-bit_) - bits: 0-7 _(BE index 7)_
 * - CN: collection-number (_28-bit_) - bits: 8-35 (unsigned)
 * - MN: map-number (_28-bit_) - bits: 36-63 (unsigned)
 * @since 3.0.0
 */
typealias StoreNumber = Int64

/**
 * Returns the undefined store-number.
 * @since 3.0.0
 */
inline fun StoreNumber(): StoreNumber = UNDEFINED_STORE_NUMBER

/**
 * Create a new store-number for a collection itself (partition-number is zero).
 * @param mapNum the map-number.
 * @param colNum the collection-number.
 * @since 3.0.0
 */
inline fun StoreNumber(mapNum: Int, colNum: Int) =
    (Int64(mapNum and MAP_NUM_VALUE_MASK) shl MAP_NUM_SHIFT) or
            (Int64(colNum and COL_NUM_VALUE_MASK) shl COL_NUM_SHIFT)

/**
 * Create a new store-number.
 * @param mapNum the map-number.
 * @param colNum the collection-number.
 * @param partitionNumber the partition-number.
 * @since 3.0.0
 */
inline fun StoreNumber(mapNum: Int, colNum: Int, partitionNumber: Int) =
    (Int64(mapNum and MAP_NUM_VALUE_MASK) shl MAP_NUM_SHIFT) or
        (Int64(colNum and COL_NUM_VALUE_MASK) shl COL_NUM_SHIFT) or
        Int64(partitionNumber and PART_NUM_VALUE_MASK)

/**
 * Returns the store-number in compact format, without partition-number (shifted right by 8).
 *
 * This can be used to compare store-numbers, ignoring the partition-number.
 *
 * Another use case is to expose the store-number to the browsers, because we release the 8 lower bit, the map-number is not safe. We can restore the collection-id actually pretty easily, because the partition-number can be calculated from the feature-id.
 * @return the collection-id without partition-number (shifted right by 8).
 * @since 3.0.0
 */
inline fun StoreNumber.compact(): Int64 = this ushr PART_NUM_SHIFT

/**
 * Tests if this is the undefined store-number.
 * @return _true_ if this is the undefined store-number.
 * @since 3.0.0
 */
inline fun StoreNumber.isUndefined(): Boolean = this == UNDEFINED_STORE_NUMBER

/**
 * Restores the original store-number from the [compact] format.
 * @param featureId the feature-id.
 * @return the full store-number.
 * @since 3.0.0
 */
inline fun StoreNumber.restore(featureId: String): Int64 = (this shl PART_NUM_SHIFT) or
        Int64(partitionNumber(featureId) and PART_NUM_VALUE_MASK)

/**
 * Restores the original store-number from the [compact] format.
 * @param partNum the partition-number.
 * @return the full store-number.
 * @since 3.0.0
 */
inline fun StoreNumber.restore(partNum: Int): Int64 = (this shl PART_NUM_SHIFT) or
        Int64(partNum and PART_NUM_VALUE_MASK)

/**
 * Create a new store-number.
 * @param mapNum the map-number.
 * @param colNum the collection-number.
 * @param featureId the feature-id for which to create a store-number.
 * @since 3.0.0
 */
inline fun StoreNumber(colNum: Int, mapNum: Int, featureId: String) =
    Int64((mapNum and MAP_NUM_VALUE_MASK) shl MAP_NUM_SHIFT) or
        (Int64(colNum and COL_NUM_VALUE_MASK) shl COL_NUM_BITS) or
        Int64(partitionNumber(featureId) and PART_NUM_VALUE_MASK)

/**
 * Returns the partition-number encoded in the store-number.
 * @return the partition-number.
 * @since 3.0.0
 */
inline fun StoreNumber.partitionNumber(): Int = this.toInt() and PART_NUM_VALUE_MASK

/**
 * Sets the partition-number in the store-number.
 * @return the store-number.
 * @since 3.0.0
 */
inline fun StoreNumber.partitionNumber(partNum: Int): Int64 = (this and PART_NUM_CLEAR) or Int64(partNum and PART_NUM_VALUE_MASK)

/**
 * Returns the map-number encoded in the store-number.
 * @return the map-number encoded in the store-number.
 * @since 3.0.0
 */
inline fun StoreNumber.mapNumber(): Int =
    (this ushr MAP_NUM_SHIFT).toInt() and MAP_NUM_VALUE_MASK

/**
 * Sets the map-number in the store-number.
 * @return the map-number.
 * @since 3.0.0
 */
inline fun StoreNumber.mapNumber(mapNum: Int): Int64 =
    (this and MAP_NUM_CLEAR) or (Int64(mapNum and MAP_NUM_VALUE_MASK) shl MAP_NUM_SHIFT)

/**
 * Returns the collection-number encoded in the store-number.
 * @return the collection-number encoded in the store-number.
 * @since 3.0.0
 */
inline fun StoreNumber.collectionNumber(): Int = (this ushr COL_NUM_SHIFT).toInt() and COL_NUM_VALUE_MASK

/**
 * Sets the collection-number encoded in the store-number.
 * @return the store-number.
 * @since 3.0.0
 */
inline fun StoreNumber.collectionNumber(colNum: Int): Int64 = (this and COL_NUM_CLEAR) or (Int64(colNum and COL_NUM_VALUE_MASK) shl COL_NUM_SHIFT)

