package naksha.model.streaming

import naksha.base.NakshaError
import naksha.model.IStreamSession
import kotlin.js.JsExport
import kotlin.jvm.JvmName
import naksha.base.NakshaException
import kotlin.js.JsName
import kotlin.time.Duration

/**
 * A _(optionally recoverable)_ stream that can be read to replicate the content of a collection. The data being streamed is dependent on a [StreamRequest].
 *
 * **A stream should only be read by a single thread!**
 *
 * Reading is generally done in chunks being either [StreamChunk] or [StreamTransaction]. Streams are based upon the principle of [at least ones delivery](https://www.systemoverflow.com/learn/message-queues/queue-fundamentals/delivery-semantics-at-most-once-at-least-once-and-exactly-once). Therefore, targets of [StreamChunk] must expect, especially in recovery case, to get a chunk multiple times, but they can be sure to never miss any [StreamChunk].
 *
 * If the stream was requested in [sequential mode][StreamRequest.sequential], synchronous reading is expected by the stream. This means, the reader will optimize to read sequentially, preventing to prefetch too much chunks and does not reorder or alike. This mode is sometimes important when the target is for example a queue, like in subscription.
 *
 * Before delivering a [StreamChunk] to targets, the reading thread must declare how many targets are going to receive the [StreamChunk] by calling [StreamChunk.setTargets]. **This must be done before handing over the chunk to the first target!**
 *
 * The moment the [acknowledgeCount][StreamChunk.acknowledgeCount] goes to `0`, the reader will be notified via [acknowledge] that the [chunk][StreamChunk] is done. This allows the reader to understand which features have outstanding targets, and which ones are done. That is important for the reader to know which pending chunks can be delivered via [next].
 *
 * The method [next] will block until a new chunk, that need to be processed, becomes available. Beware that even while [hasNext] may return _true_, this does not mean that the next chunk is instantly ready to be processed. The reader must in some cases guarantee the transaction order and avoid that the same feature is processed concurrently. Therefore, the reader may block [next] until it receives a specific acknowledgment of some outstanding chunk, before returning the next chunk, even while it may have it already. This mechanism allow the storage to return chunks for parallel processing, until a potential conflict arises. Still this allows to stream the whole source in parallel with maximum throughput and zero waiting in some cases _(for example all native Naksha storages will do)_. This is only true until the sequential mode was requested. It will force the stream to not hand out another chunk until the previous one was acknowledged.
 *
 * The recommended way to process a stream is that one thread performs the reading of chunks and then delegates the actual processing asynchronously to dedicated threads, which should invoke [StreamChunk.acknowledge] when they are done with the chunk.
 *
 * The following is the recommended read loop:
 * ```kotlin
 * // Pseudo read method of e.g. a CLI tool.
 * fun readAll(stream: Stream) {
 *   try { stream.use {
 *     while (stream.hasNext()) {
 *       // Read ones, multiple targets.
 *       // targets are N `IStreamSession`'s
 *       // into which to write the chunks
 *       val chunk = stream.next()
 *       chunk.setTargets(targets.size)
 *       for (target in this.targets) {
 *         doWrite(chunk, target)
 *       }
 *       // Potentially wait for targets!
 *       // For virtual threads normally
 *       // not necessary. Can as well be
 *       // use to consolidate writes.
 *     }
 *   }}} catch (StreamException e) {
 *     // Failure with e.recoveryRequest!
 *     // Stream is closed!
 *     // Recover via IStreamSession.read!
 *   } catch (NakshaException e) {
 *     // Failure without recovery!
 *     // Stream is closed!
 *   }
 * }
 *
 * // Could as well write into queues or alike.
 * fun doWrite(
 *   chunck: StreamChunck,
 *   session: IStreamSession
 * ) {
 *   Thread.startVirtualThread {
 *     try {
 *       // Writer must handle
 *       // duplicate chunks!
 *       session.write(chunk)
 *     } catch (Exception e) {
 *       log.error("Write error", e)
 *       chunk.failed(retry=false)
 *     }
 *   }
 * }
 * ```
 *
 * This allows to read ones and write in parallel into multiple targets _(e.g. into S3 and multiple replication databases)_.
 *
 * #### Note
 * The example code is based upon JVM version 24+, because between 21 _(including)_ and 24 _(excluding)_ the virtual threads have a severe bug with synchronized methods and synchronization blocks, see [JEPS-491](https://openjdk.org/jeps/491) and [JDK-8337395](https://bugs.openjdk.org/browse/JDK-8337395)!
 *
 * ### Implementation details
 * It is the responsibility of the stream to decide in which order it is safe to consume chunks.
 *
 * If a sequential stream was explicitly requested, the stream **must** guarantee sequential read. That means [next] must wait for [acknowledge] before another chunk is returned.
 *
 * For transactions, it is always safe to process them in parallel, if they do not impact each other. That means, as long as transactions do not contain the same features as any other not yet acknowledged transaction, they can always be read _(and written)_ in parallel.
 *
 * If the source storage does provide a [version][StreamFeature.version] and [next-version][StreamFeature.nextVersion] for each [StreamFeature], it is safe to read and write all data in parallel. This is because the features can directly be written to the correct target without generating a _HISTORY_. Therefore, if stream is not sequential, and the source can provide [version][StreamFeature.version] and [nextVersion][StreamFeature.nextVersion] for each [StreamFeature], the stream can return all data as fast as it can read it. **Beware**: The recovery request might lag behind in this case, due to acknowledgements. So, for technical reasons, it may not be possible for the stream to move a recovery point forward until certain acknowledgements have been done. This means for the targets, in the case of a recovery, they may get a bunch of chunks again!
 *
 * A Naksha storage will read the transaction log, then use multiple connections in parallel to read the features of the transactions in parallel. It will hand them out as being read, except sequential mode was explicitly requested. So, native Naksha storages can read and write in parallel with maximum performance.
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
    val session: IStreamSession,

    /**
     * The [request][StreamRequest] that was used to open the stream. This is **not** the recovery request, it is the original request that was used to establish the stream.
     * @since 3.0
     */
    @get:JvmName("request")
    val request: StreamRequest
) : Iterator<StreamChunk>, AutoCloseable {

    /**
     * An array of not yet acknowledged _(outstanding)_ [chunks][StreamChunk].
     *
     * This is intended only for logs or CLI tool reporting of the current status.
     * @since 3.0
     */
    abstract val unacknowledgedChunks: Array<StreamChunk>

    /**
     * An array of [chunks][StreamChunk] that have been acknowledged, but are pending. That means, the current restoration point was not yet forwarded, because some still outstanding [chunks][StreamChunk] prevent this. Ones the outstanding and blocking [chunks][StreamChunk] are acknowledged, the pending [chunks][StreamChunk] will be removed from this list.
     *
     * This is intended only for logs or CLI tool reporting of the current status.
     * @since 3.0
     */
    abstract val acknowledgedChunks: Array<StreamChunk>

    /**
     * The estimated total amount of chunks that will be streamed.
     *
     * This is only an educated guess by the storage, it can be adjusted while streaming. A storage can return `-1` if it has simply no clue what to expect _(or not yet a clue)_. This is intended only for logs or CLI tool reporting of the current status.
     * @since 3.0
     */
    abstract val estimatedChunks: Long

    /**
     * The estimated total amount of tuples _(feature states)_ that will be streamed.
     *
     * This is only an educated guess by the storage, it can be adjusted while streaming. A storage can return `-1` if it has simply no clue what to expect _(or not yet a clue)_. This is intended only for logs or CLI tool reporting of the current status.
     * @since 3.0
     */
    abstract val estimatedTuples: Long

    /**
     * The amount of tuple _(feature states)_ that have been successfully read and acknowledged by the processors.
     * @since 3.0
     */
    abstract val acknowledgedTuples: Long

    /**
     * The amount of tuple _(feature states)_ that have been read, but not yet fully acknowledged by the processors.
     * @since 3.0
     */
    abstract val unacknowledgedTuples: Long

    /**
     * If the stream is generally recoverable in an error case.
     *
     * If a stream is not recoverable, then a premature [close] of the stream should be avoided!
     * @since 3.0
     */
    abstract val isRecoverable: Boolean

    /**
     * Tests if there is another outstanding [StreamChunk] available.
     *
     * If this method returns _false_, the reader should [close] the stream.
     * @return _true_ if there is another [StreamChunk] available; _false_ otherwise.
     * @since 3.0
     * @see close
     */
    abstract override operator fun hasNext(): Boolean

    /**
     * Tests if the stream is finished and has no more outstanding [chunks][StreamChunk].
     *
     * If [hasNext] returns _false_, this method can be used to wait for all outstanding [chunks][StreamChunk] before calling [close] or [getRecoveryRequest]. Calling [close] directly is generally safe, but does not allow to get a final recovery request.
     *
     * @param timeout the timeout after which to return with _false_. If `null`, zero, or negative, the method must not block, but return instantly.
     * @return _false_ if there are still outstanding [chunks][StreamChunk]; _true_ otherwise.
     */
    abstract fun isDone(timeout: Duration?): Boolean

    /**
     * Returns the next [StreamFeature] or [StreamTransaction].
     * @return the next [StreamFeature] or [StreamTransaction].
     * @since 3.0
     * @throws NoSuchElementException if there are no more elements. The reader should invoke [close] to wait for outstanding [acknowledgements][acknowledge].
     * @throws NakshaException with error [ILLEGAL_STATE][naksha.base.NakshaError.ILLEGAL_STATE] if the stream is closed without any recovery possibility.
     * @throws StreamException if the stream is closed for an error reason, e.g. disconnect or timeout. Optionally can be recovered via [IStreamSession.read] using the [StreamException.recoveryRequest].
     */
    abstract override operator fun next(): StreamChunk

    /**
     * Returns the next [StreamFeature] or [StreamTransaction].
     *
     * The given timeout is no exact measurement, especially when given less than 1 second.
     * @param timeout the timeout after which to return with `null`. If `null`, less than or zero, the method must not block, but return instantly with either the next [chunk][StreamChunk] or `null`.
     * @return the next [StreamFeature] or [StreamTransaction] or `null`, if the timeout was reached.
     * @since 3.0
     * @throws NoSuchElementException if there are no more elements. The reader should invoke [close] to wait for outstanding [acknowledgements][acknowledge].
     * @throws NakshaException with error [ILLEGAL_STATE][naksha.base.NakshaError.ILLEGAL_STATE] if the stream is closed without any recovery possibility.
     * @throws StreamException if the stream is closed for an error reason, e.g. disconnect or timeout. Optionally can be recovered via [IStreamSession.read] using the [StreamException.recoveryRequest].
     */
    @JsName("nextOrNull")
    abstract fun next(timeout: Duration?): StreamChunk?

    /**
     * Called by [StreamChunk.acknowledge] to acknowledge that the chunk has been processed successfully, will potentially move the recovery position forward and allow reading more [chunks][StreamChunk], to potentially unblocks [Stream.next].
     *
     * If the same chunk is acknowledged multiple times, additional calls will simply be ignored.
     * @param chunk the [StreamChunk] chunk to flag as processed.
     * @since 3.0
     */
    protected abstract fun acknowledge(chunk: StreamChunk)
    @Suppress("FunctionName")
    internal fun _acknowledge(chunk: StreamChunk) = acknowledge(chunk)

    /**
     * Called by [StreamChunk.fail], therefore, indirectly by [IStreamSession.write], to report that writing the given [StreamChunk] eventually failed unrecoverable. This does not imply that there is no chance of recovering at a later time, but right now it is not possible to finish the processing.
     *
     * This should cause [next] to throw an [StreamException], so that the reading is aborted, but can be recovered later _(ones the reason for the failure is fixed)_.
     * @param chunk the [StreamChunk] that was not written successfully.
     * @param reason the error reason.
     * @since 3.0
     * @see IStreamSession.write
     */
    protected abstract fun fail(chunk: StreamChunk, reason: NakshaError)
    internal fun _fail(chunk: StreamChunk, reason: NakshaError) {
        fail(chunk, reason)
    }

    /**
     * Tests if this stream is closed.
     * @return _true_ if the stream is closed; _false_ otherwise.
     * @since 3.0
     */
    abstract fun isClosed(): Boolean

    /**
     * Closes the stream gracefully.
     *
     * - This method should not be invoked unless [isDone] returns _true_.
     * - This method waits for [acknowledge] of all outstanding [chunks][StreamChunk], then closes the stream.
     * - Should a [next] call be outstanding, it is interrupted.
     * - If there are more [chunks][StreamChunk] available to read, it throws a [StreamException] with error [CLOSED][naksha.base.NakshaError.CLOSED], providing a recovery request.
     *
     * @since 3.0
     * @throws StreamException with a recovery request, if there are still outstanding chunks.
     * @throws NakshaException if recovery is not possible and there are outstanding chunks.
     * @see isDone
     */
    abstract override fun close()

    /**
     * Return a recovery request of the stream.
     *
     * Actually, this method serializes the current stream position into a [StreamRequest], which can be used later to open a new [Stream] via [IStreamSession.read], continuing reading from the current stream position. The current position is serialized gracefully, this means:
     * - This method waits for [acknowledge] of all outstanding [chunks][StreamChunk] before generates the recovery request.
     * - Should a [next] call be outstanding, it is blocked until the recovery request is generated.
     * - This method can be used for subscriptions to remember the last successful event read; in that case the stream should use [StreamRequest.sequential] mode.
     *
     * @param timeout if not `null`, the maximum duration to wait for a recoverable state.
     * @return the [StreamRequest] to be used for recovery with [IStreamSession.read], can be serialized and stored long term.
     * @throws NakshaException if any error prevents the creation of the recovery request in the given time, or [isRecoverable] is _false_.
     * @since 3.0
     */
    abstract fun getRecoveryRequest(timeout: Duration?): StreamRequest
}