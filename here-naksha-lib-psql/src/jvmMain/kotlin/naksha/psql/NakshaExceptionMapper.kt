package naksha.psql

import naksha.model.NakshaError
import naksha.model.NakshaException
import org.postgresql.util.PSQLException
import java.sql.BatchUpdateException
import java.sql.SQLException

object NakshaExceptionMapper {
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
     * Maps [throwable] to appropriate [NakshaException] is possible. Main purpose of this function is selecting proper [NakshaError.code] depending on supplied [throwable].
     * Additionally, it can prepend some custom message that will become part of [NakshaException.message] (only if [msgResolver] is supplied).
     *
     * @param throwable original [Throwable] to map
     * @param msgResolver optional [Throwable] consumer to produce message prefix ([String]) from
     */
    fun nakshaExceptionFrom(throwable: Throwable): NakshaException {
        return nakshaExceptionFrom(
            throwable = throwable,
            topLevelCause = null
        )
    }

    /**
     * Same as [nakshaExceptionFrom] that accepts just [throwable] and [msgResolver]
     * The only difference (and the reason for this being private) is [topLevelCause] that is internally handled as potential cause to override the [NakshaException.cause] - needed for cases like [BatchUpdateException]
     */
    private fun nakshaExceptionFrom(
        throwable: Throwable,
        topLevelCause: Throwable? = null
    ): NakshaException {
        return when (throwable) {
            is BatchUpdateException -> nakshaExceptionFromBatch(throwable)
            is PSQLException -> nakshaExceptionFromPsql(throwable, topLevelCause)
            is SQLException -> nakshaExceptionFromSql(throwable, topLevelCause)
            is java.net.SocketTimeoutException -> timeout(throwable, topLevelCause)
            is java.util.concurrent.TimeoutException -> timeout(throwable, topLevelCause)

            else -> NakshaException(
                NakshaError(
                    code = NakshaError.EXCEPTION,
                    msg = "Exception occurred",
                    cause = throwable
                )
            )
        }
    }

    /**
     * [BatchUpdateException] usually wraps other exception - in that case, we extract the cause and handle it directly.
     * If the cause is missing, we treat it as any other [SQLException] - it might contain [SQLException.SQLState] which could be used for determining proper [NakshaError.code]
     */
    private fun nakshaExceptionFromBatch(bue: BatchUpdateException): NakshaException {
        val cause = bue.cause
        return if (cause != null) {
            nakshaExceptionFrom(throwable = cause, topLevelCause = bue)
        } else {
            nakshaExceptionFromSql(sqlException = bue, topLevelCause = null)
        }
    }

    /**
     * [PSQLException] is a subclass of [SQLException] so in most cases it will be handled by [nakshaExceptionFromSql]
     * The only exception (hehe) from this rule is when the cause is [java.net.SocketTimeoutException] - in that case we already know we should treat is as [NakshaError.TIMEOUT]
     */
    private fun nakshaExceptionFromPsql(
        exception: PSQLException,
        topLevelCause: Throwable? = null
    ): NakshaException {
        return when (exception.cause) {
            is java.net.SocketTimeoutException -> timeout(exception, topLevelCause)
            else -> nakshaExceptionFromSql(
                sqlException = exception,
                topLevelCause = topLevelCause
            )
        }
    }

    /**
     * Mapping [SQLException] is based on [SQLException.SQLState] which is mapped to certain [NakshaError.code]
     * Apart from mapping the [NakshaError.code] we also include [NakshaException.cause] from the [sqlException]
     * In some cases, this method is used for mapping the nested exception (see [nakshaExceptionFromBatch] for example), then the [topLevelCause] is used to preserve the original cause.
     */
    private fun nakshaExceptionFromSql(
        sqlException: SQLException,
        topLevelCause: Throwable? = null
    ): NakshaException {
        if (sqlException is java.sql.SQLTimeoutException) {
            return timeout(sqlException, topLevelCause)
        }
        val errorCode = when (sqlException.sqlState) {
            ERR_UNINITIALIZED -> NakshaError.EXCEPTION
            ERR_COLLECTION_EXISTS -> NakshaError.CONFLICT
            ERR_COLLECTION_NOT_EXISTS, UNDEFINED_TABLE -> NakshaError.COLLECTION_NOT_FOUND
            ERR_CONFLICT -> NakshaError.CONFLICT
            ERR_CHECK_VIOLATION -> NakshaError.EXCEPTION
            ERR_INVALID_PARAMETER_VALUE -> NakshaError.ILLEGAL_ARGUMENT
            ERR_UNIQUE_VIOLATION -> NakshaError.CONFLICT
            ERR_NO_DATA -> NakshaError.NOT_FOUND
            else -> NakshaError.EXCEPTION
        }
        return NakshaException(
            NakshaError(
                code = errorCode,
                msg = sqlException.message
                    ?: "SQL Exception occurred (sqlState: '${sqlException.sqlState}')",
                cause = topLevelCause ?: sqlException
            )
        )
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
        topLevelCause: Throwable? = null
    ): NakshaException {
        return NakshaException(
            NakshaError(
                code = NakshaError.TIMEOUT,
                msg = "Timeout exception occurred",
                cause = topLevelCause ?: timeoutException
            )
        )
    }
}