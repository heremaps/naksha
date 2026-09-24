package naksha.model.streaming

import naksha.base.AnyObject
import naksha.base.Id
import naksha.base.NotNullIdProperty
import naksha.base.NotNullProperty
import naksha.base.NullableProperty
import naksha.base.Version.VersionCompanion.HEAD
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.jvm.JvmName
import kotlin.jvm.JvmOverloads
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.DurationUnit
import kotlin.time.toDuration

/**
 * The parameters that describe the stream to be opened.
 *
 * Generally streams fit into the Naksha world. They are based upon features, which are `JBON`/`JSON` compatible objects, persisting out of time-ordered states. Each feature has a certain time when it starts to exist with its first state having [action][naksha.base.Action] set to [CREATE][naksha.base.Action.CREATE]. Each state is by definition immutable and called [Tuple][naksha.model.Tuple] in the Naksha world.
 *
 * Generally, a stream transfers **features** located in a specific **collection**, from a specific **catalog** of a specific **database** from a source **storage**. The stream will deliver all [Tuple][naksha.model.Tuple] _(feature states)_ in the given version range, being between `minVersion` and `version`.
 *
 * If [queryHistory] is explicitly set to **false** _(default is true)_, the stream will only deliver the latest [Tuple][naksha.model.Tuple] in the given version range. If there is no [Tuple][naksha.model.Tuple] within the given version range, the feature will not be part of the stream.
 *
 * When setting [queryDeleted] explicitly to **false** _(default is true)_, all features that have the latest [Tuple][naksha.model.Tuple] within the given version range in action [DELETE][naksha.base.Action.DELETE], are ignored. No [Tuple][naksha.model.Tuple] of these features will be added to the stream.
 *
 * ## Transaction Streaming
 * If the source does have a transaction log and [ignoreTransactions] is **false** _(default)_, then the stream **must** read the transaction log and filter it by the given version range. It afterwards will fetch all [Tuple][naksha.model.Tuple] belonging to these transactions and return them in history order via [Stream.next].
 *
 * The filters [queryHistory] and [queryDeleted] can lead to empty [StreamTransaction] chunks. The stream will never drop a transaction in the given version range, but it may omit all [Tuple][naksha.model.Tuple] from the [StreamTransaction], because they do not match the provided filters.
 *
 * ## Streaming without transactions
 * If the source does not contain transactions logs or [ignoreTransactions] is explicitly set to **true** _(default is false)_, then the stream **must** read only [Tuple][naksha.model.Tuple] from the source. Therefore, the [Stream] will return [StreamChunk]'s with all [Tuple][naksha.model.Tuple] in the given version range.
 *
 * The storage is free to reorder and regroup the [Tuple][naksha.model.Tuple] so that each [StreamChunk] contains [chunkSize] number of [Tuple][naksha.model.Tuple]. Still, it should try to stick to historic order, even while not strictly required.
 *
 * ## Usage Hints
 * If the latest version _(HEAD)_ is wanted, set [version] to [Version.HEAD][naksha.base.Version.HEAD] _(`9,007,199,254,740,991` aka `2^53-1`)_. In JavaScript this is as well defined as `Number.MAX_SAFE_INTEGER`.
 *
 * To stream a specific version including all history:
 *- Set [version] to the desired version and [queryHistory] to **true** _(default)_. This will return all state of all features from the very first moment up to this specific [version]. Do not provide [minVersion], leave it to its default `0`. If only living features, so those not being deleted, are needed, then additionally set [queryDeleted] to **false** _(default is true)_.
 *
 * To stream a specific version without history:
 * - Set [version] to the desired version and [queryHistory] to **false** _(default is true)_. This will return only the latest state of all features in this specific [version]. Do not provide [minVersion], leave it to its default `0`. If only living features, so those not being deleted, are needed, then additionally set [queryDeleted] to **false** _(default is true)_.
 *
 * To constantly replicate all data from a collection:
 * - Set [version] to [HEAD][naksha.base.Version.HEAD], [ignoreTransactions] to **true**, [queryHistory] to **true** _(default)_, and optionally [queryDeleted] to **false** _(if deleted features are not of any importance)_. This will return all [Tuple][naksha.model.Tuple] of all features from the very first moment up to this specific [version]. Do not provide [minVersion], leave it to its default `0`.
 * - When the stream is closed remember [Stream.minVersion] to continuation.
 * - When an update is needed, use the above request, but set [minVersion] to the remembered value.
 * - **Note**: This only replicates the features and its states, but not the transaction logs. Often this is enough for read replicas, and as the transaction logs are a dedicated collection, they can be replicated as well in parallel, closing the link to the replicated data. This way is the fastest way to do replication with a minor risk of inconsistent replication.
 *
 * To continue streaming, either use a recovery request _(strongly recommended)_, or repeat the original request with [minVersion] being set to [Stream.minVersion] and [version] set to [HEAD][naksha.base.Version.HEAD]. This is the poor man's recovery option.
 * @since 3.0
 * @see Stream
 * @see StreamRequestBuilder
 */
@JsExport
open class StreamRequest(): AnyObject() {

