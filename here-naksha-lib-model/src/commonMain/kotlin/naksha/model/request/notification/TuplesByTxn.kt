@file:Suppress("OPT_IN_USAGE")

package naksha.model.request.notification

import naksha.base.Int64
import naksha.base.PTypedMap
import naksha.base.Version
import naksha.model.TupleList
import kotlin.js.JsExport

/**
 * A map where the key is the transaction number (aka [Version]), and the value is a list of [naksha.model.Tuple].
 */
@JsExport
class TuplesByTxn : PTypedMap<Int64, TupleList>(Int64::class, TupleList::class)
