package com.here.naksha.lib.plv8.naksha.plv8

/**
 * Options when acquiring a PostgresQL database connection.
 * @property readOnly if the connection should be read-only.
 * @property connectTimeout the time in milliseconds to wait for the TCP handshake.
 * @property socketTimeout the time in milliseconds to wait for the TCP socket when reading or writing from it.
 * @property stmtTimeout the statement-timeout in milliseconds.
 * @property lockTimeout the lock-timeout in milliseconds.
 */
data class PsqlConnectOptions(
    val readOnly:Boolean,
    val connectTimeout : Int = 60_000,
    val socketTimeout : Int = 60_000,
    val stmtTimeout: Int = 60_000,
    val lockTimeout: Int = 60_000
)
