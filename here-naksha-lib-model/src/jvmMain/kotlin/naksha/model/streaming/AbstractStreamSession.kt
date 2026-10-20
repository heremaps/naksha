package naksha.model.streaming

import naksha.base.unsupportedOp
import naksha.model.IStorage
import naksha.model.IStreamSession
import naksha.model.SessionOptions

abstract class AbstractStreamSession(
    override val storage: IStorage,
    override val options: SessionOptions,
) : IStreamSession {
    override var socketTimeout: Int = options.socketTimeout
    override var stmtTimeout: Int = options.stmtTimeout
    override var lockTimeout: Int = options.lockTimeout

    @Volatile
    private var closed = false

    override val mayRead: Boolean get() = false
    override val mayWrite: Boolean get() = false

    override fun read(request: StreamRequest): Stream = throw unsupportedOp("read")

    override fun write(chunk: StreamChunk): Unit = throw unsupportedOp("write")

    override fun isClosed(): Boolean = closed

    override fun close() {
        closed = true
    }
}
