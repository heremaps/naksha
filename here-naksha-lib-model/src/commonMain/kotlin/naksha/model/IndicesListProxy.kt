@file:Suppress("OPT_IN_USAGE")

package naksha.model

import naksha.base.P_List
import kotlin.js.JsExport

@JsExport
class IndicesListProxy: P_List<String>(String::class) {
}