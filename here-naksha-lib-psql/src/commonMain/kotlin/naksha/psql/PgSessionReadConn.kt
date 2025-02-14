@file:OptIn(ExperimentalJsExport::class)

package naksha.psql

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/**
 * A small wrapper for a read-only PostgresQL connection. This is either a shared one (that must not be closed) or an individual read-only connection, which must be closed after usage.
 * @since 3.0
 */
@JsExport
class PgSessionReadConn internal constructor(val conn: PgConnection, private var closeUnderlying: Boolean) : AutoCloseable {
    override fun close() {
        if (closeUnderlying) conn.close()
    }
}