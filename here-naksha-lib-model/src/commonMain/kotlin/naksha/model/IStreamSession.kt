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
     * Asks the storage to persist
     * @since 3.0
     */
    fun store(chunk: StreamChunk)
}