package naksha.psql

import naksha.model.NakshaError
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource
import java.sql.SQLException
import java.util.stream.Stream
import kotlin.test.Test

class NakshaExceptionMapperTest {


    @ParameterizedTest
    @MethodSource("timeoutCauses")
    fun shouldConvertTimeouts(/* Given */ timeoutCause: Exception) {
        // When
        val nakshaException = NakshaExceptionMapper.nakshaExceptionFrom(timeoutCause)

        // Then
        assertEquals(NakshaError.TIMEOUT, nakshaException.error.code)
        assertEquals(timeoutCause, nakshaException.cause)
    }

    @ParameterizedTest
    @MethodSource("sqlCausesAndExpectedCodes")
    fun shouldConvertSqlExceptions(/* Given */ sqlCause: SQLException, code: String) {
        // When
        val nakshaException = NakshaExceptionMapper.nakshaExceptionFrom(sqlCause)

        // Then
        assertEquals(code, nakshaException.error.code)
        assertEquals(sqlCause, nakshaException.cause)
    }

    @Test
    fun shouldConvertUnknownExceptions() {
        // Given
        val customException = CustomTestException()

        // When
        val nakshaException = NakshaExceptionMapper.nakshaExceptionFrom(customException)

        // Then
        assertEquals(NakshaError.EXCEPTION, nakshaException.error.code)
        assertEquals(customException, nakshaException.cause)
    }

    class CustomTestException : Exception()

    companion object {

        @JvmStatic
        fun timeoutCauses(): Stream<Exception> =
            Stream.of(
                java.net.SocketTimeoutException(),
                java.util.concurrent.TimeoutException(),
                java.sql.SQLTimeoutException(),
                org.postgresql.util.PSQLException(null, null, java.net.SocketTimeoutException()) // cause matters!
            )

        @JvmStatic
        fun sqlCausesAndExpectedCodes(): Stream<Arguments> =
            Stream.of(
                NakshaExceptionMapper.ERR_UNINITIALIZED to NakshaError.EXCEPTION,
                NakshaExceptionMapper.ERR_COLLECTION_EXISTS to NakshaError.CONFLICT,
                NakshaExceptionMapper.ERR_COLLECTION_NOT_EXISTS to NakshaError.COLLECTION_NOT_FOUND,
                NakshaExceptionMapper.ERR_CONFLICT to NakshaError.CONFLICT,
                NakshaExceptionMapper.ERR_CHECK_VIOLATION to NakshaError.EXCEPTION,
                NakshaExceptionMapper.ERR_INVALID_PARAMETER_VALUE to NakshaError.ILLEGAL_ARGUMENT,
                NakshaExceptionMapper.ERR_UNIQUE_VIOLATION to NakshaError.CONFLICT,
                NakshaExceptionMapper.ERR_NO_DATA to NakshaError.NOT_FOUND,
                "Unknown sql state" to NakshaError.EXCEPTION
            ).map { (sqlState, expectedCode) ->
               arguments(sqlException(sqlState), expectedCode)
            }

        private fun sqlException(sqlState: String): SQLException =
            SQLException("test_reason", sqlState, 0)
    }
}