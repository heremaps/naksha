@file:Suppress("OPT_IN_USAGE")

package naksha.model.objects

import naksha.base.PTypedArray
import kotlin.js.JsExport

/**
 * A list of transactions.
 */
@JsExport
class NakshaTxList : PTypedArray<NakshaTx>(NakshaTx::class)