@file:Suppress("OPT_IN_USAGE")

package naksha.psql

import naksha.base.AnyObject
import naksha.base.Platform
import naksha.base.Platform.PlatformCompanion.logger
import kotlin.js.JsExport
import kotlin.jvm.JvmField

/**
 * Base class for [PgReader] and [PgWriter] with shared functions.
 * @since 3.0
 */
@JsExport
open class PgReaderWriterBase protected constructor(
    /**
      * The session to which the writer is bound.
      * @since 3.0
      */
    @JvmField
    val session: PgSession,
) {
    private val start = Platform.currentMillis()

    /**
     * If all queries should be logged.
     * @since 3.0
     */
    protected val logQueries = session.options.logLevel?.contains(PgLogLevel.QUERIES) ?: false

    /**
     * If all queries should be explained and then the "explain" should be logged.
     * @since 3.0
     */
    protected val logExplain = session.options.logLevel?.contains(PgLogLevel.EXPLAIN) ?: false

    /**
     * Executes an EXPLAIN above the given statement and returns the plain text for logging purpose.
     * @param connection The connection to use to execute to explain.
     * @param verbose If verbose is requested, which means with {@code ANALYZE, BUFFERS}.
     * @param sql The SQL query to explain.
     * @param args The arguments for the query, same as given to {@code execute}.
     * @return The plain text EXPLAIN above the given statement.
     * @since 11.9.22
     */
    protected fun explain(connection: PgConnection, verbose: Boolean, sql: String, args: Array<Any?>?) : String {
        val EXPLAIN = (if (verbose) "EXPLAIN (ANALYZE, BUFFERS) " else "EXPLAIN (COSTS false) ") + sql;
        try {
            val c: PgCursor = connection.execute(EXPLAIN, args)
            c.use {
                val sb = StringBuilder()
                while (c.next()) {
                    val map = c.map(AnyObject::class)
                    for (value in map.values) {
                        sb.append(value).append("\n");
                    }
                }
                return sb.toString();
            }
        } catch (e: Exception) {
            val msg = "Failed to execute 'EXPLAIN $sql'"
            logger.error(msg, e.message)
            return msg
        }
    }

    /**
     * Log some SQL as debug message for a connection.
     * @param sql The message to log.
     * @param args Arguments for placeholders ({@code {}}) within the given message.
     * @since 11.9.22
     */
    protected fun log(sql: String, vararg args: Any?) {
        val delta = Platform.currentMillis() - start
        logger.info("{}@{}:{}ms: $sql", session.id, session.connectionId, delta, *args)
    }
}