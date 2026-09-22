package naksha.model.streaming

import naksha.base.Id
import naksha.base.PlatformObject
import kotlin.js.JsExport
import kotlin.jvm.JvmName

/**
 * Describes a Naksha compatible transaction.
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
     * The [Naksha compatible version][naksha.base.Version] that this transaction is linked to.
     *
     * A new version is always generated as result of committing a transaction, therefore, every transaction relates exactly to one version. Beware that all features in the transaction must have the same [version][StreamFeature.version], but not necessarily the same [nextVersion][StreamFeature.nextVersion].
     * @since 3.0
     */
    @get:JvmName("version")
    val version: Long,

    /**
     * The identifier of the transaction object, if the source storage has transaction logs.
     * @since 3.0
     */
    @get:JvmName("id")
    val id: Id?,

    /**
     * The transaction details, if the source storage has transaction logs.
     * @since 3.0
     */
    @get:JvmName("transaction")
    val transaction: PlatformObject?,

    /**
     * The features being part of this transaction.
     * @since 3.0
     */
    features: Array<StreamFeature>,
): StreamChunk(stream, features)
