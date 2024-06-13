@file:Suppress("OPT_IN_USAGE")

package naksha.model.response

import naksha.base.Int64
import naksha.model.Luid
import naksha.model.Txn
import kotlin.js.JsExport

/**
 * The XYZ namespace stored in the `@ns:com:here:xyz` property of the [NakFeature].
 */
@JsExport
data class Metadata(
    val id: String,
    var txnNext: Int64?,
    var txn: Int64,
    var ptxn: Int64?,
    var fnva1: Int64?,
    var uid: Int?,
    var puid: Int?,
    var version: Int?,
    var geoGrid: Int,
    var flags: Int?,
    var action: Short?,
    var appId: String,
    var author: String?,
    var createdAt: Int64?,
    var updatedAt: Int64,
    var authorTs: Int64?,
    var origin: String?
) {
    fun getLuid(): Luid {
        return Luid(
            txn = Txn(txn),
            uid = uid!!
        )
    }
}
