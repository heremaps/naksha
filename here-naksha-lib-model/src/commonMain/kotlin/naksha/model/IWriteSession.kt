package naksha.model

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/**
 * A write-request.
 */
@OptIn(ExperimentalJsExport::class)
@JsExport
interface IWriteSession: IReadSession {

    /**
     * Commit all pending changes in the current transaction. Returns the underlying connection back into the connection pool.
     * @since 2.0.7
     */
    fun commit()

    /**
     * Rollback (revert) all pending changes in the current transaction. Returns the underlying connection back into the connection pool.
     * @since 2.0.7
     */
    fun rollback()
}