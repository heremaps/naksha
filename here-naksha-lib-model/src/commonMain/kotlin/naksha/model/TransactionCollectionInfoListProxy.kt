@file:Suppress("OPT_IN_USAGE")

package naksha.model

import naksha.base.P_Map
import kotlin.js.JsExport

@JsExport
class TransactionCollectionInfoMapProxy :
    P_Map<String, TransactionCollectionInfoProxy>(String::class, TransactionCollectionInfoProxy::class) {
}