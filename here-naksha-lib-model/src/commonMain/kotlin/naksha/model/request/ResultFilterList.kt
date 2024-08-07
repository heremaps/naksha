@file:Suppress("OPT_IN_USAGE")

package naksha.model.request

import naksha.base.ListProxy
import kotlin.js.JsExport

/**
 * A mutable list of filters.
 */
@JsExport
class ResultFilterList : ListProxy<ResultFilter>(ResultFilter::class)