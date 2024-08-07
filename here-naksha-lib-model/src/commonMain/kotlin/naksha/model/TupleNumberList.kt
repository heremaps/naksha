@file:Suppress("OPT_IN_USAGE")

package naksha.model

import naksha.base.ListProxy
import naksha.base.Platform
import naksha.base.PlatformDataViewApi.PlatformDataViewApiCompanion.dataview_set_int32
import naksha.base.PlatformDataViewApi.PlatformDataViewApiCompanion.dataview_set_int64
import kotlin.js.JsExport

/**
 * A list of [tuple-numbers][TupleNumber].
 */
@JsExport
class TupleNumberList : ListProxy<TupleNumber>(TupleNumber::class) {
    /**
     * Convert this list into a byte-array, that can be read using [tuple-number byte-array][TupleNumberByteArray].
     * @return the list as byte-array.
     */
    fun toByteArray(): ByteArray {
        val length = this.size
        val SIZE = length * 20
        val bytes = ByteArray(SIZE)
        val view = Platform.newDataView(bytes)
        var end = 0
        for (tupleNumber in this) {
            if (tupleNumber == null) continue
            dataview_set_int64(view, end, tupleNumber.storeNumber)
            dataview_set_int64(view, end + 8, tupleNumber.version.txn)
            dataview_set_int32(view, end + 16, tupleNumber.uid)
            end += 20
        }
        return if (end == bytes.size) bytes else bytes.copyOf(end)
    }
}