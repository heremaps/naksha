package naksha.model.streaming

import naksha.model.IStreamSession
import kotlin.js.JsExport
import kotlin.jvm.JvmName

/**
 * A stream that can be read to replicate the content of a collection. The data being streaming is dependent on the [StreamRequest].
 *
 * Generally all data is grouped by the reader. Reading is done in chunks being either [StreamChunk] or [StreamTransaction]. The method [next] will block until all chunks that need to be processed have been [acknowledged][acknowledge].
 *
 * The recommended way to process a stream is that one thread performs the reading of chunks and then delegates the actual write to worker threads, which should confirm the writing by closing the chunk _([StreamChunk] or [StreamTransaction])_.
 *
 * It is the responsibility of the stream to decide in which order it is safe to consume chunks.
 *
 * For transactions, it is safe to process them in parallel if they do not impact each other. That means, as long as transactions do not contain the same features as any other yet unacknowledged transaction, they can be written in parallel.
 *
 * For example a Naksha storage will read the transaction log, then use multiple connections in parallel to read the transactions. It will hand them out in order, but [next] will only block, when the next transaction contains a feature that is as well part of a previously returned [StreamTransaction], which was not yet acknowledged.
 *
 * It is the responsibility of the stream to decide in which order it is safe to consume the data. Specifically with disabled transactions, the stream implementation is free to reorder data. So it can read features, then re-group them so that as many chunks as possible can be written in parallel, only ensuring that no feature is processed in parallel. This can make the copy many times faster for certain storages. However, it means that features will move between transactions and therefore destroys the transactional order and the consistency between features.
 *
 * @since 3.0
 */
@JsExport
abstract class Stream(
    /**
     * The session to which this stream is bound.
     * @since 3.0
     */
    @get:JvmName("session")
    val session: IStreamSession
) : Iterator<StreamChunk> {

    /**
     * Returns the next [StreamFeature] or [StreamTransaction] in the iteration.
     *
     * @since 3.0
     * @throws naksha.base.NakshaException with error [ILLEGAL_STATE][naksha.base.NakshaError.ILLEGAL_STATE] if the iteration has no next element.
     */
    abstract override operator fun next(): StreamChunk

    /**
     * Returns `true` if the iteration has more elements.
     * @since 3.0
     */
    abstract override operator fun hasNext(): Boolean

    /**
     * Calling by [StreamChunk] when closed to acknowledge that the chunk has been processed successfully, will move the [recoveryRequest] forward.
     * @param chunk the [StreamChunk] chunk to flag as processed.
     * @since 3.0
     */
    abstract fun acknowledge(chunk: StreamChunk)

    /**
     * A recovery request that can be used to restart this stream for the current position.
     *
     * ### Warning
     * When writing in parallel, the recovery need care by the consumer. It needs to be aware that when recovering, some writes are duplicates. The stream implementation will only guarantee read ordering, not write order.
     *
     * Therefore, it can happen that the recovery point is behind the current write _HEAD_. For example, assume a stream starts at version `0`, and chunks `1`, `2`, `3`, and `4` are read and written in parallel in four worker threads, because they do not intersect. Now worker #1 acknowledges chunk `1`, this will update the _recoveryRequest_ and move it to `1`. Assume worker `3` and `4` next confirm their writes. This will **not** update _recoveryRequest_, because the confirmation for chuk `2` is outstanding. Now assume, the read aborts or the write fails.
     *
     * When the _recoveryRequest_ is then stored to resume the operation, it will start at chunk `1`. This will then read chunk `2`, `3`, and `4` again, of which `3` and `4` have been written already! This is the responsibility of the consumer to either skip writes `3` or `4`, or to ignore the write errors due to the fact that these features do exist already.
     * @since 3.0
     */
    abstract val recoveryRequest: StreamRequest
}