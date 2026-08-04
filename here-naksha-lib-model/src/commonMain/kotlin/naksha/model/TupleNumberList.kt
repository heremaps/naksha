@file:Suppress("OPT_IN_USAGE")

package naksha.model

import naksha.base.PTypedArray
import naksha.base.TupleNumber
import naksha.base.TupleNumberVariant
import naksha.model.BinaryUtil.BinaryUtil_C.TYPE_TUPLE_NUMBER_ARRAY
import naksha.model.BinaryUtil.BinaryUtil_C.writeSimpleHeader
import naksha.base.TupleNumberVariant.TupleNumberVariant_C.B128
import naksha.base.TupleNumberVariant.TupleNumberVariant_C.B160
import naksha.base.TupleNumberVariant.TupleNumberVariant_C.B192
import naksha.base.TupleNumberVariant.TupleNumberVariant_C.B256
import naksha.base.TupleNumberVariant.TupleNumberVariant_C.B64
import naksha.base.illegalArg
import naksha.base.illegalState
import naksha.base.setInt32Be
import naksha.base.setInt64Be
import naksha.model.request.ITupleNumberArray
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic

/**
 * A list of [tuple-numbers][naksha.base.TupleNumber].
 * @since 3.0.0
 */
@JsExport
class TupleNumberList : PTypedArray<TupleNumber>(TupleNumber::class), ITupleNumberArray {

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
        var databaseNumber = 0L
        var catalogNumber = 0
        var collectionNumber = 0
        var featureNumber = 0L
        for (tupleNumber in this) {
            if (tupleNumber == null) {
                length--
                continue
            }
            if (variant == null) {
                // We found a first tuple, we hope that each tuple can be encoded in 64-bit only.
                variant = B64
                // Share all values
                databaseNumber = tupleNumber.databaseNumber
                catalogNumber = tupleNumber.catalogNumber
                collectionNumber = tupleNumber.collectionNumber
                featureNumber = tupleNumber.featureNumber
                continue
            }
            if (variant === B256) continue
            if (databaseNumber != tupleNumber.databaseNumber) {
                // We need to encode all values individually
                variant = B256
                break
            }
            if (variant === B192) continue // We know that we max share the database
            if (catalogNumber != tupleNumber.catalogNumber) {
                // We need to encode individual catalog-, collection-, and feature-numbers
                variant = B192
                continue
            }
            if (variant === B160) continue // We know that we max share the database, and catalog
            if (collectionNumber != tupleNumber.collectionNumber) {
                // We need to encode individual collection-, and feature-numbers
                variant = B160
                continue
            }
            if (variant === B128) continue // We know that we max share the database, catalog, and collection
            if (featureNumber != tupleNumber.featureNumber) {
                // We need to encode individual feature-numbers
                variant = B128
                continue
            }
            // So far, we found the same storage-, catalog-, collection-, and feature-numbers for all tuple-numbers.
            // So we expect that the current variant is still B64 as initially setup for the first tuple.
            check(variant === B64)
        }
        // If the list is empty, return empty bytes.
        if (variant == null || length == 0) return byteArrayOf()
        // Dependent on which variant is the smallest, we will encode for each tuple-number:
        // 32 byte = storage:8, map:4, collection:4, feature:8, txn:8
        // 24 byte = map:4, collection:4, feature:8, txn:8
        // 20 byte = collection:4, feature:8, txn:8
        // 16 byte = feature:8, txn:8
        //  8 byte = txn:8
        val SIZE = 8 + variant.sharedBytes + variant.encodingBytes * length
        val bytes = ByteArray(SIZE)
        writeSimpleHeader(bytes, 0, TYPE_TUPLE_NUMBER_ARRAY, variant.subType, length, SIZE)
        var i = 8
        if (variant.sharedDatabaseNumber()) {
            bytes.setInt64Be(i, databaseNumber)
            i += 8
        }
        if (variant.sharedCatalogNumber()) {
            bytes.setInt32Be( i, catalogNumber)
            i += 4
        }
        if (variant.sharedCollectionNumber()) {
            bytes.setInt32Be(i, collectionNumber)
            i += 4
        }
        if (variant.sharedFeatureNumber()) {
            bytes.setInt64Be(i, featureNumber)
            i += 8
        }
        check(i == variant.sharedBytes)
        for (tupleNumber in this) {
            if (tupleNumber == null) continue
            if (variant.encodeDatabaseNumber()) {
                bytes.setInt64Be(i, tupleNumber.databaseNumber)
                i += 8
            }
            if (variant.encodeCatalogNumber()) {
                bytes.setInt32Be(i, tupleNumber.catalogNumber)
                i += 4
            }
            if (variant.encodeCollectionNumber()) {
                bytes.setInt32Be(i, tupleNumber.collectionNumber)
                i += 4
            }
            if (variant.encodeFeatureNumber()) {
                bytes.setInt64Be(i, tupleNumber.featureNumber)
                i += 8
            }
            bytes.setInt64Be(i, tupleNumber.version)
            i += 8
        }
        check(i == SIZE)
        return bytes
    }

    override fun getDatabaseNumber(i: Int): Long = get(i)?.databaseNumber ?: throw illegalArg("No tuple-number at index $i")
    override fun getCatalogNumber(i: Int): Int = get(i)?.catalogNumber ?: throw illegalArg("No tuple-number at index $i")
    override fun getCollectionNumber(i: Int): Int = get(i)?.collectionNumber ?: throw illegalArg("No tuple-number at index $i")
    override fun getFeatureNumber(i: Int): Long = get(i)?.featureNumber ?: throw illegalArg("No tuple-number at index $i")
    override fun getVersion(i: Int): Long = get(i)?.version ?: throw illegalArg("No tuple-number at index $i")
    override fun getTupleNumber(i: Int): TupleNumber = get(i) ?: throw illegalArg("No tuple-number at index $i")

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