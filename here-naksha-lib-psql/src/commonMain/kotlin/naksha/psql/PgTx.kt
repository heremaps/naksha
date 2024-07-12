package naksha.psql

import naksha.model.Luid
import naksha.model.Txn
import kotlin.js.JsExport

/**
 * An abstraction above a PostgresQL transaction.
 */
@Suppress("OPT_IN_USAGE")
@JsExport
interface PgTx {
    /**
     * Returns the transaction number of this transaction.
     * @throws IllegalStateException if the connection is closed.
     */
    fun txn(): Txn

    /**
     * Returns the current unique identifier within the connection.
     * @throws IllegalStateException if the connection is closed.
     */
    fun uid(): Int

    /**
     * Creates a new [LUID][naksha.model.Luid] (local unique identifier) by consuming the current [txn] and [uid].
     * @throws IllegalStateException if the connection is closed.
     */
    fun newGuid(): Luid
}