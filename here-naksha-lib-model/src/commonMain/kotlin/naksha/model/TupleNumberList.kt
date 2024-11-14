@file:Suppress("OPT_IN_USAGE")

package naksha.model

import naksha.base.Int64
import naksha.base.ListProxy
import naksha.base.Platform
import naksha.base.PlatformDataViewApi.PlatformDataViewApiCompanion.dataview_set_int32
import naksha.base.PlatformDataViewApi.PlatformDataViewApiCompanion.dataview_set_int64
import naksha.base.toInt64
import naksha.model.BinaryUtil.BinaryUtil_C.SUBTYPE_TNA_12_BYTE
import naksha.model.BinaryUtil.BinaryUtil_C.SUBTYPE_TNA_16_BYTE
import naksha.model.BinaryUtil.BinaryUtil_C.SUBTYPE_TNA_20_BYTE
import naksha.model.BinaryUtil.BinaryUtil_C.SUBTYPE_TNA_28_BYTE
import naksha.model.BinaryUtil.BinaryUtil_C.TYPE_TUPLE_NUMBER_ARRAY
import naksha.model.BinaryUtil.BinaryUtil_C.writeSimpleHeader
import kotlin.js.JsExport

/**
 * A list of [tuple-numbers][TupleNumber].
 * @since 3.0.0
 */
@JsExport
class TupleNumberList : ListProxy<TupleNumber>(TupleNumber::class) {
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
        // The first loop detects if all tuples share the same storage, map, and collection, which allows a more compact encoding.
        // A side effect is, that we can find out how many null values we have.
        // Note, this code is not thread safe, no other thread must modify the list while we iterate it.
        val MINUS_ONE = Int64(-1)
        var storageNumber: Int64? = MINUS_ONE
        var mapNumber: Int? = -1
        var collectionNumber: Int? = -1
        for (tupleNumber in this) {
            if (tupleNumber == null) {
                length--
                continue
            }
            // Init with actual values of this tuple.
            if (storageNumber === MINUS_ONE) {
                storageNumber = tupleNumber.storageNumber
                mapNumber = tupleNumber.mapNumber
                collectionNumber = tupleNumber.collectionNumber
                continue
            }
            // We know already that we have different storages.
            if (storageNumber == null) continue
            if (storageNumber != tupleNumber.storageNumber) {
                storageNumber = null
                mapNumber = null
                collectionNumber = null
                continue
            }
            // We know already that we have different maps.
            if (mapNumber == null) continue
            if (mapNumber != tupleNumber.mapNumber) {
                // We have different maps, we can share storage, but not
                mapNumber = null
                collectionNumber = null
                continue
            }
            if (collectionNumber == null) continue
            if (storageNumber != tupleNumber.storageNumber) collectionNumber = null
        }
        if (length == 0) return byteArrayOf()
        // 28 = storage:8, map:4, collection:4, version:8, uid:4
        // 20 = map:4, collection:4, version:8, uid:4
        // 16 = collection:4, version:8, uid:4
        // 12 = version:8, uid:4
        val entrySize = if (storageNumber == null) 28 else if (mapNumber == null) 20 else if (collectionNumber == null) 16 else 12
        val SIZE = 8 + length * entrySize
        val bytes = ByteArray(SIZE)
        val view = Platform.newDataView(bytes)
        var end = when (entrySize) {
            28 -> writeSimpleHeader(view, 0, TYPE_TUPLE_NUMBER_ARRAY, SUBTYPE_TNA_28_BYTE, length, SIZE)
            20 -> {
                val i = writeSimpleHeader(view, 0, TYPE_TUPLE_NUMBER_ARRAY, SUBTYPE_TNA_20_BYTE, length, SIZE)
                dataview_set_int64(view, i, storageNumber!!)
                i + 8
            }
            16 -> {
                val i = writeSimpleHeader(view, 0, TYPE_TUPLE_NUMBER_ARRAY, SUBTYPE_TNA_16_BYTE, length, SIZE)
                dataview_set_int64(view, i, storageNumber!!)
                dataview_set_int32(view, i + 8, mapNumber!!)
                i + 12
            }
            else -> {
                val i = writeSimpleHeader(view, 0, TYPE_TUPLE_NUMBER_ARRAY, SUBTYPE_TNA_12_BYTE, length, SIZE)
                dataview_set_int64(view, i, storageNumber!!)
                dataview_set_int32(view, i + 8, mapNumber!!)
                dataview_set_int32(view, i + 12, collectionNumber!!)
                i + 16
            }
        }
        for (tupleNumber in this) {
            if (tupleNumber == null) continue
            if (entrySize >= 28) {
                dataview_set_int64(view, end, tupleNumber.storageNumber)
                end += 8
            }
            if (entrySize >= 20) {
                dataview_set_int32(view, end, tupleNumber.mapNumber)
                end += 4
            }
            if (entrySize >= 16) {
                dataview_set_int32(view, end, tupleNumber.collectionNumber)
                end += 4
            }
            dataview_set_int64(view, end, (tupleNumber.txn shl 8) or tupleNumber.partitionNumber.toInt64())
            end += 8
            dataview_set_int32(view, end, tupleNumber.uid)
            end += 4
        }
        return bytes
    }
}