package naksha.psql

import kotlin.js.JsExport

/**
 * The PostgresQL plan (prepared statement), this API is designed so that it matches the way the
 * [PLV8](https://plv8.github.io/) engine exposes plans. In Java this is a thin wrapper around a JDBC `PreparedStatement`.
 */
@Suppress("OPT_IN_USAGE")
@JsExport
interface PgPlan : AutoCloseable {
    /**
     * Execute the prepared plan with the given arguments. The types must match to the prepared statement.
     * @param args the arguments to be set at $n position, where $1 is the first array element.
     * @return either the number of affected rows or the rows.
     */
    fun execute(args: Array<Any?>? = null): PgCursor

    /**
     * Adds the prepared statement with the given arguments into batch-execution queue. This requires a mutation query like UPDATE or
     * INSERT. The types of the arguments must match to the prepared statement. This does not return anything, it queues the execution
     * until [executeBatch] is invoked.
     * @param args the arguments to be set at $n position, where $1 is the first array element.
     */
    fun addBatch(args: Array<Any?>? = null)

    /**
     * Execute all queued (batched) executions.
     * @return an array with the amount of effected rows by each queued execution.
     */
    fun executeBatch(): IntArray

    /**
     * Frees the plan, in [PLV8](https://plv8.github.io/#database-access-via-spi) invokes
     * [free](https://plv8.github.io/#-code-preparedplan-free-code-).
     */
    override fun close()
}