@file:Suppress("OPT_IN_USAGE")

package naksha.model

import naksha.base.AnyObject
import naksha.base.Int64
import naksha.base.NotNullProperty
import kotlin.js.JsExport
import kotlin.js.JsName

/**
 * A row reference, being a [proxy][naksha.base.Proxy] version of a [row address][RowAddr].
 */
@JsExport
class RowRef() : AnyObject() {

    /**
     * Create a row-reference from the given individual parameters.
     * @param txn the transaction number.
     * @param uid the unique transaction local identifier.
     * @param flags the flags of the tow.
     */
    @JsName("of")
    constructor(txn: Double, uid: Int, flags: Int) : this() {
        setRaw("txn", txn)
        setRaw("uid", uid)
        setRaw("flags", flags)
    }

    /**
     * Create a row-reference from an internal [row address][RowAddr].
     * @param addr the internal [row address][RowAddr].
     */
    @JsName("fromRowAddr")
    constructor(addr: RowAddr) : this() {
        setRaw("txn", addr.txn.value.toDouble())
        setRaw("uid", addr.uid)
        setRaw("flags", addr.flags)
    }

    companion object RowRef_C {
        val DOUBLE = NotNullProperty<RowRef, Double>(Double::class) { _, _ -> 0.0 }
        val INT = NotNullProperty<RowRef, Int>(Int::class) { _, _ -> 0 }
    }

    /**
     * The transaction number, stored as JavaScript compatible double, so it can be serialized.
     */
    val txn by DOUBLE

    /**
     * The unique transaction local identifier.
     */
    val uid by INT

    /**
     * The row flags.
     */
    val flags by INT

    /**
     * Convert this proxy into an internal [row address][RowAddr].
     * @return this proxy as [row address][RowAddr].
     */
    fun toAddr() : RowAddr = RowAddr(Version(Int64(txn)), uid, flags)
}