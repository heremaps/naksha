@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package naksha.psql

import naksha.base.NakshaError
import naksha.base.NakshaException

/**
 * Helper to map native exceptions into [NakshaException].
 * @since 3.0
 */
expect class PgExceptionMapper {
    companion object PgExceptionMapper_C {
        /**
         * Maps [Throwable] to appropriate [NakshaException].
         *
         * Main purpose of this function is selecting proper [NakshaError.code] depending on supplied [Throwable]. Additionally, it can prepend some custom message that will become part of [NakshaException.message].
         *
         * @param throwable original [Throwable] to map.
         * @param sql if the exception happens while executing some SQL query, the query that has been executed.
         * @return the [NakshaException] that maps the given [Throwable].
         * @since 3.0
         */
        fun map(throwable: Throwable, sql: String? = null): NakshaException
    }
}