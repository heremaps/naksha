package naksha.psql

import java.sql.Connection
import java.sql.PreparedStatement

/**
 * The Java implementation of a plan.
 */
class PsqlPlan(internal val query: PsqlQuery, conn: Connection) : PgPlan {
    val stmt: PreparedStatement = query.prepare(conn)
    var closed: Boolean = false

    /**
     * Execute the prepared plan with the given arguments. The types must match to the prepared statement.
     * @param args the arguments to be set at $n position, where $1 is the first array element.
     * @return either the number of affected rows or the rows.
     */
    override fun execute(args: Array<Any?>?): PgCursor {
        check(!closed)
        if (!args.isNullOrEmpty()) query.bindArguments(stmt, args)
        stmt.execute()
        return PsqlCursor(stmt, false)
    }

    /**
     * Adds the prepared statement with the given arguments into batch-execution queue. This requires a mutation query like UPDATE or
     * INSERT. The types of the arguments must match to the prepared statement. This does not return anything, it queues the execution
     * until [executeBatch] is invoked.
     * @param args the arguments to be set at $n position, where $1 is the first array element.
     */
    override fun addBatch(args: Array<Any?>?) {
        check(!closed)
        if (!args.isNullOrEmpty()) query.bindArguments(stmt, args)
        stmt.addBatch()
    }

    /**
     * Execute all queued (batched) executions.
     * @return an array with the amount of effected rows by each queued execution.
     */
    override fun executeBatch(): IntArray {
        return stmt.executeBatch()
    }

    override fun close() {
        val closed = this.closed
        this.closed = true
        if (!closed) stmt.close()
    }
}