@file:OptIn(ExperimentalJsExport::class)

package naksha.psql

import naksha.base.Int64
import naksha.model.Version
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/**
 * The PostgresQL transaction information object.
 * @since 3.0.0
 */
@JsExport
data class PgTxn(
    /**
     * The transaction-number.
     * @since 3.0.0
     */
    val txn: Int64,

    /**
     * The Epoch timestamp _(milliseconds since 1 January 1970)_ when the transaction started.
     * @since 3.0.0
     */
    val epoch: Int64,

    /**
     * The version object, created from [txn].
     */
    val version: Version = Version(txn)
)