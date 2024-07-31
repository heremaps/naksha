@file:Suppress("OPT_IN_USAGE")

package naksha.psql.executors

import naksha.model.NakshaError.NakshaErrorCompanion.ILLEGAL_STATE
import naksha.model.NakshaException
import naksha.model.RowIdArray
import naksha.psql.PgConnection
import kotlin.js.JsExport
import kotlin.jvm.JvmField

/**
 * A query.
 */
@JsExport
class PgQuery internal constructor(
    /**
     * The reference to the query plan that generated this query.
     */
    @JvmField
    val plan: PgQueryPlan,

    /**
     * The SQL to execute.
     */
    @JvmField
    internal val sql: String,

    /**
     * The arguments that need to be used for the query.
     */
    @JvmField
    internal val arguments: Array<Any?>?
) {
    private var _results: RowIdArray? = null

    /**
     * The result that is produces by [execute], basically:
     * ```sql
     * SELECT array_agg(rowid) as rowid_arr FROM table WHERE ...
     * ```
     * Optionally, compressed via:
     * ```sql
     * SELECT gzip(array_agg(rowid)) as rowid_arr FROM table WHERE ...
     * ```
     */
    val results: RowIdArray
        get() = _results ?: throw NakshaException(ILLEGAL_STATE, "Invoke 'execute' before reading the results")

    /**
     * Executes this query in the current thread, using the given connection, fetches the results, and stores them in [results].
     * @param conn the connection to use to execute the query.
     */
    fun execute(conn: PgConnection) {
        TODO("Implement me!")
    }
}