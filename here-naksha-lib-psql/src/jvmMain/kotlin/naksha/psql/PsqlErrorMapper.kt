package naksha.psql

import naksha.model.NakshaError
import naksha.model.NakshaException
import java.sql.SQLException

class PsqlErrorMapper {
    companion object {
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

        fun nakshaExceptionFromSql(sqlException: SQLException): NakshaException {
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
            val msg = sqlException.message ?: "SQL Exception occurred (${sqlException.sqlState})"
            return NakshaException(NakshaError(code = errorCode, msg = msg, cause = sqlException))
        }
    }
}