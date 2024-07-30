@file:Suppress("OPT_IN_USAGE", "unused")

package naksha.model

import naksha.base.AnyObject
import naksha.base.Int64
import naksha.base.NotNullProperty
import kotlin.js.JsExport
import kotlin.js.JsName

/**
 * A row reference, being the local unique row identifier (a [proxy][naksha.base.Proxy] version of a [row identifier][RowId]).
 */
@JsExport
class RowRef() : AnyObject() {

    /**
     * Create a row-reference from the given individual parameters.
     * @param txn the transaction number.
     * @param uid the unique transaction local identifier.
     * @param flags the partition number of the row.
     */
    @JsName("of")
    constructor(txn: Double, uid: Int, flags: Int) : this() {
        setRaw("txn", txn)
        setRaw("uid", uid)
        setRaw("flags", flags)
    }

    /**
     * Create a row-reference from an internal [row address][RowId].
     * @param rowId the internal [row identifier][RowId].
     */
    @JsName("fromRowId")
    constructor(rowId: RowId) : this() {
        setRaw("txn", rowId.version.value.toDouble())
        setRaw("uid", rowId.uid)
        setRaw("flags", rowId.flags)
    }

    companion object RowRef_C {
        val DOUBLE = NotNullProperty<RowRef, Double>(Double::class) { _, _ -> 0.0 }
        val FLAGS = NotNullProperty<RowRef, Flags>(Flags::class) { _, _ -> 0 }
    }

    /**
     * The transaction number, stored as JavaScript compatible double, so it can be serialized.
     */
    val txn by DOUBLE

    /**
     * The unique transaction local identifier.
     */
    val uid by FLAGS

    /**
     * The partition number.
     */
    val flags by FLAGS

    /**
     * Convert this proxy into an internal [row identifier][RowId].
     * @return this proxy as [row address][RowId].
     */
    fun toRowId() : RowId = RowId(Version(Int64(txn)), uid, flags)
}