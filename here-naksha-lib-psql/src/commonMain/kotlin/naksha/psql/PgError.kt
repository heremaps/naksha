@file:Suppress("OPT_IN_USAGE")

package naksha.psql

import kotlin.js.JsExport

/**
 * An error as reported by PostgresQL.
 * @since 3.0.0
 */
@JsExport
data class PgError(
    /**
     * The last [PostgreSQL Error Code](https://www.postgresql.org/docs/current/errcodes-appendix.html) or _null_, if no error has happened.
     */
    val errNo: String,

    /**
     * The human-readable error message.
     */
    val errMsg: String
)