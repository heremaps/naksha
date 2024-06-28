package naksha.psql

import kotlin.js.JsExport

/**
 * Options when acquiring a PostgresQL database connection.
 * @property appName the application name to be registered against the PostgresQL database, appears in the
 * [pg_stat_activity](https://www.postgresql.org/docs/current/monitoring-stats.html#MONITORING-PG-STAT-ACTIVITY-VIEW) table as `name`.
 * @property schema the schema to select as default schema.
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
    val appName: String,
    val schema: String,
    val readOnly: Boolean = false,
    val connectTimeout: Int = 60_000,
    val socketTimeout: Int = 60_000,
    val stmtTimeout: Int = 60_000,
    val lockTimeout: Int = 10_000,
    val useMaster: Boolean = !readOnly
)