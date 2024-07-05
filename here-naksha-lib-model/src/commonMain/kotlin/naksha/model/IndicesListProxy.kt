@file:Suppress("OPT_IN_USAGE")

package naksha.model

import naksha.base.ListProxy
import kotlin.js.JsExport

@JsExport
class IndicesListProxy: ListProxy<String>(String::class) {
}