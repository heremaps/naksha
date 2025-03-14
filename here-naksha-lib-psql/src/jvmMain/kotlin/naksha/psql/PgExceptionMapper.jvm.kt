@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package naksha.psql

import naksha.model.NakshaError
import naksha.model.NakshaError.NakshaErrorCompanion.EXCEPTION
import naksha.model.NakshaException
import org.postgresql.util.PSQLException
import java.sql.BatchUpdateException
import java.sql.SQLException

actual class PgExceptionMapper {
    actual companion object PgExceptionMapper_C {
        @JvmOverloads
        actual fun map(throwable: Throwable, sql: String?): NakshaException {
            if (throwable is NakshaException) return throwable
            return nakshaExceptionFrom(
                throwable = throwable,
                topLevelCause = null,
                sql = sql
            )
        }

        const val ERR_UNINITIALIZED = "N0000"
        const val ERR_COLLECTION_EXISTS = "N0001"
        const val ERR_COLLECTION_NOT_EXISTS = "N0002"
        const val ERR_CONFLICT = "N0003"
        const val ERR_FEATURE_NOT_EXISTS = "N0004"
        const val ERR_FATAL = "NX000"
        const val ERR_CHECK_VIOLATION = "23514"
        const val ERR_INVALID_PARAMETER_VALUE = "22023"
        const val ERR_UNIQUE_VIOLATION = "23505"
        const val ERR_NO_DATA = "02000"
        const val UNDEFINED_TABLE = "42P01"


        /**
         * Internal variant of [nakshaExceptionFrom].
         *
         * The difference (and the reason for this being private) is [topLevelCause], that is internally handled as potential cause to override the [NakshaException.cause] - needed for cases like [BatchUpdateException].
         *
         * @param throwable original [Throwable] to map.
         * @param topLevelCause potential cause to override the [NakshaException.cause].
         * @param sql if the exception happens while executing some SQL query, the query that has been executed.
         */
        private fun nakshaExceptionFrom(
            throwable: Throwable,
            topLevelCause: Throwable?,
            sql: String?,
        ): NakshaException {
            return when (throwable) {
                is BatchUpdateException -> nakshaExceptionFromBatch(throwable, sql)
                is PSQLException -> nakshaExceptionFromPsql(throwable, topLevelCause, sql)
                is SQLException -> nakshaExceptionFromSql(throwable, topLevelCause, sql)
                is java.net.SocketTimeoutException -> timeout(throwable, topLevelCause, sql)
                is java.util.concurrent.TimeoutException -> timeout(throwable, topLevelCause, sql)
                else -> NakshaException(
                    NakshaError(
                        code = EXCEPTION,
                        msg = if (sql != null) "Exception while executing SQL query '$sql'" else
                              throwable.message ?: "Exception without message",
                        cause = throwable
                    )
                )
            }
        }

        /**
         * [BatchUpdateException] usually wraps other exception - in that case, we extract the cause and handle it directly.
         *
         * If the cause is missing, we treat it as any other [SQLException] - it might contain [SQLException.SQLState] which could be used for determining proper [NakshaError.code].
         * @param bue the batch-update-exception.
         * @param sql if the exception happens while executing some SQL query, the query that has been executed.
         */
        private fun nakshaExceptionFromBatch(bue: BatchUpdateException, sql: String? = null): NakshaException {
            val cause = bue.cause
            return if (cause != null) {
                nakshaExceptionFrom(throwable = cause, topLevelCause = bue, sql = sql)
            } else {
                nakshaExceptionFromSql(sqlException = bue, topLevelCause = null, sql = sql)
            }
        }

        /**
         * [PSQLException] is a subclass of [SQLException], so in most cases it will be handled by [nakshaExceptionFromSql].
         *
         * The only exception to this rule is when the cause is [java.net.SocketTimeoutException] - in that case we already know we should treat is as [NakshaError.TIMEOUT].
         *
         * @param exception the [PSQLException].
         * @param topLevelCause potential cause to override the [NakshaException.cause].
         * @param sql if the exception happens while executing some SQL query, the query that has been executed.
         */
        private fun nakshaExceptionFromPsql(
            exception: PSQLException,
            topLevelCause: Throwable? = null,
            sql: String? = null
        ): NakshaException {
            return when (exception.cause) {
                is java.net.SocketTimeoutException -> timeout(exception, topLevelCause)
                else -> nakshaExceptionFromSql(
                    sqlException = exception,
                    topLevelCause = topLevelCause,
                    sql = sql
                )
            }
        }

        /**
         * Mapping [SQLException] is based on [SQLException.SQLState] which is mapped to certain [NakshaError.code].
         *
         * Apart from mapping the [NakshaError.code] we also include [NakshaException.cause] from the [sqlException]. In some cases, this method is used for mapping the nested exception (see [nakshaExceptionFromBatch] for example), then the [topLevelCause] is used to preserve the original cause.
         *
         * @param sqlException the SQL exception.
         * @param topLevelCause potential cause to override the [NakshaException.cause].
         * @param sql if the exception happens while executing some SQL query, the query that has been executed.
         */
        private fun nakshaExceptionFromSql(
            sqlException: SQLException,
            topLevelCause: Throwable? = null,
            sql: String? = null
        ): NakshaException {
            if (sqlException is java.sql.SQLTimeoutException) {
                return timeout(sqlException, topLevelCause)
            }
            val errorCode = when (sqlException.sqlState) {
                ERR_UNINITIALIZED -> EXCEPTION
                ERR_COLLECTION_EXISTS -> NakshaError.COLLECTION_EXISTS
                ERR_COLLECTION_NOT_EXISTS, UNDEFINED_TABLE -> NakshaError.COLLECTION_NOT_FOUND
                ERR_CONFLICT -> NakshaError.CONFLICT
                ERR_CHECK_VIOLATION -> EXCEPTION
                ERR_INVALID_PARAMETER_VALUE -> NakshaError.ILLEGAL_ARGUMENT
                ERR_UNIQUE_VIOLATION -> NakshaError.CONFLICT
                ERR_NO_DATA -> NakshaError.NOT_FOUND
                else -> EXCEPTION
            }
            var msg = if (sql != null) "Failed to execute query '$sql', reason: " else "Failed to execute unknown query, reason: "
            msg += sqlException.message ?: "${sqlException.sqlState}: No message"
            return NakshaException(NakshaError(code = errorCode, msg = msg, cause = topLevelCause ?: sqlException))
        }

        /**
         * Creates [NakshaException] for [NakshaError.TIMEOUT]
         *
         * @param timeoutException the actual timeout exception that is reason for this to happen
         * @param topLevelCause optional wrapper for the [timeoutException], present only if [timeoutException] is nested
         * @param msgResolver optional resolver for [NakshaException.message] prefix
         */
        private fun timeout(
            timeoutException: Exception,
            topLevelCause: Throwable? = null,
            sql: String? = null
        ): NakshaException {
            return NakshaException(
                NakshaError(
                    code = NakshaError.TIMEOUT,
                    msg = "Timeout while executing '${sql ?: "unknown query"}'",
                    cause = topLevelCause ?: timeoutException
                )
            )
        }
    }
}