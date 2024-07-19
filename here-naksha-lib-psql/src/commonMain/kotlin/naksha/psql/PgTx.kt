@file:Suppress("OPT_IN_USAGE")

package naksha.psql

import naksha.base.AtomicInt
import naksha.base.AtomicMap
import naksha.base.AtomicRef
import naksha.base.Platform
import naksha.model.Txn
import kotlin.js.JsExport

/**
 * A transaction.
 * @property txn the transaction number.
 * @property parallel _true_ if queries can be executed in parallel.
 */
@JsExport
open class PgTx(val txn: Txn, val parallel: Boolean) : AutoCloseable {
    // TODO:
    // We make a multi-platform implementation and extend it in Java
    // -> Change newSession, so that it accepts a "parallel" parameter
    // -> Then, in PsqlStore, return PsqlSession, if parallel is requested, overloading only the execution of PgTx in PsqlTx!
    // -> So, queue building is shared code, really just the actual execution is parallel in Java, if requested!
    //
    // (how to read in parallel?)
    // something like: query()
    // -> only sort them into buckets, group by schema, collection, partition
    // -> for some queries we can only use "general"
    // -> we always union the result of the queries
    // -> only read the meta-data, the details can be cached and fetched asynchronously
    // -> offer some helper to fetchAll(rows), which Naksha-Hub need, so for Hub, we always do both
    // -> the row technically should always read only guid, with an option to "fetchAll" later
    //
    // followed by an: readAll() -> result
    // -> use multiple connections in parallel (one per partition, history table, ...)
    // -> fallback is to leave everything to the database
    //
    // (insert|update|delete|purge)Row(row: Row)
    // -> only sort them into buckets, group by schema, collection, partition
    // (writeAll(): result)
    //  -> this needs use (optionally) multiple connections in parallel, therefore multiple bulk loaders
    //  -> this need a special java implementation using multiple threads, the parallel
    //  -> We need an own bulk loader for every connection.
    //     -> The bulk loader need to be able to spread this above different schemas, updating the corresponding transaction log!
    //
    //

    /**
     * The `uid` counter (unique identifier within a transaction).
     */
    val uid: AtomicInt = Platform.newAtomicInt(0)

    /**
     * If the transaction is closed.
     */
    private var closed: AtomicRef<Boolean> = Platform.newAtomicRef(false)

    /**
     * The connections being used for parallel queries, and the [MAIN] connection.
     */
    private val connections: AtomicMap<Int, PgConnection> = Platform.newAtomicMap()

    // TODO: We need a map of ongoing transaction logs, one per schema!

    /**
     * Creates a new [LUID][naksha.model.Luid] (local unique identifier) by consuming the current [txn] and [uid].
     * @throws IllegalStateException if the connection is closed.
     */
    //fun newGuid(): Luid

    fun commit() {
        // Commit all connections!
        TODO("Implement me, close the connection after commit!")
    }

    fun rollback() {
        // Rollback all connections!
        TODO("Implement me, close the connection after rollback!")
    }

    override fun close() {
        TODO("Implement me, rollback, and then close the connection!")
    }
}