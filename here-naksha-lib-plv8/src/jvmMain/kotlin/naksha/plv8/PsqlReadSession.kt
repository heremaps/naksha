package com.here.naksha.lib.plv8.naksha.plv8

import naksha.model.IReadSession
import naksha.model.NakshaContext
import naksha.model.request.ReadRequest
import naksha.model.request.Request
import naksha.model.request.ResultRow
import naksha.model.response.Response
import naksha.model.response.SuccessResponse
import naksha.plv8.DbRowMapper
import naksha.plv8.NakshaSession
import naksha.plv8.read.ReadQueryBuilder

/**
 * A PostgresQL read-only session.
 * @property storage the storage to which this session is bound.
 * @property context the context for which to initialize new connections.
 * @property options the options when opening new connections.
 * @property useMaster if the connections must be opened to the master.
 */
open class PsqlReadSession internal constructor(
    val storage: PsqlStorage,
    override var context: NakshaContext,
    open var options: PsqlConnectOptions,
    private val useMaster: Boolean = false
): PsqlSession(), IReadSession {

    override var stmtTimeout: Int
        get() = options.stmtTimeout
        set(value) {
            options = options.copy(stmtTimeout = value)
        }

    override var lockTimeout: Int
        get() = options.lockTimeout
        set(value) {
            options = options.copy(lockTimeout = value)
        }

    private var _connection: PsqlConnection? = null

    /**
     * Returns a new connection.
     */
    fun connection(): PsqlConnection {
        check(!_closed)
        var conn = _connection
        if (conn == null) {
            conn = storage.pgCluster.getConnection(options, useMaster)
            // TODO: Initialize the connection with timeouts and other stuff!
            _connection = conn
        }
        return conn
    }

    protected val nakshaSession: NakshaSession = NakshaSession(
        sql = connection(),
        schema = storage.schema, // FIXME
        storage = storage,
        appName = "FIXME", // FIXME
        streamId = "FIXME", // FIXME
        appId = context.appId,
        author = context.author
    )

    override fun execute(request: Request): Response {
        return when (request) {
            is ReadRequest -> {
                val (sql, params) = ReadQueryBuilder(nakshaSession.sql).build(request)
                val pgResult = nakshaSession.sql.rows(nakshaSession.sql.execute(sql, params.toTypedArray()))
                val rows = DbRowMapper.toReadRows(pgResult, storage)
                return SuccessResponse(rows = rows)
            }

            else -> throw UnsupportedOperationException("Read session can execute only read requests")
        }
    }

    override fun executeParallel(request: Request): Response {
        TODO("Not yet implemented")
    }

    override fun getFeatureById(id: String): ResultRow? {
        TODO("Not yet implemented")
    }

    override fun getFeaturesByIds(ids: List<String>): Map<String, ResultRow> {
        TODO("Not yet implemented")
    }

    protected var _closed = false
    override fun close() {
        if (!_closed) {
            _closed = true
            _connection?.close()
            _connection = null
        }
    }
}