package naksha.model.streaming

import naksha.base.AnyObject
import naksha.base.Id
import naksha.base.NotNullIdProperty
import naksha.base.NotNullProperty
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
 * Implementors of [IStreamSession][naksha.model.IStreamSession] are free to
 * @since 3.0
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
     */
    @get:JvmName("chunkSize")
    val chunkSize: Int by INT_1000

    companion object StreamRequestCompanion {
        private val ID_NOT_NULL = NotNullIdProperty<StreamRequest>()
        private val BOOLEAN_FALSE = NotNullProperty<StreamRequest, Boolean>(Boolean::class) { _,_ -> false }
        private val BOOLEAN_TRUE = NotNullProperty<StreamRequest, Boolean>(Boolean::class) { _,_ -> true }
        private val LONG_HEAD = NotNullProperty<StreamRequest, Long>(Long::class) { _,_ -> HEAD.number }
        private val LONG_0 = NotNullProperty<StreamRequest, Long>(Long::class) { _,_ -> 0L }
        private val INT_1000 = NotNullProperty<StreamRequest, Int>(Int::class) { _,_ -> 1000 }
    }
}