@file:Suppress("OPT_IN_USAGE")

package naksha.model

import naksha.base.Id
import naksha.base.unsupportedOp
import naksha.model.objects.NakshaCollection
import naksha.model.objects.NakshaCatalog
import naksha.model.request.*
import kotlin.js.JsExport
import kotlin.js.JsName

/**
 * When a session is opened, it is bound to the context in which the session shall operate.
 *
 * A read session will acquire a connection from a connection pool whenever a read is performed, and release the connections instantly after the read is done _(logically, not guaranteed)_.
 *
 * A write session will acquire a connection when the first write operation is executed, and stick with it until `commit`, `rollback` or [close] invoked. All reads after write will always utilize this single connection to ensure consistency. Before the first write operation, the optimizer is free to utilize multiple connections to read in parallel, but after the first write execution, a single connection must be used for all reading and writing, to guarantee consistency. Therefore, it is recommended to first perform all reads, then to perform the writes. The parallel reading can be disabled, if needed, using the [SessionOptions.parallel] switch.
 *
 * Beware that this description is a logical one, the details are implementation dependent, but this description provides general guidance to implementors about the behavior that clients will expect.
 */
@JsExport
interface ISession : AutoCloseable {
    /**
     * The storage to which the session is bound.
     * @since 3.0
     */
    val storage: IStorage

    /**
     * The socket timeout in milliseconds.
     * @since 3.0
     */
    var socketTimeout: Int

    /**
     * The statement timeout in milliseconds.
     * @since 3.0
     */
    var stmtTimeout: Int

    /**
     * The lock timeout in milliseconds.
     * @since 3.0
     */
    var lockTimeout: Int

    /**
     * The options when opening new connections.
     *
     * The options are mostly immutable, except for the timeout values, for which there are dedicated setter.
     * @since 3.0
     */
    val options: SessionOptions

    /**
     * Returns the [MemberProcessorMap] for this session.
     *
     * Use the map to register, remove, or inspect [IMemberProcessor] instances for individual member processing. Processors are invoked in the order in which they were added.
     * @return the member processor map.
     * @since 3.0
     */
    val processors: MemberProcessorMap

    // TODO: Define a streaming API (full table scan) to consume all features from a collection.
    //       This API is designed to backup data, or to execute a read request with a huge cardinality,
    //       therefore it should always operate on snapshots (specific versions).
    //       For example, when a result-set is bigger than 16,777,215, it has a huge cardinality, and
    //       performance is normally better doing a full table scan.
    //       We need to allow reading only HEAD, or all data, including HISTORY. Reading in parallel should
    //       be supported, when the collection is partitioned, and the history data should be read always in
    //       parallel to the HEAD.
    //       !!! This is as high throughput API, not a low latency !!!

    /**
     * Execute the given [Request].
     *
     * The read-only session will only be able to execute [ReadRequest]'s and throw an [naksha.base.NakshaError.UNSUPPORTED_OPERATION], when a [WriteRequest] is provided.
     * @param request the request to execute.
     * @return the response.
     * @since 2.0.7
     */
    fun execute(request: Request): Response

