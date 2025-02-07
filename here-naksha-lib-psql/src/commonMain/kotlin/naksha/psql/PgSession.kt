@file:Suppress("OPT_IN_USAGE")

package naksha.psql

import naksha.base.*
import naksha.jbon.JbDictionary
import naksha.model.*
import naksha.model.NakshaError.NakshaErrorCompanion.EXCEPTION
import naksha.model.NakshaError.NakshaErrorCompanion.ILLEGAL_ARGUMENT
import naksha.model.NakshaError.NakshaErrorCompanion.ILLEGAL_STATE
import naksha.model.objects.NakshaCollection
import naksha.model.objects.NakshaMap
import naksha.model.request.*
import naksha.model.request.WriteRequest
import naksha.model.objects.NakshaTransaction
import naksha.psql.executors.PgReader
import naksha.psql.executors.PgWriter
import naksha.psql.executors.write.BulkWriteExecutor
import naksha.psql.executors.write.InstantWriteExecutor
import kotlin.js.JsExport
import kotlin.jvm.JvmField

/**
 * A session linked to a PostgresQL database.
 *
 * This object is created when [IStorage.newReadSession] or [IStorage.newWriteSession] are called, creating a session is cheap, without database access.
 * @since 3.0.0
 */
@JsExport
open class PgSession(
    /**
     * The storage to which the session is bound.
     * @since 3.0.0
     */
    @JvmField val pgStorage: PgStorage,

    /**
     * The session options to use, if any specific.
     * @since 3.0.0
     */
    options: SessionOptions?,

    /**
     * If this session is read-only.
     * @since 3.0.0
     */
    @JvmField val readOnly: Boolean
) : IWriteSession, IReadSession, ISession {

    override val storage
        get() = pgStorage

    /**
     * Assert that this session mutable and not closed.
     * - Throws [NakshaError.ILLEGAL_STATE] if this session is [readOnly] or [closed][isClosed].
     */
    fun assertMutable() {
        if (readOnly) throw NakshaException(ILLEGAL_STATE, "Failed to acquire mutable connection from read-only session")
        assertOpen()
    }

    /**
     * Assert that this session mutable.
     * - Throws [NakshaError.ILLEGAL_STATE] if this session is [closed][isClosed].
     */
    fun assertOpen() {
        if (_closed) throw NakshaException(ILLEGAL_STATE, "Connection closed")
    }

    private var optionsValue: SessionOptions = options ?: SessionOptions()

    /**
     * The options when opening new connections. The options are mostly immutable, except for the timeout values, for which there are dedicated setter.
     */
    override val options: SessionOptions
        get() = optionsValue

    override var socketTimeout: Int
        get() = options.socketTimeout
        set(value) {
            optionsValue = options.copy(socketTimeout = value)
        }

    override var stmtTimeout: Int
        get() = options.stmtTimeout
        set(value) {
            optionsValue = options.copy(stmtTimeout = value)
        }

    override var lockTimeout: Int
        get() = options.lockTimeout
        set(value) {
            optionsValue = options.copy(lockTimeout = value)
        }

    /**
     * The PostgresQL database connection currently being used; if any.
     */
    var pgConnection: PgConnection? = null
        private set

    /**
     * Tests if reading in parallel is applicable for this session.
     * @return _true_ if multiple read-connections can be used in parallel for this session; _false_ otherwise.
     */
    fun mayReadParallel(): Boolean = pgConnection == null && options.parallel

    /**
     * Opens a new read connection for the session.
     * - Throws [NakshaError.ILLEGAL_STATE] if the session is [closed][isClosed] or the session may [not be read in parallel right now][mayReadParallel].
     * @return a new read-only connection for this session, which must be closed when done reading.
     */
    fun newReadConnection(): PgConnection {
        assertOpen()
        if (!mayReadParallel()) throw NakshaException(ILLEGAL_STATE, "Session can't be read in parallel right now")
        return storage.newConnection(options, readOnly, this::initConnection)
    }

    /**
     * Returns a single shared PostgresQL session connection.
     *
     * If none is yet acquired, acquires on from the pools and returns it. This connection is shared and must not be closed, it will automatically be closed when either [rollback] or [commit] are invoked.
     * @return the shared PostgresQL connection.
     */
    fun useConnection(): PgConnection {
        assertOpen()
        var conn = pgConnection
        if (conn == null) {
            conn = storage.newConnection(options, readOnly, this::initConnection)
            pgConnection = conn
        }
        return conn
    }

    /**
     * Internally invoked by [useConnection] to initialize the connection.
     * @param conn the connection to initialize.
     * @param query the query to executed, can be modified, when overriding this method.
     */
    protected open fun initConnection(conn: PgConnection, query: String) {
        // This is the same as the default implementation, when init is null, see PgStorage::newConnection
        conn.execute(query).close()
    }

    override val uid: AtomicInt = AtomicInt(0)

    /**
     * The last [PostgreSQL Error Code](https://www.postgresql.org/docs/current/errcodes-appendix.html) or _null_, if no error has happened.
     */
    var error: PgError? = null
        private set

    /**
     * The transaction of the session, if any.
     * @since 3.0.0
     */
    var transaction: NakshaTransaction? = null
        private set

    override fun getTransaction(): NakshaTransaction? = transaction

    /**
     * Return the current transaction, if no transaction started yet, starts a new one.
     *
     * - Throws [NakshaError.ILLEGAL_STATE] if this is session is [readOnly] or [closed][isClosed].
     * @return the current transaction.
     */
    override fun useTransaction(): NakshaTransaction {
        assertMutable()
        assertOpen()
        var tx = transaction
        if (tx == null) {
            val txn = pgStorage.adminMap.newTxn(useConnection())
            tx = NakshaTransaction(txn.number, txn.epoch)
            transaction = tx
        }
        return tx
    }

    override fun execute(request: Request): Response {
        when (request) {
            is WriteRequest -> {
                useTransaction()
                val response = PgWriter(this, request, BulkWriteExecutor(this)).execute()
                return response
            }

            is ReadRequest -> {
                val response = PgReader(this, request).execute()
                if (transaction == null) {
                    // If this read was performed on a blank session, without a pending transaction, then we can release the connection.
                    pgConnection?.close()
                    pgConnection = null
                }
                return response
            }

            else -> throw NakshaException(ILLEGAL_ARGUMENT, "Unknown request")
        }
    }

    /**
     * Reset the session into the initial state.
     */
    private fun clear() {
        uid.set(0)
        error = null
        transaction = null
        try {
            pgConnection?.close()
        } catch (ignore: Throwable) {
        } finally {
            pgConnection = null
        }
    }

    override fun commit() {
        val conn = pgConnection
        assertOpen()
        assertMutable()
        if (conn != null) {
            val tx = transaction
            if (tx != null) {
                try {
                    val writeTxReq = WriteRequest()
                    val writeTx = Write()
                    writeTxReq.add(writeTx)
                    //writeTx.createFeature(null, TRANSACTIONS_COL, useTransaction())
                    PgWriter(this, writeTxReq, InstantWriteExecutor(this)).execute()
                } catch (e: Throwable) {
                    throw NakshaException(EXCEPTION, "Failed to save transaction", cause = e)
                }
            }
            try {
                conn.commit()
            } catch (e: Throwable) {
                throw NakshaException(EXCEPTION, "Failed to commit", cause = e)
            }
            clear()
        }
    }

    override fun rollback() {
        val conn = pgConnection
        assertOpen()
        assertMutable()
        if (conn != null) {
            try {
                conn.rollback()
            } finally {
                clear()
            }
        }
    }

    private var _closed = false

    override fun isClosed(): Boolean = _closed

    override fun close() {
        if (!_closed) {
            rollback()
            _closed = true
            clear()
        }
    }

    @v30_experimental
    override fun acquireSessionLock(lockId: String): ILock {
        assertOpen()
        return PgLock(this, useConnection(), lockId, true)
    }

    @v30_experimental
    override fun acquireTransactionLock(lockId: String): ILock {
        assertOpen()
        return PgLock(this, useConnection(), lockId, false)
    }

    override fun fetchTuples(featureTuples: List<FeatureTuple?>, from: Int, to: Int, fetchFromHistory: Boolean, mode: FetchMode) {
        //TODO("Not yet implemented")
        // TODO: Rohit - Only if you want:
        val cachedTuples = Naksha.cache.load(featureTuples, from, to)
        val missingTuples = featureTuples.subList(from, to).filter { it !in cachedTuples }
        if (missingTuples.isNotEmpty()) {
            fetchFromDatabase(missingTuples, fetchFromHistory, mode)?.forEachIndexed { index, dbResult ->
                if (dbResult != null) {
                    dbResult.tuple?.let { it1 -> Naksha.cache.store(it1) }
                    //Update the original featureTup at the appropriate position. How as list is immutable
                    //featureTuples[from+index] = dbResult
                }
            }
        }
    }

    private fun fetchFromDatabase( missingTuples: List<FeatureTuple?>, fetchFromHistory: Boolean, mode: FetchMode): List<FeatureTuple?>? {

//        WITH source AS (
//            -- Select all tuples needed from all collections.
//            -- We can read all tuples using paging
//                    -- Then we order by tuple_number, and use offset/limit here!
//            -- This must only be done in a single table, but nothing else changes.
//            -- Note that using tuple_number will perform an index scan, its ordered already.
//            (SELECT ${col_number} as col_num, * FROM ${col_name} WHERE tn = ANY($1))
//        UNION ALL
//        ...
//        ), meta_with_rest AS (
//        -- Compose metadata binary, and add the other binary columns.
//        SELECT bytea_agg(
//                int8send(${storage_number})
//                ||int4send(${map_number})
//        ||int4send(col_num)
//                ||tn -- 12 byte, txn is part of tuple_number
//        ||int4send(flags) -- 4 byte, we're aligned to 64-bit again
//        ||coalesce(int8send(txn_next),''::bytea)
//                ||coalesce(int8send(cv0),''::bytea)
//                ||coalesce(int8send(cv1),''::bytea)
//                ||coalesce(int8send(cv2),''::bytea)
//                ||coalesce(int8send(cv3),''::bytea)
//                ||coalesce(prev_tn,''::bytea)
//                ||coalesce(base_tn,''::bytea)
//                ||coalesce(substring(int8send(created_at),3),''::bytea) -- u48
//                ||coalesce(substring(int8send(author_ts),3),''::bytea) -- u48
//                ||substring(int8send(updated_at), 3) -- u48
//                ||int4send(coalesce(change_count, 1))
//                ||int4send(coalesce(hash, 0))
//                ||int4send(coalesce(here_tile,0))
//                ||id::bytea||'\x00'::bytea
//                ||coalesce(app_id,'')::bytea||'\x00'::bytea
//                ||coalesce(author,'')::bytea||'\x00'::bytea
//                ||coalesce(origin,'')::bytea||'\x00'::bytea
//                ||coalesce(target,'')::bytea||'\x00'::bytea
//                ||coalesce(ft,'')::bytea||'\x00'::bytea
//                ||coalesce(cs0,'')::bytea||'\x00'::bytea
//                ||coalesce(cs1,'')::bytea||'\x00'::bytea
//                ||coalesce(cs2,'')::bytea||'\x00'::bytea
//                ||coalesce(cs3,'')::bytea||'\x00'::bytea
//        ) as meta, ref_point, geo, tags, feature, attachment
//        FROM source
//        ), tuple_objects_without_header AS (
//        -- Create Tuple-Binary-Objects without header.
//        SELECT bytea_agg(
//                int4send((octet_length(meta) << 16)|octet_length(coalesce(ref_point,''::bytea)))
//        ||int4send(octet_length(coalesce(geo,''::bytea)))
//                ||int4send(octet_length(coalesce(tags,''::bytea)))
//                ||int4send(octet_length(coalesce(feature,''::bytea)))
//                ||int4send(octet_length(coalesce(attachment,''::bytea)))
//                ||meta
//                ||coalesce(ref_point,''::bytea)
//                ||coalesce(geo,''::bytea)
//                ||coalesce(tags,''::bytea)
//                ||coalesce(feature,''::bytea)
//                ||coalesce(attachment,''::bytea)
//        ) as obj
//        ), result AS (
//        -- Join all Tuple-Binary-Objects, adding the headers, count the amount of tuples.
//        SELECT sum(1)::int as len, bytea_agg(
//        int4send((3 << 28)|1) -- type 3, length 1
//        ||int4send(8 + octet_length(obj)) -- size
//                ||obj
//        ) as all_obj
//        FROM tuple_objects_without_header
//                LIMIT 16777215
//        )
//        -- Create the Tuple-Binary-Array, compress it.
//        SELECT gzip(bytea_agg(
//            int4send((4 << 28)|len) -- type 4
//                ||int4send(8 + octet_length(all_obj)) -- size
//                ||all_obj
//        )) FROM result
        return null
    }

    override fun getMapById(mapId: String): NakshaMap? {
        assertOpen()
        val conn = pgConnection
        if (conn == null && mayReadParallel()) {
            return newReadConnection().use { pgStorage.adminMap.getPgMapById(it, mapId)?.nakshaMap }
        }
        return pgStorage.adminMap.getPgMapById(conn ?: useConnection(), mapId)?.nakshaMap
    }

    override fun getMapByNumber(mapNumber: Int): NakshaMap? {
        assertOpen()
        val conn = pgConnection
        if (conn == null && mayReadParallel()) {
            return newReadConnection().use { pgStorage.adminMap.getPgMapByNumber(it, mapNumber)?.nakshaMap }
        }
        return pgStorage.adminMap.getPgMapByNumber(conn ?: useConnection(), mapNumber)?.nakshaMap
    }

    override fun refreshMaps() {
        // TODO: Implement me, for now we ignore the call.
    }

    private fun _getCollectionById(conn: PgConnection, map: NakshaMap, collectionId: String): NakshaCollection? {
        val pgMap = pgStorage.adminMap.getPgMapById(conn, map.id) ?: return null
        return pgStorage.adminMap.getPgCollectionById(conn, pgMap, collectionId)?.nakshaCollection
    }

    override fun getCollectionById(map: NakshaMap, collectionId: String): NakshaCollection? {
        assertOpen()
        val conn = pgConnection
        if (conn == null && mayReadParallel()) {
            return newReadConnection().use { _getCollectionById(it, map, collectionId) }
        }
        return _getCollectionById(conn ?: useConnection(), map, collectionId)
    }

    private fun _getCollectionByNumber(conn: PgConnection, map: NakshaMap, collectionNumber: Int): NakshaCollection? {
        val pgMap = pgStorage.adminMap.getPgMapById(conn, map.id) ?: return null
        return pgStorage.adminMap.getPgCollectionByNumber(conn, pgMap, collectionNumber)?.nakshaCollection
    }

    override fun getCollectionByNumber(map: NakshaMap, collectionNumber: Int): NakshaCollection? {
        assertOpen()
        val conn = pgConnection
        if (conn == null && mayReadParallel()) {
            return newReadConnection().use { _getCollectionByNumber(it, map, collectionNumber) }
        }
        return _getCollectionByNumber(conn ?: useConnection(), map, collectionNumber)
    }

    override fun refreshCollections(map: NakshaMap) {
        // TODO: Implement me, for now we ignore the call.
    }

    override fun executeParallel(request: Request): Response = execute(request)

    override fun getEncodingFlags(feature: Any?, context: Any?): Flags = pgStorage.adminMap.getEncodingFlags(feature, context)

    override fun getDictionary(id: String): JbDictionary? = pgStorage.adminMap.getDictionary(id)

    override fun getEncodingDictionary(feature: Any?, context: Any?): JbDictionary? = pgStorage.adminMap.getEncodingDictionary(feature, context)
}
