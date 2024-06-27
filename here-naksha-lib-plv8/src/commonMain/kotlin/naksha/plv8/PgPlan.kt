@file:OptIn(ExperimentalJsExport::class)

package naksha.plv8

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/**
 * The PostgresQL plan (aka prepared statement), this API is designed so that it matches the way the
 * [PLV8](https://plv8.github.io/) engine exposes plans. In Java this is a thin wrapper
 * around a JDBC `PreparedStatement`.
 */
@JsExport
interface PgPlan : AutoCloseable {
    /**
     * Execute the prepared plan with the given arguments. The types must match to the prepared statement.
     * @param args The arguments to be set at $n position, where $1 is the first array element.
     * @return Either the number of affected rows or the rows.
     */
    fun execute(args: Array<Any?>? = null): Any

    /**
     * Execute the prepared plan with the given arguments. The types must match to the prepared statement.
     * @param args The arguments to be set at $n position, where $1 is the first array element.
     * @return A cursor into the result-set.
     */
    fun cursor(args: Array<Any?>? = null): PgCursor

    /**
     * Frees the plan, in [PLV8](https://plv8.github.io/#database-access-via-spi) invokes
     * [free](https://plv8.github.io/#-code-preparedplan-free-code-).
     */
    override fun close()
}