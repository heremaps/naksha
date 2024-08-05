@file:Suppress("OPT_IN_USAGE")

package naksha.model.request.notification

import naksha.base.Int64
import naksha.base.MapProxy
import naksha.model.Version
import naksha.model.request.ResultRow
import naksha.model.request.ResultRowList
import kotlin.js.JsExport

/**
 * A map where the key is the transaction number (aka [Version]), and the value is a list of [result-rows][ResultRow], order by [uid][naksha.model.Metadata.uid].
 */
@JsExport
class RowsByTxn : MapProxy<Int64, ResultRowList>(Int64::class, ResultRowList::class)
