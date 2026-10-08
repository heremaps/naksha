package com.here.naksha.cli.streaming

import naksha.base.AnyObject
import naksha.base.Platform
import naksha.base.proxy
import naksha.model.IStorage
import naksha.model.IStreamSession
import naksha.model.SessionOptions
import naksha.model.streaming.Stream
import naksha.model.streaming.StreamChunk
import naksha.model.streaming.StreamRequest
import naksha.psql.PgConfig
import naksha.psql.PgInstanceConfig

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
        val master = Platform.proxy(config.properties, PgConfig::class).master
        val db = master.db
        val password = master.password

        val stream = Stream(this, request)
        TODO("Not yet implemented")
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