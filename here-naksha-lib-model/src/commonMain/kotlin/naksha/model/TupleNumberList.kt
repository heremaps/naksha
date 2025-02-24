@file:Suppress("OPT_IN_USAGE")

package naksha.model

import naksha.base.Int64
import naksha.base.ListProxy
import naksha.base.Platform
import naksha.base.PlatformDataViewApi.PlatformDataViewApiCompanion.dataview_set_int32
import naksha.base.PlatformDataViewApi.PlatformDataViewApiCompanion.dataview_set_int64
import naksha.model.BinaryUtil.BinaryUtil_C.TYPE_TUPLE_NUMBER_ARRAY
import naksha.model.BinaryUtil.BinaryUtil_C.writeSimpleHeader
import naksha.model.NakshaError.NakshaErrorCompanion.ILLEGAL_STATE
import naksha.model.TupleNumberVariant.TupleNumberVariant_C.B160
import naksha.model.TupleNumberVariant.TupleNumberVariant_C.B192
import naksha.model.TupleNumberVariant.TupleNumberVariant_C.B224
import naksha.model.TupleNumberVariant.TupleNumberVariant_C.B288
import naksha.model.TupleNumberVariant.TupleNumberVariant_C.B96
import naksha.model.request.FeatureTuple
import naksha.model.request.FeatureTupleList
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic

/**
 * A list of [tuple-numbers][TupleNumber].
 * @since 3.0.0
 */
@JsExport
class TupleNumberList : ListProxy<TupleNumber>(TupleNumber::class) {

    /**
     * Convert this list of [TupleNumber] into a list of [FeatureTuple].
     * @param from the index of the first entry to convert.
     * @param to the index of the first entry **not** to convert.
     * @return the [FeatureTupleList].
     * @since 3.0
     */
    @JvmOverloads
    fun toFeatureTupleList(from: Int = 0, to: Int = size): FeatureTupleList {
        val rs = FeatureTupleList()
        val length = to - from
        rs.setCapacity(length)
        var i = from
        while (i < to) {
            val tupleNumber = this[i] ?: continue
            val featureTuple = FeatureTuple(tupleNumber)
            rs.add(featureTuple)
            i++
        }
        return rs
    }

    /**
     * Encode this list into a byte-array.
     *
     * The returned byte-array can be read using [TupleNumberBinaryArray].
     * @return the list as byte-array.
     * @since 3.0.0
     */
    fun toByteArray(): ByteArray {
        var length = this.size
        if (length == 0) return byteArrayOf()
        // The first loop rounds loads the storage-, map-, collection-, and feature-number of the first tuple-number
        // All further loops remove null values, and they detect,
        //   if the tuple-numbers share storage-, map-, collection-, and/or feature-number
        // Note, this code is not thread safe, no other thread must modify the list while we iterate it.
        var variant: TupleNumberVariant? = null
        var storageNumber: Int64? = null
        var mapNumber: Int? = -1
        var collectionNumber: Int? = -1
        var featureNumber: Int64? = null
        for (tupleNumber in this) {
            if (tupleNumber == null) {
                length--
                continue
            }
            if (variant == null) {
                // We found a first tuple, we hope that each tuple can be encoded in 96-bit only.
                variant = B96
                storageNumber = tupleNumber.storageNumber
                mapNumber = tupleNumber.mapNumber
                collectionNumber = tupleNumber.collectionNumber
                featureNumber = tupleNumber.featureNumber
                continue
            }
            if (storageNumber != tupleNumber.storageNumber) {
                // We need to encode all values individually
                variant = B288
                storageNumber = null
                mapNumber = null
                collectionNumber = null
                featureNumber = null
                break
            }
            if (variant === B224) continue
            if (mapNumber != tupleNumber.mapNumber) {
                // We need to encode individual map-, collection-, and feature-numbers
                variant = B224
                mapNumber = null
                collectionNumber = null
                featureNumber = null
                continue
            }
            if (variant === B192) continue
            if (collectionNumber != tupleNumber.collectionNumber) {
                // We need to encode individual collection-, and feature-numbers
                variant = B192
                collectionNumber = null
                featureNumber = null
                continue
            }
            if (variant === B160) continue // We need to encode at least individual feature-numbers
            if (featureNumber != tupleNumber.featureNumber) {
                // We need to encode individual feature-numbers
                variant = B160
                featureNumber = null
                continue
            }
            // So far, we found the same storage-, map-, collection-, and feature-numbers for all tuple-numbers.
            check(variant === B96)
        }
        // If the list is empty, return empty bytes.
        if (variant == null || length == 0) return byteArrayOf()
        // Dependent on which variant is the smallest, we will encode for each tuple-number:
        // 36 byte = storage:8, map:4, collection:4, feature:8, version:8, uid:4
        // 28 byte = map:4, collection:4, feature:8, version:8, uid:4
        // 24 byte = collection:4, feature:8, version:8, uid:4
        // 20 byte = feature:8, version:8, uid:4
        // 12 byte = version:8, uid:4
        val SIZE = 8 + variant.sharedBytes + variant.encodingBytes * length
        val bytes = ByteArray(SIZE)
        val view = Platform.newDataView(bytes)
        writeSimpleHeader(view, 0, TYPE_TUPLE_NUMBER_ARRAY, variant.subType, length, SIZE)
        var i = 8
        if (variant.sharedStorageNumber()) {
            dataview_set_int64(view, i, storageNumber!!)
            i + 8
        }
        if (variant.sharedMapNumber()) {
            dataview_set_int32(view, i, mapNumber!!)
            i += 4
        }
        if (variant.sharedCollectionNumber()) {
            dataview_set_int32(view, i, collectionNumber!!)
            i += 4
        }
        if (variant.sharedFeatureNumber()) {
            dataview_set_int64(view, i, featureNumber!!)
            i + 8
        }
        check(i == variant.sharedBytes)
        for (tupleNumber in this) {
            if (tupleNumber == null) continue
            if (variant.encodeStorageNumber()) {
                dataview_set_int64(view, i, tupleNumber.storageNumber)
                i += 8
            }
            if (variant.encodeMapNumber()) {
                dataview_set_int32(view, i, tupleNumber.mapNumber)
                i += 4
            }
            if (variant.encodeCollectionNumber()) {
                dataview_set_int32(view, i, tupleNumber.collectionNumber)
                i += 4
            }
            if (variant.encodeFeatureNumber()) {
                dataview_set_int64(view, i, tupleNumber.featureNumber)
                i += 8
            }
            dataview_set_int64(view, i, tupleNumber.txn)
            i += 8
            dataview_set_int32(view, i, tupleNumber.uid)
            i += 4
        }
        check(i == SIZE)
        return bytes
    }

    companion object TupleNumberList_C {
        /**
         * Convert the given [tuple-number-binary-array][TupleNumberBinaryArray] into a [tuple-number list][TupleNumberList].
         *
         * @param array the tuple-number-binary-array.
         * @param from the index of the first entry to convert.
         * @param to the index of the first entry **not** to convert.
         * @return the given binary converted into a list of tuple-number.
         * @since 3.0.0
         */
        @JvmStatic
        @JsStatic
        @JvmOverloads
        fun fromByteArray(array: TupleNumberBinaryArray, from: Int = 0, to: Int = array.size): TupleNumberList {
            val rs = TupleNumberList()
            val length = to - from
            rs.setCapacity(length)
            var i = from
            while (i < to) {
                val tupleNumber = array[i] ?: throw illegalState("Invalid tuple-number at index $i")
                rs.add(tupleNumber)
                i++
            }
            return rs
        }
    }
}