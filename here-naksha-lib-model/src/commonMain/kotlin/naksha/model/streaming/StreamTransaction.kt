package naksha.model.streaming

import naksha.base.AtomicInt
import naksha.base.Platform
import naksha.base.PlatformObject
import kotlin.js.JsExport
import kotlin.jvm.JvmName

/**
 * Describes a transaction.
 * @since 3.0
 */
@JsExport
open class StreamTransaction(
    /**
     * The stream to which the transaction belongs.
     * @since 3.0
     */
    stream: Stream,

    /**
     * The version of this transaction.
     * @since 3.0
     */
    @get:JvmName("version")
    val version: Long,

    /**
     * The transaction details, if available in the source storage.
     * @since 3.0
     */
    @get:JvmName("transaction")
    val transaction: PlatformObject?,

    /**
     * The features being part of this transaction.
     * @since 3.0
     */
    features: Array<StreamFeature>,

    /**
     * The amount of outstanding acknowledgements; defaults to `1`.
     *
     * The stream reading thread should set the correct value, before handing over the chunk to the writers. It defaults to `1` assuming that each chunk need only to be processed ones. If the same data should be copied concurrently into multiple storages, the count can be increased.
     * @see acknowledge
     * @see failed
     */
    acknowledgeCount: AtomicInt = AtomicInt(1)
): StreamChunk(stream, features, acknowledgeCount) {

    override fun copy(): StreamTransaction = StreamTransaction(
        stream,
        version,
        Platform.copy(transaction, true),
        features,
        acknowledgeCount
    )
}