@file:Suppress("OPT_IN_USAGE")

package naksha.model

import naksha.base.Int64
import kotlin.js.JsExport
import kotlin.js.JsName

/**
 * A 128-bit address that uniquely refers a row in a collection. As the highest 10 bit of the transaction number are not used, this uses a maximum of 118-bit, which means, theoretically this can be encoded into a [UUID version 4](https://datatracker.ietf.org/doc/html/rfc4122) or [UUID version 8](https://uuid6.github.io/uuid6-ietf-draft/).
 *
 * @property txn the transaction number.
 * @property uid the unique identifier within the transaction.
 * @property flags the flags, which allow to locate where the row is stored, so in which partition, and if it is in HEAD, DELETED or HISTORY. Additionally, this provides information about the state of the feature, so if being CREATED, UPDATED or DELETED in this state, and how the data parts are encoded.
 */
@JsExport
data class RowAddr(val txn: Version, val uid: Int, val flags: Flags) {

    /**
     * Create the row address from the given [row reference][RowRef].
     * @param ref the [row reference][RowRef] to read.
     */
    @JsName("fromRowRef")
    constructor(ref: RowRef) : this(Version(Int64(ref.txn)), ref.uid, ref.flags)

    /**
     * Convert this internal row address into a [row-reference][RowRef].
     * @return this address as [row-reference][RowRef].
     */
    fun toRowRef(): RowRef = RowRef(txn.value.toDouble(), uid, flags)
}
