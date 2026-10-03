package naksha.model.streaming

import naksha.base.NakshaError
import naksha.base.NakshaError.NakshaErrorCompanion.CLOSED
import naksha.base.NakshaError.NakshaErrorCompanion.EXCEPTION
import naksha.base.NakshaError.NakshaErrorCompanion.TIMEOUT
import naksha.base.NakshaException
import naksha.base.Version
import naksha.base.unsupportedOp
import naksha.model.IStreamSession
import naksha.model.SessionOptions
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * DRAFT: shared JVM base for stream readers.
 *
 * A producer thread calls [produce], which pushes chunks via [Sink.emit]. The base class takes care of:
 * - bounded prefetch (`1` in sequential mode),
 * - never handing out a chunk while another unacknowledged chunk contains the same feature,
 * - tracking the recovery position (oldest unacknowledged chunk),
 * - failure and timeout handling as documented in [Stream].
 *
 * Positions are opaque, storage-specific, monotonically increasing longs (e.g. a version, a table sequence, a feature-number). The position of a chunk must be a restart point: reading again from it must re-deliver the chunk (at-least-once).
 */
abstract class QueueStream(
    session: IStreamSession,
    request: StreamRequest,
    private val options: SessionOptions,
    prefetch: Int = 16,
) : Stream(session, request) {

    /** Callback given to [produce]. */
    fun interface Sink {
        /**
         * Hands a chunk to the stream, blocks while the prefetch buffer is full.
         * @param position the restart position of this chunk.
         * @param nextPosition the restart position after this chunk.
         * @return _false_ if the stream was closed and the producer should stop.
         */
        fun emit(chunk: StreamChunk, position: Long, nextPosition: Long): Boolean
    }

    /** Reads the source and emits chunks in order; runs on its own thread. Return normally when done. */
    protected abstract fun produce(sink: Sink)

    /** Builds a request that restarts reading at the given position (inclusive). */
    protected abstract fun recoveryRequestAt(position: Long): StreamRequest

    /** Maps a position to a version for [minVersion]; storages without Naksha versions may return [request]'s minVersion. */
    protected open fun versionAt(position: Long): Version = Version(request.minVersion)

    /** Start position of this stream. */
    protected abstract val startPosition: Long

    private class Entry(val chunk: StreamChunk, val position: Long, val ids: Set<Long>) {
        var acknowledged = false
    }

    private val lock = ReentrantLock()
    private val changed = lock.newCondition()
    private val capacity = if (request.sequential) 1 else maxOf(1, prefetch)
    private val ready = ArrayDeque<Entry>()
    private val outstanding = ArrayList<Entry>()
    private val inFlightIds = HashMap<Long, Int>()
    private var producerDone = false
    private var closed = false
    private var error: NakshaError? = null
    private var producerPosition = Long.MIN_VALUE
    private var ackTuples = 0L
    private var unackTuples = 0L

    private val producer: Thread = Thread {
        try {
            produce { chunk, position, nextPosition -> push(chunk, position, nextPosition) }
        } catch (e: NakshaException) {
            lock.withLock { if (error == null) error = e.error }
        } catch (e: Throwable) {
            lock.withLock { if (error == null) error = NakshaError(EXCEPTION, e.message ?: e.toString(), e) }
        } finally {
            lock.withLock { producerDone = true; changed.signalAll() }
        }
    }.apply { name = "stream-producer"; isDaemon = true }

    /** Must be called by the implementation after construction. */
    protected fun start() {
        producerPosition = startPosition
        producer.start()
    }

    private fun push(chunk: StreamChunk, position: Long, nextPosition: Long): Boolean = lock.withLock {
        while (!closed && error == null && ready.size >= capacity) changed.await()
        if (closed || error != null) return false
        ready.addLast(Entry(chunk, position, chunk.tuples.mapTo(HashSet()) { it.id.number }))
        producerPosition = nextPosition
        changed.signalAll()
        true
    }

    private fun timeoutMillis(): Long = if (options.stmtTimeout > 0) options.stmtTimeout.toLong() else 300_000L

    private fun canHandOut(e: Entry): Boolean {
        if (request.sequential) return outstanding.all { it.acknowledged }
        return e.ids.none { inFlightIds.containsKey(it) }
    }

    private fun fail(reason: NakshaError): Nothing {
        val recovery = if (isRecoverable) recoveryRequestLocked() else null
        closed = true
        changed.signalAll()
        if (recovery != null) throw StreamException(recovery, this, reason)
        throw NakshaException(reason)
    }

    override fun hasNext(): Boolean = lock.withLock {
        while (ready.isEmpty() && !producerDone && error == null && !closed) changed.await()
        error?.let { fail(it) }
        ready.isNotEmpty()
    }

    override fun next(): StreamChunk =
        next(timeoutMillis()) ?: lock.withLock {
            if (closed) throw NakshaException(CLOSED, "Stream closed")
            fail(NakshaError(TIMEOUT, "Timeout waiting for the next chunk"))
        }

    override fun next(timeoutMillis: Long): StreamChunk? = lock.withLock {
        var nanos = TimeUnit.MILLISECONDS.toNanos(maxOf(0L, timeoutMillis))
        while (true) {
            error?.let { fail(it) }
            if (closed) return null
            val head = ready.firstOrNull()
            if (head == null && producerDone) throw NoSuchElementException()
            if (head != null && canHandOut(head)) {
                ready.removeFirst()
                outstanding.add(head)
                for (id in head.ids) inFlightIds.merge(id, 1, Int::plus)
                unackTuples += head.chunk.tuples.size
                changed.signalAll()
                return head.chunk
            }
            if (nanos <= 0L) return null
            nanos = changed.awaitNanos(nanos)
        }
        @Suppress("UNREACHABLE_CODE") null
    }

    override fun acknowledge(chunk: StreamChunk) = lock.withLock {
        val e = outstanding.firstOrNull { it.chunk === chunk } ?: return
        if (e.acknowledged) return
        e.acknowledged = true
        for (id in e.ids) inFlightIds.computeIfPresent(id) { _, n -> if (n <= 1) null else n - 1 }
        ackTuples += chunk.tuples.size
        unackTuples -= chunk.tuples.size
        while (outstanding.isNotEmpty() && outstanding.first().acknowledged) outstanding.removeFirst()
        changed.signalAll()
    }

    override fun fail(chunk: StreamChunk, reason: NakshaError) = lock.withLock {
        if (error == null) error = reason
        changed.signalAll()
    }

    private fun oldestPositionLocked(): Long =
        outstanding.firstOrNull { !it.acknowledged }?.position ?: ready.firstOrNull()?.position ?: producerPosition

    private fun recoveryRequestLocked(): StreamRequest = recoveryRequestAt(oldestPositionLocked())

    private fun awaitAcknowledgedLocked(timeoutMillis: Long): Boolean {
        var nanos = TimeUnit.MILLISECONDS.toNanos(maxOf(0L, timeoutMillis))
        while (outstanding.any { !it.acknowledged } && error == null) {
            if (nanos <= 0L) return false
            nanos = changed.awaitNanos(nanos)
        }
        return error == null
    }

    override val minVersion: Version get() = lock.withLock { versionAt(oldestPositionLocked()) }
    override val unacknowledgedChunks: Array<StreamChunk>
        get() = lock.withLock { outstanding.filter { !it.acknowledged }.map { it.chunk }.toTypedArray() }
    override val acknowledgedChunks: Array<StreamChunk>
        get() = lock.withLock { outstanding.filter { it.acknowledged }.map { it.chunk }.toTypedArray() }
    override val estimatedChunks: Long get() = -1L
    override val estimatedTuples: Long get() = -1L
    override val acknowledgedTuples: Long get() = lock.withLock { ackTuples }
    override val unacknowledgedTuples: Long get() = lock.withLock { unackTuples }
    override val isRecoverable: Boolean get() = true

    override fun isAcknowledged(timeoutMillis: Long): Boolean = lock.withLock { awaitAcknowledgedLocked(timeoutMillis) }

    override fun isClosed(): Boolean = lock.withLock { closed }

    override fun close() = lock.withLock {
        if (closed) return
        if (!awaitAcknowledgedLocked(timeoutMillis())) fail(error ?: NakshaError(TIMEOUT, "Timeout waiting for acknowledgements"))
        val more = ready.isNotEmpty() || !producerDone
        val recovery = recoveryRequestLocked()
        closed = true
        changed.signalAll()
        producer.interrupt()
        if (more) throw StreamException(recovery, this, NakshaError(CLOSED, "Stream closed with unread chunks"))
    }

    override fun closeForRecovery(): StreamRequest = lock.withLock {
        if (!isRecoverable) throw unsupportedOp("Stream is not recoverable")
        awaitAcknowledgedLocked(timeoutMillis())
        val recovery = recoveryRequestLocked()
        closed = true
        changed.signalAll()
        producer.interrupt()
        recovery
    }

    override fun getRecoveryRequest(timeoutMillis: Long): StreamRequest = lock.withLock {
        if (!awaitAcknowledgedLocked(timeoutMillis)) throw NakshaException(TIMEOUT, "Timeout waiting for acknowledgements")
        recoveryRequestLocked()
    }
}
