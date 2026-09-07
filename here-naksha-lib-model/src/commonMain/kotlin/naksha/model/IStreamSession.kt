package naksha.model

import naksha.model.streaming.Stream
import naksha.model.streaming.StreamChunk
import naksha.model.streaming.StreamRequest
import kotlin.js.JsExport

/**
 * A session that can be used to stream data from a storage.
 * @since 3.0
 */
@JsExport
interface IStreamSession: ISession {
    /**
     * Opens a new stream to read from a collection in streaming mode.
     * @param request the request for the stream.
     * @return the stream.
     * @since 3.0
     */
    fun read(request: StreamRequest): Stream

    /**
     * Asks the storage to synchronously persist the given chunk.
     *
     * The method will block until all data is eventually writen into the storage with the consistency guarantees for the underlying implementation.
     * @param chunk the feature states to persist, either a [StreamTransaction][naksha.model.streaming.StreamTransaction] or a pure [StreamChunk].
     * @since 3.0
     * @throws naksha.base.NakshaException if any error occurred.
     */
    fun store(chunk: StreamChunk)
}