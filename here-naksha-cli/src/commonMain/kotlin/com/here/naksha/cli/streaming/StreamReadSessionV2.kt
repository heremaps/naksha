package com.here.naksha.cli.streaming

import naksha.base.Platform
import naksha.model.IStorage
import naksha.model.IStreamSession
import naksha.model.SessionOptions
import naksha.model.streaming.Stream
import naksha.model.streaming.StreamChunk
import naksha.model.streaming.StreamRequest
import naksha.psql.PgConfig
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

    override fun read(request: StreamRequest): Stream {
        val config = storage.config
        val master = PsqlInstance(Platform.proxy(config.properties, PgConfig::class).master)
        val connection = master.openConnection(
            options = SessionOptions(),
            readOnly = true,
            init = null
        )
        val sql = "SELECT jsondata, geo FROM ${request.catalogId}.${request.collectionId} WHERE id = ${request.id}"
        val cursor = connection.execute(sql)
        val stream = StreamV2(this, request)
        return stream
    }

    override val mayWrite: Boolean
        get() = false

    override fun write(chunk: StreamChunk): Boolean {
        throw UnsupportedOperationException("Write is not supported in StreamReadSessionV2")
    }

    override fun isClosed(): Boolean {
        TODO("Not yet implemented")
    }

    override fun close() {
        TODO("Not yet implemented")
    }
}