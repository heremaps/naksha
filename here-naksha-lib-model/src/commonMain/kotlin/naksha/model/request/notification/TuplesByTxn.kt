@file:Suppress("OPT_IN_USAGE")

package naksha.model.request.notification

import naksha.base.Int64
import naksha.base.MapProxy
import naksha.model.Version
import naksha.model.request.ResultTuple
import naksha.model.request.ResultTupleList
import kotlin.js.JsExport

/**
 * A map where the key is the transaction number (aka [Version]), and the value is a list of [result-rows][ResultTuple], order by [uid][naksha.model.Metadata.uid].
 */
@JsExport
class TuplesByTxn : MapProxy<Int64, ResultTupleList>(Int64::class, ResultTupleList::class)
