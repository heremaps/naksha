package com.here.naksha.cli.stream.providers

import naksha.base.NakshaError.NakshaErrorCompanion.CLOSED
import naksha.base.NakshaError.NakshaErrorCompanion.ILLEGAL_ARGUMENT
import naksha.base.NakshaException
import naksha.base.Version
import naksha.base.unsupportedOp
import naksha.model.IStorage
import naksha.model.IStreamSession
import naksha.model.SessionOptions
import naksha.model.streaming.Stream
import naksha.model.streaming.StreamChunk
import naksha.model.streaming.StreamRequest
import org.slf4j.LoggerFactory

/** Reads DataHub spaces; the request's collection is the space id, its database and catalog are ignored. */
class DataHubStreamSession(
    override val storage: IStorage,
    private val client: DataHubClient,
    private val config: DataHubConfig,
    override val options: SessionOptions,
) : IStreamSession {
    override var socketTimeout: Int = options.socketTimeout
    override var stmtTimeout: Int = options.stmtTimeout
    override var lockTimeout: Int = options.lockTimeout

    @Volatile
    private var closed = false

    override val mayRead: Boolean get() = true
    override val mayWrite: Boolean get() = false

    override fun read(request: StreamRequest): Stream {
        if (closed) throw NakshaException(CLOSED, "Session closed")
        val spaceId = request.collectionId.toString()
        val space = client.space(spaceId)
        val version = if (request.version == Version.HEAD.number) null else request.version
        if (version != null && !space.globalVersioning) {
            throw NakshaException(ILLEGAL_ARGUMENT, "DataHub space $spaceId has no global versioning, only the latest state can be read")
        }
        if (request.queryHistory) {
            log.warn("History of DataHub space {} is not read yet, streaming its latest state only", spaceId)
        }
        return DataHubStream(this, request, client, space.id, version, config.pageSize, client.count(spaceId))
    }

    override fun write(chunk: StreamChunk): Unit = throw unsupportedOp("write")

    override fun isClosed(): Boolean = closed

    override fun close() {
        closed = true
    }

    private companion object {
        val log = LoggerFactory.getLogger(DataHubStreamSession::class.java)
    }
}
