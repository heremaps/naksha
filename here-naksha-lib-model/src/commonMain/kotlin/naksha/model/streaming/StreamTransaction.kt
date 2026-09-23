package naksha.model.streaming

import naksha.base.Id
import naksha.base.PlatformObject
import naksha.model.objects.NakshaTx
import kotlin.js.JsExport
import kotlin.jvm.JvmName

/**
 * Describes a Naksha compatible transaction.
 *
 * ### Note to writers
 * Transactions in Naksha can modify features located in multiple collections, even in different maps. As a stream is linked to a single collection, transactions are not restored consistently. In other words, even while the transaction logs will be read complete, not all changes referred by these transactions are restorable using a single stream. To restore all data, all other collections need to be streamed as well.
 *
 * If the client requests a lower bound, using [minVersion][StreamRequest.minVersion], then it is even possible that certain [Tuple][naksha.model.Tuple] are excluded from the steam and therefore, even while being part of the same collection, not part of the chunk.
 *
 * A result of this design is that writers need to be aware that not only feature states can be received multiple times, they can as well be missing or only be received partially. The transactions can be received as well multiple times.
 *
 * Writes need to ignore all duplicates, except the state changes.
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
     * The transaction details in Naksha format, if the source storage has transaction logs.
     * @since 3.0
     */
    @get:JvmName("transaction")
    val transaction: NakshaTx?,

    /**
     * The features being part of this transaction.
     * @since 3.0
     */
    features: Array<StreamFeature>,
): StreamChunk(stream, features)
