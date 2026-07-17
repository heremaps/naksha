@file:Suppress("OPT_IN_USAGE")

package naksha.model.request.notification

import naksha.base.Int64
import naksha.base.MapProxy
import naksha.base.Version
import naksha.model.request.FeatureTuple
import naksha.model.request.FeatureTupleList
import kotlin.js.JsExport

/**
 * A map where the key is the transaction number (aka [Version]), and the value is a list of [result-rows][FeatureTuple], order by [uid][naksha.model.Metadata.uid].
 */
@JsExport
class TuplesByTxn : MapProxy<Int64, FeatureTupleList>(Int64::class, FeatureTupleList::class)
