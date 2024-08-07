@file:Suppress("OPT_IN_USAGE")

package naksha.model.objects

import naksha.base.ListProxy
import kotlin.js.JsExport

/**
 * A list of transactions.
 */
@JsExport
class TransactionList : ListProxy<Transaction>(Transaction::class)