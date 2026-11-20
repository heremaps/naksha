@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package naksha.psql

import naksha.base.NakshaException
import naksha.base.NakshaError

actual class PgExceptionMapper {
    actual companion object PgExceptionMapper_C {
        internal actual fun sqlState(throwable: Throwable): String? {
            var failure: Throwable? = throwable
            var depth = 0
            while (failure != null && depth++ < 16) {
                val state = failure.asDynamic().sqlerrcode
                if (state is String) return state
                failure = failure.cause
            }
            return null
        }

        actual fun map(throwable: Throwable, sql: String?): NakshaException {
            if (throwable is NakshaException) return throwable
            val code = when (sqlState(throwable)) {
                "42P01" -> NakshaError.COLLECTION_NOT_FOUND
                "23505", "40P01", "40001" -> NakshaError.CONFLICT
                "22023" -> NakshaError.ILLEGAL_ARGUMENT
                "P0002" -> NakshaError.NOT_FOUND
                else -> NakshaError.EXCEPTION
            }
            val message = if (sql == null) throwable.message ?: throwable.toString()
                else "Failed to execute query '$sql', reason: ${throwable.message ?: throwable.toString()}"
            // Native JavaScript Errors are not Kotlin Throwable proxies. Keep the cause on the carrier rather than losing it through the JSON property's typed getter.
            return object : NakshaException(code, message) {
                override val cause: Throwable get() = throwable
            }
        }
    }
}
