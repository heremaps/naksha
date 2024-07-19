package naksha.model

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlin.jvm.JvmField

/**
 * The Local Unique Identifier, being a 96-bit value, persisting out of the transaction number and a 32-bit integer that uniquely
 * identifies the state of a feature in a specific transaction. The uid allows ordering changes within a transaction and to process
 * transactions step by step. It is stringified to: `{year}:{month}:{day}:{seq}:{uid}`
 */
@OptIn(ExperimentalJsExport::class)
@JsExport
data class Luid (@JvmField val txn: Txn, @JvmField val uid: Int) {
    private lateinit var _string: String

    /**
     * Return the LUID in URN form.
     * @return the LUID in URN form.
     */
    override fun toString(): String {
        if (!this::_string.isInitialized) _string = "$txn:$uid"
        return _string
    }
}