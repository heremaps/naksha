@file:Suppress("OPT_IN_USAGE")

package naksha.psql

import naksha.base.*
import naksha.model.Guid
import naksha.model.Luid
import naksha.model.Version
import kotlin.js.JsExport
import kotlin.jvm.JvmField

/**
 * A transaction is internally used with [PgSession] to actually execute operations. [PgSession] translates [Request's][naksha.model.request.Request] into internal operations, and execute them in the transaction.
 * @property session the session to which this transaction is bound.
 * @property txn the transaction number.
 * @property parallel _true_ if queries can be executed in parallel.
 */
@JsExport
open class PgTx(val session: PgSession) : AutoCloseable {
    /**
     * The transaction number of this transaction.
     */
    @JvmField
    val txn: Version

    /**
     * The `uid` counter (unique identifier within a transaction).
     */
    @JvmField
    val uid: AtomicInt = Platform.newAtomicInt(0)

    /**
     * The PostgresQL master connection (the control connection) used for this transaction.
     */
    @JvmField
    val conn: PgConnection

    /**
     * The connections being used for parallel queries.
     */
    private val connections: AtomicMap<Int, PgConnection> = Platform.newAtomicMap()

    /**
     * Tests if this transaction is executed in parallel.
     * @return _true_ if the transaction is executed in parallel.
     */
    fun isParallel(): Boolean = connections.isEmpty()

    /**
     * If the transaction is closed.
     */
    private val closed: AtomicRef<Boolean> = Platform.newAtomicRef(false)

    init {
        // TODO: Fix it!
        txn = Version(Int64(0))
        conn = session.storage.newConnection()
    }

    // TODO:
    // We make a multi-platform implementation and extend it in Java
    // -> in PsqlStore, return PsqlSession, if parallel is requested, overloading only the execution of PgTx in PsqlTx!
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

    // TODO: We need a map of ongoing transaction logs, one per schema!

    /**
     * Creates a new [local unique identifier][naksha.model.Luid] by consuming the current [txn], and generating a new [unique transaction local identifier][uid].
     */
    fun newLuid(): Luid = Luid(txn, uid.getAndAdd(1))

    /**
     * Generates a [global unique identifier][Guid] of a feature from the given arguments.
     * @param collectionId the collection-id of the collection in which the feature is located.
     * @param featureId the ID of the feature.
     * @param luid the local unique identifier; defaults to create a new one.
     * @return the global unique identifier of the given local unique identifier.
     */
    fun guidOf(collectionId: String, featureId: String, luid: Luid): Guid = Guid(session.storage.id(), collectionId, featureId, luid)

    //

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