    /**
     * Force parallel execution of the given [Request], if supported, otherwise fallback to a normal [execute]. This differs from [SessionOptions.parallel] in that it does not have such strong guarantee requirements, it is mainly for bulk loading or other situations, in which performance matters more than 100% safety.
     *
     * For [ReadRequest]'s this method is relatively safe, because it will only use multiple connections in parallel in the background, joining the result-set, returning the used connections back to a connection pool after having performed the read. The only side effect is, that all pending _(uncommitted)_ changes are not visible to the additional connections being used in the background, so it is strongly recommended to not use this method on connections with pending changes.
     *
     * ## Warning
     * For write requests there is a minor risk to create a broken state in the storage! This depends on the exact implementation, but it needs to be an accepted risk, when using `executeParallel` for write requests.
     *
     * In `lib-psql`, even after all requests have been executed successfully, committing may fail partially, for example when only one connection aborts or the server crashes in the middle of the operation, while having committed already some connections, with others not yet to being done.
     *
     * Parallel executions on partitioned tables can be up to 256 times faster than sequential ones, depending on how many partitions there are, and on the available network bandwidth. This is, because all partitions are queried in parallel. The biggest PostgresQL database server available currently (at the time of writing, mid 2024) can be installed on an EC2 [r6idn.metal](https://aws.amazon.com/ec2/instance-types/r6i/) instance, providing 200 Gbps of networking, which would allow theoretically to satisfy 40 parallel connections or 20, when being in the same [placement group](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/placement-strategies.html#placement-groups-cluster), see [AWS ec2-instance-network-bandwidth](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/ec2-instance-network-bandwidth.html). So, with two `r6idn.metal` machines, one being the database, and the other being the client, in the same `placement group`, theoretically 20 concurrent connections will reach the limit of 200 Gbps. However, because the CPU load has a factor too, and the kernel configuration for TCP as well, it is recommended to use 64 partitions, so that each partition receives 3.125 Gbps of traffic only. The reason is, that this uses all CPU perfectly fine and works as well, when the clients are distributed on 64 EMR nodes. For all of this to be working eventually, especially when reading later, it is recommended to think always about the CPU limit, because reading requires nearly always to query all partitions in parallel to be efficient, this requires a lot of CPU resources on the server. Therefore, the best often is to find a middle ground between extreme write throughput, and query performance. In the above example, 8 to 16 partitions (and therefore 8 to 16 concurrent connections) would still be able to each between 40/80 and 80/160 Gbps of throughput, but make reading much more efficient. As the EBS volume is anyway limited to 100 Gbps, and for updates or deleted multiple writes are needed (move to history), and WAL logs are as well sharing this bandwidth, it is anyway unlikely to satisfy even 100 Gbps of the bandwidth for writing. This is not true for the temporary tablespace located on the ephemeral storage, where 200 Gbps can be fully satisfied. **In a nutshell, planning is essential!**
     * @since 3.0
     */
    fun executeParallel(request: Request): Response

    /**
     * Tests if the session is closed.
     * @return _true_ if the session is closed.
     * @since 3.0
     */
    fun isClosed(): Boolean

    /**
     * Closing a session will roll back the underlying connection, and then return it to their connection pool. After closing a session
     * any further methods invocation will raise an [IllegalStateException].
     * @since 2.0.7
     */
    override fun close()

    /**
     * Returns the catalog with the given identifier.
     *
     * If invoked on a [write session][IWriteSession] before committing changes, it will return catalogs that were created in the current session, but beware that these catalogs may eventually fail to commit.
     * @param id the catalog-id for which to return the latest _HEAD_ state.
     * @return the catalog; _null_ if no such catalog exists.
     * @since 3.0
     */
    @JsName("getCatalogByIdWithoutTombstone")
    fun getCatalogById(id: Id): NakshaCatalog?
        = getCatalogByNumber(id.number.toInt(), false)

    /**
     * Returns the catalog for the given identifier.
     *
     * If invoked on a [write session][IWriteSession] before committing changes, it will return catalogs that were created in the current session, but beware that these catalogs may eventually fail to commit.
     * @param id the catalog-id for which to return the latest _HEAD_ state.
     * @param allowTombstone if tombstones are returned, so _HEAD_ states indicating that the catalog was deleted.
     * @return the catalog; _null_ if no such catalog exists.
     * @since 3.0
     */
    fun getCatalogById(id: Id, allowTombstone: Boolean): NakshaCatalog?
        = getCatalogByNumber(id.number.toInt(), allowTombstone)

    /**
     * Returns the catalog for the given number.
     *
     * If invoked on a [write session][IWriteSession] before committing changes, it will return catalogs that were created in the current session, but beware that these catalogs may eventually fail to commit.
     * @param catalogNumber the catalog-number for which to return the latest _HEAD_ state.
     * @return the catalog; _null_ if no such catalog exists.
     * @since 3.0
     */
    @JsName("getCatalogByNumberWithoutTombstone")
    fun getCatalogByNumber(catalogNumber: Int): NakshaCatalog?
        = getCatalogByNumber(catalogNumber, false)

