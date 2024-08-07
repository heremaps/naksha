@file:Suppress("OPT_IN_USAGE")

package naksha.model.request

import naksha.base.ListProxy
import kotlin.js.JsExport

/**
 * A list of writes to perform.
 */
@JsExport
class WriteList : ListProxy<Write>(Write::class)