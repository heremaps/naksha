package naksha.model.streaming

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
    features: Array<StreamFeature>
): StreamChunk(stream, features)