package naksha.model.streaming

import naksha.base.Id
import naksha.base.Version.VersionCompanion.HEAD
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Builds a fully configured [StreamRequest].
 *
 * The identifiers of the target database, catalog, and collection, together with the expected writer concurrency, are required when this builder is created. All remaining settings have the same defaults as [StreamRequest], so [build] always creates a valid request.
 *
 * @param databaseId the identifier of the database to read from.
 * @param catalogId the identifier of the catalog to read from.
 * @param collectionId the identifier of the collection to read from.
 * @since 3.0
 */
@JsExport
class StreamRequestBuilder(
    val databaseId: Id,
    val catalogId: Id,
    val collectionId: Id
) {
    /**
     * Builds a stream request using textual identifiers.
     *
     * @param databaseId the textual identifier of the database to read from.
     * @param catalogId the textual identifier of the catalog to read from.
     * @param collectionId the textual identifier of the collection to read from.
     * @since 3.0
     */
    @JsName("newStreamRequestBuilder")
    constructor(
        databaseId: String,
        catalogId: String,
        collectionId: String
    ) : this(Id(databaseId), Id(catalogId), Id(collectionId))

    /** If the stream should be read sequentially. */
    var sequential: Boolean = false

    /** The maximal version to read; defaults to [HEAD]. */
    var version: Long = HEAD.number

    /** Whether deleted features are included; defaults to `true`. */
    var queryDeleted: Boolean = true

    /** Whether all states _([Tuple][naksha.model.Tuple])_ between `minVersion` _(inclusive)_ and `version` _(inclusive)_ should be returned _(true)_ or just the latest state, closest to [version] _(false)_; defaults to _true_. */
    var queryHistory: Boolean = true

    /** The minimal version to read; defaults to `0`. */
    var minVersion: Long = 0L

    /** Whether the storage may ignore transaction ordering; defaults to `false`. */
    var ignoreTransactions: Boolean = false

    /** The preferred number of features per stream chunk; defaults to `1000`. */
    var chunkSize: Int = 1000

    /** The timeout for stream reads and acknowledgements; defaults to five minutes. */
    var timeout: Duration = 5.minutes

    /**
     * Sets [sequential].
     * @param value _true_ to force sequential read; _false_ otherwise _(default)_.
     * @return this builder.
     */
    fun withSequential(value: Boolean): StreamRequestBuilder = apply { sequential = value }

    /**
     * Sets [version].
     * @param value the maximal version to read.
     * @return this builder.
     */
    fun withVersion(value: Long): StreamRequestBuilder = apply { version = value }

    /**
     * Sets [queryDeleted].
     * @param value whether deleted features are included.
     * @return this builder.
     */
    fun withQueryDeleted(value: Boolean): StreamRequestBuilder = apply { queryDeleted = value }

    /**
     * Sets [queryHistory].
     * @param value whether all states in the requested version range are included.
     * @return this builder.
     */
    fun withQueryHistory(value: Boolean): StreamRequestBuilder = apply { queryHistory = value }

    /**
     * Sets [minVersion].
     * @param value the minimal version to read.
     * @return this builder.
     */
    fun withMinVersion(value: Long): StreamRequestBuilder = apply { minVersion = value }

    /**
     * Sets [ignoreTransactions].
     * @param value whether the storage may ignore transaction ordering.
     * @return this builder.
     */
    fun withIgnoreTransactions(value: Boolean): StreamRequestBuilder = apply { ignoreTransactions = value }

    /**
     * Sets [chunkSize].
     * @param value the preferred number of features per stream chunk.
     * @return this builder.
     */
    fun withChunkSize(value: Int): StreamRequestBuilder = apply { chunkSize = value }

    /**
     * Sets [timeout].
     * @param value the timeout for stream reads and acknowledgements.
     * @return this builder.
     */
    fun withTimeout(value: Duration): StreamRequestBuilder = apply { timeout = value }

    /**
     * Creates a stream request from the required constructor values and the current settings.
     *
     * @return the configured stream request.
     */
    fun build(): StreamRequest = StreamRequest(
        databaseId = databaseId,
        catalogId = catalogId,
        collectionId = collectionId,
        version = version,
        queryDeleted = queryDeleted,
        queryHistory = queryHistory,
        sequential = sequential,
        minVersion = minVersion,
        ignoreTransactions = ignoreTransactions,
        chunkSize = chunkSize,
        timeout = timeout,
    )
}