@file:Suppress("OPT_IN_USAGE")

package naksha.model

import naksha.base.AbstractMapProxy
import kotlin.js.JsExport

@JsExport
class TransactionCollectionInfoMapProxy :
    AbstractMapProxy<String, TransactionCollectionInfoProxy>(String::class, TransactionCollectionInfoProxy::class) {
}