@file:OptIn(ExperimentalJsExport::class)

package naksha.psql

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/**
 * A small wrapper for a read-only PostgresQL connection. This is either a shared one (that must not be closed) or an individual read-only connection, which must be closed after usage.
 *
 * @param conn the PostgresQL connection to wrap.
 * @param closeUnderlying if the wrapped connection should be closed when this wrapper is closed.
 * @since 3.0
 * @see PgSession.newReadConnection
 * @see PgSession.readConnection
 */
@JsExport
class PgSessionReadConn internal constructor(
    /**
     * The wrapped connection.
     * @since 3.0
     */
    val conn: PgConnection,
    /**
     * If the wrapped connection should be closed, when the wrapper is closed.
     *
     * This is automatically considered when [close] of this wrapper is called.
     * @since 3.0
     */
    private var closeUnderlying: Boolean
) : AutoCloseable {
    override fun close() {
        if (closeUnderlying) conn.close()
    }
}