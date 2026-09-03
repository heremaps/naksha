package naksha.model.streaming

import naksha.base.AtomicBool
import naksha.base.illegalState
import kotlin.js.JsExport
import kotlin.jvm.JvmName

/**
 * A chunk of features with the guarantee that it does not contain multiple states of same feature _(with the same [identifier][naksha.base.Id])_. Therefore, the chunk does not contain the same feature multiple times.
 *
 * The order of the provided features is not significant. They should be consumed and stored together in an atomic way. When features have been processed the steam must be notified by closing the chunk.
 * @since 3.0
 */
@JsExport
abstract class StreamChunk(
    /**
     * The stream to which this chunk belongs.
     * @since 3.0
     */
    @get:JvmName("stream")
    val stream: Stream,

    /**
     * The features being part of this chunk.
     * @since 3.0
     */
    @get:JvmName("features")
    val features: Array<StreamFeature>
): AutoCloseable {
    private var closed = AtomicBool()

    /**
     * If this chunk is closed.
     * @since 3.0
     */
    @get:JvmName("isClosed")
    val isClosed: Boolean
        get() = closed.get()

    /**
     * Closes this chunk and notifies the [Stream] that the features have been processed. This allows the stream to continue reading features that have been part of this stream.
     * @since 3.0
     * @throws naksha.base.NakshaException with error [ILLEGAL_STATE][naksha.base.NakshaError.ILLEGAL_STATE] if the chunk was already closed.
     */
    override fun close() {
        if (closed.compareAndSet(false, true)) {
            stream.acknowledge(this)
        } else {
            throw illegalState("Chunk already closed")
        }
    }
}