@file:Suppress("OPT_IN_USAGE")

package naksha.model.request

import naksha.model.Naksha
import kotlin.js.JsExport

/**
 * Perform a read from the transaction log.
 *
 * Note that the [featureIds] are the [versions][naksha.model.Version] converted to a string, which can be done by calling [transaction.toId()][naksha.model.Version.toId], which basically is just the [transaction number][naksha.model.Version.txn] as string.
 */
@JsExport
open class ReadTransactions : ReadFeatures(Naksha.VIRT_TRANSACTIONS)