    /**
     * Returns the catalog for the given number.
     *
     * If invoked on a [write session][IWriteSession] before committing changes, it will return catalogs that were created in the current session, but beware that these catalogs may eventually fail to commit.
     * @param catalogNumber the catalog-number for which to return the latest _HEAD_ state.
     * @param allowTombstone if tombstones are returned, so _HEAD_ states indicating that the catalog was deleted.
     * @return the catalog; _null_ if no such catalog exists.
     * @since 3.0
     */
    fun getCatalogByNumber(catalogNumber: Int, allowTombstone: Boolean): NakshaCatalog?

    /**
     * Returns the collection for the given identifier.
     *
     * If invoked on a [write session][IWriteSession] before committing changes, it will return collections that were created in the current session, but beware that these collections may eventually fail to commit.
     * @param catalog the catalog to query.
     * @param id the collection-id for which to return the latest _HEAD_ state.
     * @return the collection; _null_ if no such collection exists.
     * @since 3.0
     */
    @JsName("getCollectionByIdWithoutTombstone")
    fun getCollectionById(catalog: NakshaCatalog, id: Id): NakshaCollection?
        = getCollectionByNumber(catalog, id.number.toInt(), false)

    /**
     * Returns the collection for the given identifier.
     *
     * If invoked on a [write session][IWriteSession] before committing changes, it will return collections that were created in the current session, but beware that these collections may eventually fail to commit.
     * @param catalog the catalog to query.
     * @param id the collection-id for which to return the latest _HEAD_ state.
     * @param allowTombstone if tombstones are returned, so _HEAD_ states indicating that the collection was deleted.
     * @return the collection; _null_ if no such collection exists.
     * @since 3.0
     */
    fun getCollectionById(catalog: NakshaCatalog, id: Id, allowTombstone: Boolean): NakshaCollection?
        = getCollectionByNumber(catalog, id.number.toInt(), allowTombstone)

    /**
     * Returns the collection for the given number.
     *
     * If invoked on a [write session][IWriteSession] before committing changes, it will return collections that were created in the current session, but beware that these collections may eventually fail to commit.
     * @param catalog the catalog to query.
     * @param collectionNumber the collection-number for which to return the latest _HEAD_ state.
     * @return the collection; _null_ if no such collection exists.
     * @since 3.0
     */
    @JsName("getCollectionByNumberWithoutTombstone")
    fun getCollectionByNumber(catalog: NakshaCatalog, collectionNumber: Int): NakshaCollection?
        = getCollectionByNumber(catalog, collectionNumber, false)

    /**
     * Returns the collection for the given number.
     *
     * If invoked on a [write session][IWriteSession] before committing changes, it will return collections that were created in the current session, but beware that these collections may eventually fail to commit.
     * @param catalog the catalog to query.
     * @param collectionNumber the collection-number for which to return the latest _HEAD_ state.
     * @param allowTombstone if tombstones are returned, so _HEAD_ states indicating that the collection was deleted.
     * @return the collection; _null_ if no such collection exists.
     * @since 3.0
     */
    fun getCollectionByNumber(catalog: NakshaCatalog, collectionNumber: Int, allowTombstone: Boolean): NakshaCollection?

    /**
     * Restores a result-set that was serialized via [ResultSet.getBytes]. This only works for results that where serialized by this storage.
     * @param resultSetBytes the bytes returned by [ResultSet.getBytes].
     * @return the restored result-set
     * @since 3.0
     * @throws naksha.base.NakshaException if any error occurred, for example the results are no longer available or the bytes where serialized from a different storage; if the storage does not implement the method the error will be [UNSUPPORTED_OPERATION][naksha.base.NakshaError.NakshaErrorCompanion.UNSUPPORTED_OPERATION].
     */
    fun restoreResultSet(resultSetBytes: ByteArray): ResultSet {
        throw unsupportedOp("restoreResultSet")
    }

    /**
     * Loads the [Tuple] by their [tuple-number][naksha.base.TupleNumber].
     *
     * @param tupleNumbers the [TupleNumber][naksha.base.TupleNumber] of the [Tuple] to load.
     * @param cacheOnly if the tuples should only be loaded form cache.
     * @return the loaded [Tuple], the result is in the same order as the input, contains `null` for those [Tuple] that failed to load _(should only happen for cache-only access or when invalid tuple-numbers are given)_.
     */
    fun loadTuples(tupleNumbers: ITupleNumberArray, cacheOnly: Boolean): Array<Tuple?>
}
