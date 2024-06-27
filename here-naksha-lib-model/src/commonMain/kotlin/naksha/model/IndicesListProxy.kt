@file:Suppress("OPT_IN_USAGE")

package naksha.model

import naksha.base.AbstractListProxy
import kotlin.js.JsExport

@JsExport
class IndicesListProxy: AbstractListProxy<String>(String::class) {
}