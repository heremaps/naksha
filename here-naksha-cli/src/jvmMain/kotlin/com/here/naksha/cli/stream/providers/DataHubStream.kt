package com.here.naksha.cli.stream.providers

import naksha.base.Id
import naksha.base.JvmMap
import naksha.base.NakshaError
import naksha.base.NakshaError.NakshaErrorCompanion.BAD_GATEWAY
import naksha.base.NakshaError.NakshaErrorCompanion.CLOSED
import naksha.base.NakshaError.NakshaErrorCompanion.EXCEPTION
import naksha.base.NakshaError.NakshaErrorCompanion.PAYLOAD_TOO_LARGE
import naksha.base.NakshaError.NakshaErrorCompanion.TIMEOUT
import naksha.base.NakshaException
import naksha.base.Version
import naksha.model.streaming.Stream
import naksha.model.streaming.StreamChunk
import naksha.model.streaming.StreamException
import naksha.model.streaming.StreamRequest
import naksha.model.streaming.StreamTuple
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Streams the current state of a DataHub space via `/iterate`. Pages are read on the thread calling [next]; the
 * recovery point is the iterate handle of the page holding the oldest unacknowledged chunk.
 */
class DataHubStream(
    session: DataHubStreamSession,
    request: StreamRequest,
    private val client: DataHubClient,
    private val spaceId: String,
    private val version: Long?,
    pageSize: Int,
    override val estimatedTuples: Long,
) : Stream(session, request) {

    /** Where to continue reading: before the first page ([handle] _null_), at an iterate handle, or behind the last page. */
    data class Position(val handle: String?, val end: Boolean) {
        companion object {
            val START = Position(null, false)
            val END = Position(null, true)
        }
    }

    private class Pending(val chunk: StreamChunk, val position: Position) {
        var acknowledged = false
    }

    private val defaultTimeout = session.options.stmtTimeout.let { if (it > 0) it.toLong() else 300_000L }
    private var limit = pageSize
    private var readPosition: Position = startPosition(request)
    private val buffer = ArrayDeque<Pending>()
    private var done = readPosition.end
    private val lock = ReentrantLock()
    private val changed = lock.newCondition()
    private val pending = ArrayDeque<Pending>()
    private var cursor = readPosition
    private var closed = false
    private var error: NakshaError? = null
    private var ackTuples = 0L
    private var unackTuples = 0L

    private fun fetch(): Boolean {
        val pageStart = readPosition
        val page = try {
            client.iterate(spaceId, pageStart.handle, limit, version)
        } catch (e: NakshaException) {
            if (e.error.code == PAYLOAD_TOO_LARGE && limit > 1) {
                limit = maxOf(1, limit / 2)
                return true
            }
            throw e
        }
        val chunkSize = maxOf(1, request.chunkSize)
        for (part in page.features.chunked(chunkSize)) {
            buffer.addLast(Pending(StreamChunk(this, part.map(::toStreamTuple).toTypedArray()), pageStart))
        }
        val next = page.handle
        if (next == null || page.features.isEmpty()) {
            readPosition = Position.END
            return false
        }
        if (next == pageStart.handle) throw NakshaException(BAD_GATEWAY, "DataHub returned the same iterate handle twice for space $spaceId")
        readPosition = Position(next, false)
        return true
    }

    private fun toStreamTuple(feature: JvmMap): StreamTuple {
        val id = feature["id"] as? String ?: throw NakshaException(BAD_GATEWAY, "DataHub returned a feature without id in space $spaceId")
        val xyz = (feature["properties"] as? Map<*, *>)?.get(XYZ_NS) as? Map<*, *>
        val v = (xyz?.get("version") as? Number)?.toLong() ?: -1L
        return StreamTuple(Id(id), feature, null, -1L, v)
    }

    private fun read() {
        try {
            while (buffer.isEmpty() && !done) if (!fetch()) done = true
        } catch (e: NakshaException) {
            lock.withLock { fail(e.error) }
        } catch (e: Throwable) {
            lock.withLock { fail(NakshaError(EXCEPTION, e.message ?: e.toString(), e)) }
        }
        lock.withLock { cursor = buffer.firstOrNull()?.position ?: readPosition }
    }

    private fun fail(reason: NakshaError): Nothing {
        if (error == null) error = reason
        val recovery = recoveryRequestLocked()
        shutdownLocked()
        throw StreamException(recovery, this, reason)
    }

    private fun shutdownLocked() {
        if (closed) return
        closed = true
        changed.signalAll()
    }

    override fun hasNext(): Boolean {
        lock.withLock {
            error?.let { fail(it) }
            if (closed) return false
        }
        if (buffer.isEmpty() && !done) read()
        return buffer.isNotEmpty()
    }

    override fun next(): StreamChunk = next(defaultTimeout) ?: lock.withLock {
        if (closed) throw NakshaException(CLOSED, "Stream closed")
        fail(NakshaError(TIMEOUT, "Timeout waiting for the next chunk"))
    }

    override fun next(timeoutMillis: Long): StreamChunk? {
        if (!hasNext()) throw NoSuchElementException()
        lock.withLock {
            var nanos = TimeUnit.MILLISECONDS.toNanos(maxOf(0L, timeoutMillis))
            while (request.sequential && pending.isNotEmpty() && error == null && !closed) {
                if (nanos <= 0L) return null
                nanos = changed.awaitNanos(nanos)
            }
            error?.let { fail(it) }
            if (closed) return null
            val p = buffer.removeFirst()
            pending.addLast(p)
            unackTuples += p.chunk.tuples.size
            cursor = buffer.firstOrNull()?.position ?: readPosition
            return p.chunk
        }
    }

    override fun acknowledge(chunk: StreamChunk) {
        lock.withLock {
            val p = pending.firstOrNull { it.chunk === chunk } ?: return
            if (p.acknowledged) return
            p.acknowledged = true
            ackTuples += chunk.tuples.size
            unackTuples -= chunk.tuples.size
            while (pending.firstOrNull()?.acknowledged == true) pending.removeFirst()
            changed.signalAll()
        }
    }

    override fun fail(chunk: StreamChunk, reason: NakshaError) = lock.withLock {
        if (error == null) error = reason
        changed.signalAll()
    }

    private fun recoveryRequestLocked(): StreamRequest = recoveryRequestAt(pending.firstOrNull()?.position ?: cursor)

    private fun recoveryRequestAt(position: Position): StreamRequest {
        val r = StreamRequest()
        r.putAll(request)
        r.remove(HANDLE)
        r.remove(END)
        if (position.handle != null) r[HANDLE] = position.handle
        if (position.end) r[END] = true
        return r
    }

    private fun awaitAcknowledgedLocked(timeoutMillis: Long): Boolean {
        var nanos = TimeUnit.MILLISECONDS.toNanos(maxOf(0L, timeoutMillis))
        while (pending.isNotEmpty() && error == null) {
            if (nanos <= 0L) return false
            nanos = changed.awaitNanos(nanos)
        }
        return error == null
    }

    override val minVersion: Version get() = Version(request.minVersion)
    override val unacknowledgedChunks: Array<StreamChunk>
        get() = lock.withLock { pending.filter { !it.acknowledged }.map { it.chunk }.toTypedArray() }
    override val acknowledgedChunks: Array<StreamChunk>
        get() = lock.withLock { pending.filter { it.acknowledged }.map { it.chunk }.toTypedArray() }
    override val estimatedChunks: Long
        get() = if (estimatedTuples < 0) -1L else (estimatedTuples + maxOf(1, request.chunkSize) - 1) / maxOf(1, request.chunkSize)
    override val acknowledgedTuples: Long get() = lock.withLock { ackTuples }
    override val unacknowledgedTuples: Long get() = lock.withLock { unackTuples }
    override val isRecoverable: Boolean get() = true

    override fun isAcknowledged(timeoutMillis: Long): Boolean = lock.withLock { awaitAcknowledgedLocked(timeoutMillis) }

    override fun isClosed(): Boolean = lock.withLock { closed }

    override fun close() {
        lock.withLock {
            if (closed) return
            if (!awaitAcknowledgedLocked(defaultTimeout)) fail(error ?: NakshaError(TIMEOUT, "Timeout waiting for acknowledgements"))
            val more = buffer.isNotEmpty() || !done
            val recovery = recoveryRequestLocked()
            shutdownLocked()
            if (more) throw StreamException(recovery, this, NakshaError(CLOSED, "Stream closed with unread chunks"))
        }
    }

    override fun closeForRecovery(): StreamRequest = lock.withLock {
        awaitAcknowledgedLocked(defaultTimeout)
        val recovery = recoveryRequestLocked()
        shutdownLocked()
        recovery
    }

    override fun getRecoveryRequest(): StreamRequest = lock.withLock { recoveryRequestLocked() }

    companion object {
        const val HANDLE = "datahubHandle"
        const val END = "datahubEnd"
        const val XYZ_NS = "@ns:com:here:xyz"

        private fun startPosition(request: StreamRequest): Position = when {
            request.getRaw(END) == true -> Position.END
            else -> (request.getRaw(HANDLE) as? String)?.let { Position(it, false) } ?: Position.START
        }
    }
}
