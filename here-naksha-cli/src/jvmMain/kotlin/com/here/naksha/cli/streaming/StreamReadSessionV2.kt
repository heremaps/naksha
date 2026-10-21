package com.here.naksha.cli.streaming

import naksha.base.Platform
import naksha.model.IStorage
import naksha.model.IStreamSession
import naksha.model.SessionOptions
import naksha.model.streaming.Stream
import naksha.model.streaming.StreamChunk
import naksha.model.streaming.StreamRequest
import naksha.psql.PgConfig
import naksha.psql.PsqlConnection
import naksha.psql.PsqlInstance

class StreamReadSessionV2(
    override val storage: IStorage,
    override var socketTimeout: Int,
    override var stmtTimeout: Int,
    override var lockTimeout: Int,
    override val options: SessionOptions
) : IStreamSession {
    override val mayRead: Boolean
        get() = true

    /**
     * The connection to the database, opened in read-only mode.
     * @since 3.0.0
     */
    val connection: PsqlConnection

    init {
        val master = PsqlInstance(Platform.proxy(storage.config.properties, PgConfig::class).master)

        connection = master.openConnection(
            options = SessionOptions(),
            readOnly = true,
            init = null
        )
        connection.execute("SET SESSION enable_sort = off;")
    }

    override fun read(request: StreamRequest): Stream {
        return StreamV2(this, request)
    }

    override val mayWrite: Boolean
        get() = false

    override fun write(chunk: StreamChunk): Boolean {
        throw UnsupportedOperationException("Write is not supported in StreamReadSessionV2")
    }

    override fun isClosed(): Boolean {
        return connection.isClosed()
    }

    override fun close() {
        connection.close()
    }
}