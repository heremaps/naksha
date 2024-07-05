@file:Suppress("OPT_IN_USAGE")

package naksha.model

import naksha.base.MapProxy
import kotlin.js.JsExport

@JsExport
class TransactionCollectionInfoMapProxy :
    MapProxy<String, TransactionCollectionInfoProxy>(String::class, TransactionCollectionInfoProxy::class) {
}