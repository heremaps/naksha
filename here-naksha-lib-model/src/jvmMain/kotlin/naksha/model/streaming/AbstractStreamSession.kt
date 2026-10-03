package naksha.model.streaming

import naksha.base.unsupportedOp
import naksha.model.IStorage
import naksha.model.IStreamSession
import naksha.model.SessionOptions

/**
 * Session boilerplate for stream sessions; subclasses override [read] and/or [write].
 *
 * Providers that are not backed by a storage, like the CLI test providers, pass `null` as storage.
 */
abstract class AbstractStreamSession(
    private val backingStorage: IStorage?,
    override val options: SessionOptions,
) : IStreamSession {
    override val storage: IStorage
        get() = backingStorage ?: throw unsupportedOp("This stream session is not backed by a storage")

    override var socketTimeout: Int = options.socketTimeout
    override var stmtTimeout: Int = options.stmtTimeout
    override var lockTimeout: Int = options.lockTimeout

    @Volatile
    private var closed = false

    override val mayRead: Boolean get() = false
    override val mayWrite: Boolean get() = false

    override fun read(request: StreamRequest): Stream = throw unsupportedOp("read")

    override fun write(chunk: StreamChunk): Boolean = throw unsupportedOp("write")

    override fun isClosed(): Boolean = closed

    override fun close() {
        closed = true
    }
}
