package naksha.model

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@OptIn(ExperimentalJsExport::class)
@JsExport
class Luid (
    val txn: Txn,
    val uid: Int
) {
    private lateinit var _string: String

    override fun toString(): String {
        if (!this::_string.isInitialized) _string = "$txn:$uid"
        return _string
    }
}