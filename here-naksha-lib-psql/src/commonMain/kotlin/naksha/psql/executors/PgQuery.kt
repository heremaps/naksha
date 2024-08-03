@file:Suppress("OPT_IN_USAGE")

package naksha.psql.executors

import naksha.model.IMetadataArray
import naksha.psql.PgConnection
import kotlin.js.JsExport
import kotlin.jvm.JvmField

/**
 * A single query to be executed, mainly used when reading features.
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
    /**
     * Executes this query in the current thread, using the given connection, and return the outcome.
     *
     * Internally most will do something like:
     * ```sql
     * SELECT array_agg(rowid) as rowid_arr FROM table WHERE ...
     * ```
     * Optionally, compressed via:
     * ```sql
     * SELECT gzip(array_agg(rowid)) as rowid_arr FROM table WHERE ...
     * ```
     * Optionally may include the feature-ids:
     * ```sql
     * SELECT gzip(string_agg(rowid||id::bytea,'\x00'::bytea))
     *   AS rowid_arr FROM table WHERE ...
     * ```
     * @param conn the connection to use to execute the query.
     * @return the result that are produces.
     */
    fun execute(conn: PgConnection): IMetadataArray {
        conn.execute(sql, arguments)
        TODO("Implement me!")
    }
}