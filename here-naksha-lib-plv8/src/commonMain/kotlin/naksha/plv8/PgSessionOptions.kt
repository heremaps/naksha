package naksha.plv8

import kotlin.js.JsExport

/**
 * Options when acquiring a PostgresQL database connection.
 * @property readOnly if the connection should be read-only.
 * @property connectTimeout the time in milliseconds to wait for the TCP handshake.
 * @property socketTimeout the time in milliseconds to wait for the TCP socket when reading or writing from it.
 * @property stmtTimeout the statement-timeout in milliseconds.
 * @property lockTimeout the lock-timeout in milliseconds.
 * @property useMaster if connections should be established against the master node; only relevant for [readOnly] mode to avoid
 * replication lag.
 */
@Suppress("OPT_IN_USAGE")
@JsExport
data class PgSessionOptions(
    val readOnly: Boolean,
    val connectTimeout: Int = 60_000,
    val socketTimeout: Int = 60_000,
    val stmtTimeout: Int = 60_000,
    val lockTimeout: Int = 60_000,
    val useMaster: Boolean = if (readOnly) false else true
)