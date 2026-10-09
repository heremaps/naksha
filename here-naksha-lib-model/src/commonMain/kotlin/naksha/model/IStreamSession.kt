package naksha.model

import naksha.model.streaming.Stream
import naksha.model.streaming.StreamChunk
import naksha.model.streaming.StreamRequest
import kotlin.js.JsExport
import kotlin.jvm.JvmName

/**
 * A session that can be used to stream data from a storage.
 * @since 3.0
 */
@JsExport
interface IStreamSession: ISession {
    /**
     * If [read] is supported.
     * @since 3.0
     */
    val mayRead: Boolean

    /**
     * Opens a new stream to read from a collection in streaming mode.
     * @param request the request for the stream.
     * @return the stream.
     * @since 3.0
     * @throws naksha.base.NakshaException with error [UNSUPPORTED_OPERATION][naksha.base.NakshaError.UNSUPPORTED_OPERATION] if the request is not supported. For example, if a sequential read is requested and not supported, or vice versa.
     */
    fun read(request: StreamRequest): Stream

    /**
     * If [write] is supported.
     * @since 3.0
     */
    val mayWrite: Boolean

    /**
     * Asks the storage to atomically store the features of the given chunk.
     *
     * The method only blocks until the storage accepted the chunk, normally by adding it to a queue. When the queue is full, it blocks until there is room again. The storage decides how to write the accepted chunks, for example with a thread pool, virtual threads, asynchronous IO or a single writer.
     *
     * **The implementation must expect to handle the situation that the provided features do exist already**. It should not fail in such a case, except the existing features are in a modified state.
     *
     * The storage must call either [StreamChunk.acknowledge] or [StreamChunk.fail], normally asynchronously after this method returned.
     * @param chunk the chunk to persist, either a [StreamTransaction][naksha.model.streaming.StreamTransaction] or [StreamChunk].
     * @since 3.0
     * @throws naksha.base.NakshaException if the chunk was not accepted, then the caller must [fail][StreamChunk.fail] it.
     * @see StreamChunk.acknowledge
     * @see StreamChunk.failed
     */
    fun write(chunk: StreamChunk)
}