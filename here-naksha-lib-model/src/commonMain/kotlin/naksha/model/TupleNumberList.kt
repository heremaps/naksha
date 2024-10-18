@file:Suppress("OPT_IN_USAGE")

package naksha.model

import naksha.base.Int64
import naksha.base.ListProxy
import naksha.base.Platform
import naksha.base.PlatformDataViewApi.PlatformDataViewApiCompanion.dataview_set_int32
import naksha.base.PlatformDataViewApi.PlatformDataViewApiCompanion.dataview_set_int64
import naksha.model.TupleNumberByteArray.TupleNumberByteArray_C.FULL_VARIANT
import kotlin.js.JsExport

/**
 * A list of [tuple-numbers][TupleNumber].
 */
@JsExport
class TupleNumberList : ListProxy<TupleNumber>(TupleNumber::class) {
    /**
     * Convert this list into a byte-array, that can be read using [tuple-number byte-array][TupleNumberByteArray].
     * @return the list as byte-array, readable via [tuple-number byte-array][TupleNumberByteArray].
     */
    fun toByteArray(): ByteArray {
        var length = this.size
        if (length == 0) return byteArrayOf()
        // The first loop detects if all tuples share the same storage, which allows a compact encoding.
        // A side effect is, that we can find out how many null values we have.
        // Note, this code is not thread safe, no other thread must modify the list while we iterate it.
        var storageNumber: Int64? = null
        for (tupleNumber in this) {
            if (tupleNumber == null) {
                length--
            } else if (storageNumber === null) {
                storageNumber = tupleNumber.storageNumber
            } else if (storageNumber != tupleNumber.storageNumber) {
                storageNumber = FULL_VARIANT
            }
        }
        if (storageNumber == null) return byteArrayOf()
        val entrySize = if (storageNumber === FULL_VARIANT) 32 else 24
        val SIZE = 8 + length * entrySize
        val bytes = ByteArray(SIZE)
        val view = Platform.newDataView(bytes)
        dataview_set_int64(view, 0, storageNumber)
        var end = 8
        for (tupleNumber in this) {
            if (tupleNumber == null) continue
            dataview_set_int64(view, end, tupleNumber.storeNumber)
            dataview_set_int64(view, end + 8, tupleNumber.version.txn)
            dataview_set_int32(view, end + 16, tupleNumber.uid)
            dataview_set_int32(view, end + 20, tupleNumber.flags)
            if (entrySize == 32) dataview_set_int64(view, end + 24, tupleNumber.storageNumber)
            end += entrySize
        }
        return bytes
    }
}