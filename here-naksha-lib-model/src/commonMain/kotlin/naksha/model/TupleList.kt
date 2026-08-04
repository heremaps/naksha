@file:Suppress("OPT_IN_USAGE")

package naksha.model

import naksha.base.PTypedArray
import kotlin.js.JsExport

/**
 * A list of [Tuple].
 */
@JsExport
class TupleList : PTypedArray<Tuple>(Tuple::class)