    /**
     * Create a fully configured stream request.
     * @param databaseId the identifier of the database to read from.
     * @param catalogId the identifier of the catalog to read from.
     * @param collectionId the identifier of the collection to read from.
     * @param version the maximal version to read.
     * @param queryDeleted whether deleted features should be part of the stream; defaults to _true_.
     * @param queryHistory whether all states _([Tuple][naksha.model.Tuple])_ between `minVersion` _(inclusive)_ and `version` _(inclusive)_ should be returned _(true)_ or just the latest state, closest to `version` _(false)_; defaults to _true_.
     * @param sequential whether the stream should be forced into sequential reading _(defaults to false)_.
     * @param minVersion the minimal version to read; defaults to `0`. The only purpose of this parameter is to read change-sets.
     * @param ignoreTransactions whether transactions should be ignored, even while the storage supports transactions. This allows certain optimizations to be performed, like reordering features and re-grouping to created filled chunks for faster writing and eventually faster copy; defaults to _false_.
     * @param chunkSize the amount of features to pack into each [StreamChunk] chunk; only applies when the storage does not support transactions **or** `ignoreTransactions` was explicitly set to _true_; defaults to `1000`.
     * @since 3.0
     * @see StreamRequestBuilder
     * @see StreamRequest
     */
    @JvmOverloads
    @JsName("newStreamRequest")
    constructor(
        databaseId: Id,
        catalogId: Id,
        collectionId: Id,
        version: Long = HEAD.number,
        queryDeleted: Boolean = true,
        queryHistory: Boolean = true,
        sequential: Boolean = false,
        minVersion: Long = 0L,
        ignoreTransactions: Boolean = false,
        chunkSize: Int = 1000
    ): this() {
        set("databaseId", databaseId)
        set("catalogId", catalogId)
        set("collectionId", collectionId)
        set("sequential", sequential)
        set("queryDeleted", queryDeleted)
        set("queryHistory", queryHistory)
        set("version", version)
        set("minVersion", minVersion)
        set("ignoreTransactions", ignoreTransactions)
        set("chunkSize", chunkSize)
    }

    /**
     * The identifier of the database to read.
     * @since 3.0
     */
    @get:JvmName("databaseId")
    val databaseId: Id by ID_NOT_NULL

    /**
     * The identifier of the catalog to read.
     * @since 3.0
     */
    @get:JvmName("catalogId")
    val catalogId: Id by ID_NOT_NULL

    /**
     * The identifier of the collection to read.
     * @since 3.0
     */
    @get:JvmName("collectionId")
    val collectionId: Id by ID_NOT_NULL

    /**
     * If the stream is forced into sequential read mode.
     *
     * This forces the stream reader into sequential mode, it does not load [chunks][StreamChunk] in parallel and does not return more than one [chunk][StreamChunk] at a time. It as well guarantees the order. This is only necessary for certain special targets _(that can't support parallel writing)_, e.g. useful for subscription.
     * @since 3.0
     */
    @get:JvmName("sequential")
    val sequential: Boolean by BOOLEAN_FALSE

    /**
     * If deleted features should be read.
     * @since 3.0
     */
    @get:JvmName("queryDeleted")
    val queryDeleted: Boolean by BOOLEAN_TRUE

    /**
     * If all states _([Tuple][naksha.model.Tuple])_ between [minVersion] and [version] should be returned or just the latest state, closest to [version].
     * @since 3.0
     */
    @get:JvmName("queryHistory")
    val queryHistory: Boolean by BOOLEAN_TRUE

    /**
     * The maximal version to read, defaults to [HEAD].
     * @since 3.0
     */
    @get:JvmName("version")
    val version: Long by LONG_HEAD

    /**
     * The minimal version to start reading, defaults to `0`.
     * @since 3.0
     */
    @get:JvmName("minVersion")
    val minVersion: Long by LONG_0

    /**
     * If transactions should be ignored, even while the storage supports them.
     *
     * Turning this option on allow the implementation to change the streaming order. It means, only keep the historic order per feature intact, not across features. That means the storage can reorder features and pack them into [StreamChunk] chunks, so that the only guarantee is that the history of each individual feature stays intact, but the consistency between features is no longer ensured.
     *
     * Turning this option _on_ will speed up reading and writing drastically, but breaks history and transaction logs.
     * @since 3.0
     */
    @get:JvmName("ignoreTransactions")
    val ignoreTransactions: Boolean by BOOLEAN_FALSE

    /**
     * The amount features to pack into each [StreamChunk] chunk. This is a soft-limit that only applies if the source storage either does not support transactions, no history is requested
     * @since 3.0
     */
    @get:JvmName("chunkSize")
    val chunkSize: Int by INT_1000

    companion object StreamRequestCompanion {
        private val ID_NOT_NULL = NotNullIdProperty<StreamRequest>(randomId = false)
        private val BOOLEAN_FALSE = NotNullProperty<StreamRequest, Boolean>(Boolean::class) { _,_ -> false }
        private val BOOLEAN_TRUE = NotNullProperty<StreamRequest, Boolean>(Boolean::class) { _,_ -> true }
        private val LONG_HEAD = NotNullProperty<StreamRequest, Long>(Long::class) { _,_ -> HEAD.number }
        private val LONG_0 = NotNullProperty<StreamRequest, Long>(Long::class) { _,_ -> 0L }
        private val INT_1000 = NotNullProperty<StreamRequest, Int>(Int::class) { _,_ -> 1000 }
    }
}