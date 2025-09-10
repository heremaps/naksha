@file:Suppress("OPT_IN_USAGE")

package naksha.psql

import kotlin.js.JsExport

/**
 * A singleton for different log levels to be set at the [naksha.model.SessionOptions].
 * @since 3.0
 */
@JsExport
class PgLogLevel {
    companion object LogLevel_C {
        /**
         * Logging disabled, same as `null`.
         * @since 3.0
         */
        const val NONE = "none";

        /**
         * Log all queries.
         * @since 3.0
         */
        const val QUERIES = "queries";

        /**
         * Log an `explain` for all executed queries.
         * @since 3.0
         */
        const val EXPLAIN = "explain";

        /**
         * Log an `explain` for all executed queries, plus the query itself.
         * @since 3.0
         */
        const val EXPLAIN_AND_QUERIES = "explain+queries";
    }
}