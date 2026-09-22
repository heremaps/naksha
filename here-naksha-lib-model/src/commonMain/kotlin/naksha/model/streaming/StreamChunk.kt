package naksha.model.streaming

import naksha.base.AtomicInt
import naksha.base.NakshaError
import naksha.base.illegalArg
import naksha.base.illegalState
import kotlin.js.JsExport
import kotlin.jvm.JvmName

/**
 * A chunk of features with the guarantee that it does not contain multiple states of same feature _(so with the same [identifier][naksha.base.Id])_. Therefore, the chunk does not contain the same feature multiple times.
 *
 * The order of the provided features is not significant. They should be consumed and stored together in an atomic way. When features have been processed the steam must be notified by closing the chunk.
 *
 * After being returned by a [Stream], this chunk requires initialization by setting the targets via [setTargets].
 * @since 3.0
 * @see setTargets
 * @see Stream
 */
@JsExport
open class StreamChunk(
    /**
     * The stream to which this chunk belongs.
     * @since 3.0
     */
    @get:JvmName("stream")
    val stream: Stream,

    /**
     * The features being part of this chunk.
     * @since 3.0
     * @see StreamFeature
     */
    @get:JvmName("features")
    val features: Array<StreamFeature>
) {
    companion object StreamChunkCompanion {
        /**
         * The [acknowledgeCount] returned while a [StreamChunk] is not yet initialized (`-2`), so [setTargets] has not yet been called.
         * @since 3.0
         */
        const val UNINITIALIZED = -2

        /**
         * The [acknowledgeCount] returned when a [StreamChunk] failed (`-1`).
         * @since 3.0
         */
        private const val FAILED = -1

        /**
         * The [acknowledgeCount] returned when a [StreamChunk] succeeded (`0`), a value greater than `0` means there are outstanding acknowledgements.
         * @since 3.0
         */
        private const val OK = 0
    }

    // -2 = uninitialized
    // -1 = failed
    // 0 = done successfully
    // 1+ = initialized, waiting for acknowledgements
    private val _acknowledgeCount: AtomicInt = AtomicInt(UNINITIALIZED)

    /**
     * Sets the target counter, therefore, the amount of [acknowledgements][acknowledge] expected.
     *
     * **This method must be called excatly ones before handing over the chunk to the targets _(processors or writers)_!**
     * @param count the target count.
     * @since 3.0
     * @throws naksha.base.NakshaException with error [ILLEGAL_STATE][naksha.base.NakshaError.ILLEGAL_STATE] if the targets are already set; with error [ILLEGAL_ARGUMENT][naksha.base.NakshaError.ILLEGAL_ARGUMENT] if `count` is not greater than `0`.
     */
    open fun setTargets(count: Int) {
        if (count <= 0) throw illegalArg("Invalid target count given, must be greater than 0, got $count")
        if (!_acknowledgeCount.compareAndSet(UNINITIALIZED, count)) {
            throw illegalState("Targets are already set")
        }
    }

    /**
     * The amount of outstanding acknowledgements.
     * @see UNINITIALIZED
     * @see FAILED
     * @see OK
     * @see acknowledge
     * @see fail
     */
    @get:JvmName("acknowledgeCount")
    val acknowledgeCount: Int
        get() = _acknowledgeCount.get()

    /**
     * If the chunk was processed successfully.
     *
     * While a chunk is not [failed] nor [ok], it is currently being processed.
     * @since 3.0
     */
    @get:JvmName("ok")
    val ok: Boolean
        get() = _acknowledgeCount.get() == OK

    /**
     * If processing the chunk failed _(at least at one target)_.
     *
     * While a chunk is not [failed] nor [ok], it is currently being processed.
     * @since 3.0
     */
    @get:JvmName("failed")
    val failed: Boolean
        get() = _acknowledgeCount.get() == FAILED

    /**
     * A thread safe failure reporting, called by a target to report a failed chunk.
     *
     * Sets [acknowledgeCount] to `-1` and calls [Stream.fail]. Only the first error reported will pass through the reason.
     * @param reason the reason for the failure to be reported to the reader, which will report it back to the reading/processing thread.
     * @since 3.0
     * @throws StreamException if the reading can be recovered later _**(requires a new stream)**_.
     * @see naksha.model.IStreamSession.write
     */
    open fun fail(reason: NakshaError) {
        while (true) {
            val count = _acknowledgeCount.get()
            if (count <= 0) return
            if (_acknowledgeCount.compareAndSet(count, FAILED)) {
                stream._fail(chunk = this, reason = reason)
                return
            }
            // Failed to update acknowledge count due to concurrent modifications, retry.
        }
    }

    /**
     * A thread safe acknowledgement, called e.g. by a target to acknowledge a successful chunk processing.
     *
     * Decrements the [acknowledgeCount], if reaching `0` notifies the [Stream] by calling [Stream.acknowledge]. The method is thread safe.
     * @return `0` if this was the last acknowledgment, `-1` when some writing failed, a positive value if there are more processing.
     * @since 3.0
     * @throws naksha.base.NakshaException with error [ILLEGAL_STATE][naksha.base.NakshaError.ILLEGAL_STATE], if the acknowledgement count is already at zero.
     * @see naksha.model.IStreamSession.write
     */
    open fun acknowledge(): Int {
        while (true) {
            val count = _acknowledgeCount.get()
            if (count == 0) throw illegalState("Too many acknowledgments!")
            if (count < 0) return FAILED
            // count > 0
            val new_count = count - 1
            if (_acknowledgeCount.compareAndSet(count, new_count)) {
                if (new_count == 0) stream._acknowledge(this)
                return new_count
            }
            // Failed to update acknowledge count due to concurrent modifications, retry.
        }
    }
}