@file:Suppress("OPT_IN_USAGE")

package naksha.model.request

import naksha.base.ListProxy
import kotlin.js.JsExport

/**
 * A list of result rows.
 */
@JsExport
class ResultRowList : ListProxy<ResultRow>(ResultRow::class)