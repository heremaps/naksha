package naksha.psql

import kotlin.js.JsExport

/**
 * Options when acquiring PostgresQL database connections.
 * @property appName the application name to be registered against the PostgresQL database, appears in the
 * [pg_stat_activity](https://www.postgresql.org/docs/current/monitoring-stats.html#MONITORING-PG-STAT-ACTIVITY-VIEW) table as `name`.
 * @property schema the schema to use.
 * @property appId the application identifier of the change, stored in the [naksha.model.Metadata.appId].
 * @property author the author of the change, stored in the [naksha.model.Metadata.author]. Special rules apply for author handling.
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
data class PgOptions(
    val appName: String,
    val schema: String,
    var appId: String,
    var author: String? = null,
    val readOnly: Boolean = false,
    val connectTimeout: Int = 60_000,
    val socketTimeout: Int = 60_000,
    val stmtTimeout: Int = 60_000,
    val lockTimeout: Int = 10_000,
    val useMaster: Boolean = !readOnly
)