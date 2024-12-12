package naksha.psql

import naksha.model.NakshaError
import naksha.model.NakshaException
import org.postgresql.util.PSQLException
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

    fun nakshaExceptionFrom(exception: Exception): NakshaException {
        return when (exception) {
            is PSQLException -> nakshaExceptionFromPsql(exception)
            is SQLException -> nakshaExceptionFromSql(exception)
            is java.net.SocketTimeoutException, is java.util.concurrent.TimeoutException -> timeoutException(
                exception
            )

            else -> NakshaException(
                NakshaError(
                    code = NakshaError.EXCEPTION,
                    msg = "Exception occurred",
                    cause = exception
                )
            )
        }
    }

    private fun nakshaExceptionFromPsql(exception: PSQLException): NakshaException {
        return when (exception.cause) {
            is java.net.SocketTimeoutException -> timeoutException(exception)
            else -> nakshaExceptionFromSql(exception)
        }
    }

    private fun nakshaExceptionFromSql(sqlException: SQLException): NakshaException {
        if (sqlException is java.sql.SQLTimeoutException) {
            return timeoutException(sqlException)
        }
        val errorCode = when (sqlException.sqlState) {
            ERR_UNINITIALIZED -> NakshaError.EXCEPTION
            ERR_COLLECTION_EXISTS -> NakshaError.CONFLICT
            ERR_COLLECTION_NOT_EXISTS -> NakshaError.COLLECTION_NOT_FOUND
            ERR_CONFLICT -> NakshaError.CONFLICT
            ERR_CHECK_VIOLATION -> NakshaError.EXCEPTION
            ERR_INVALID_PARAMETER_VALUE -> NakshaError.ILLEGAL_ARGUMENT
            ERR_UNIQUE_VIOLATION -> NakshaError.CONFLICT
            ERR_NO_DATA -> NakshaError.NOT_FOUND
            else -> NakshaError.EXCEPTION
        }
        val msg = sqlException.message ?: "SQL Exception occurred (sqlState: '${sqlException.sqlState}')"
        return NakshaException(NakshaError(code = errorCode, msg = msg, cause = sqlException))
    }

    private fun timeoutException(timeoutException: Exception): NakshaException {
        return NakshaException(
            NakshaError(
                code = NakshaError.TIMEOUT,
                msg = "Timeout exception occurred",
                cause = timeoutException
            )
        )
    }
}