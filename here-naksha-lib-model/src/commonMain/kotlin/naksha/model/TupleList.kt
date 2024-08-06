@file:Suppress("OPT_IN_USAGE")

package naksha.model

import naksha.base.ListProxy
import kotlin.js.JsExport

/**
 * A list of [Tuple].
 */
@JsExport
class TupleList : ListProxy<Tuple>(Tuple::class)