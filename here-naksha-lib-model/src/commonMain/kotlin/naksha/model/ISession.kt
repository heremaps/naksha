@file:Suppress("OPT_IN_USAGE")

package naksha.model

import naksha.model.objects.NakshaCollection
import naksha.model.objects.NakshaMap
import naksha.model.request.*
import kotlin.js.JsExport
import kotlin.js.JsName

/**
 * When a session is opened, it is bound to the context in which the session shall operate.
 *
 * A read session will acquire a connection from a connection pools whenever a read is performed, and release the connections instantly after the read is done.
 *
 * A write session will acquire a connection when the first write operation is executed, and stick with it until `commit`, `rollback` or [close] invoked. All reads after write will always utilize this single connection to ensure consistency. Before the first write operation, the optimizer is free to utilize multiple connections to read in parallel, but after the first write execution, a single connection must be used for all reading and writing, to guarantee consistency. Therefore, it is recommended to first perform all reads, then to perform the writes. The parallel reading can be disabled, if needed, using the [SessionOptions.parallel] switch.
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
     * Use the map to register, remove, or inspect [IMemberProcessor] instances for individual members.
     * Processors are invoked in the order in which they were added.
     * @return the member processor map.
     * @since 3.0
     */
    fun processors(): MemberProcessorMap

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
     * The read-only session will only be able to execute [ReadRequest]'s and throw an [NakshaError.UNSUPPORTED_OPERATION], when a [WriteRequest] is provided.
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
     * Returns the map for the given identifier.
     *
     * This method does only access the internal caching, and may not be up-to-date. If invoked on a [write session][IWriteSession] before committing changes, it will return maps that were created in the current session, but beware that these maps may eventually fail to commit.
     * @param mapId the map-id for which to return the latest HEAD state.
     * @return the map; _null_ if no such map exists.
     * @since 3.0
     */
    fun getMapById(mapId: String): NakshaMap?

    /**
     * Returns the map for the given number.
     *
     * This method does only access the internal caching, and may not be up-to-date. If invoked on a [write session][IWriteSession] before committing changes, it will return maps that were created in the current session, but beware that these maps may eventually fail to commit.
     * @param mapNumber the map-number for which to return the latest HEAD state.
     * @return the map; _null_ if no such map exists.
     * @since 3.0
     */
    fun getMapByNumber(mapNumber: Int): NakshaMap?

    /**
     * Returns the collection for the given identifier.
     *
     * This method does only access the internal caching, and may not be up-to-date. If invoked on a [write session][IWriteSession] before committing changes, it will return maps that were created in the current session, but beware that these collections may eventually fail to commit.
     * @param map the map to query.
     * @param collectionId the collection-id for which to return the latest HEAD state.
     * @return the collection; _null_ if no such collection exists.
     * @since 3.0
     */
    fun getCollectionById(map: NakshaMap, collectionId: String): NakshaCollection?

    /**
     * Returns the collection for the given number.
     *
     * This method does only access the internal caching, and may not be up-to-date. If invoked on a [write session][IWriteSession] before committing changes, it will return maps that were created in the current session, but beware that these collections may eventually fail to commit.
     * @param map the map to query.
     * @param collectionNumber the collection-number for which to return the latest HEAD state.
     * @return the collection; _null_ if no such collection exists.
     * @since 3.0
     */
    fun getCollectionByNumber(map: NakshaMap, collectionNumber: Int): NakshaCollection?

    /**
     * Load all tuples into the given [feature-tuples][FeatureTuple].
     *
     * [Tuple] that can't be fetched will still be `null` after the method returns. The method should query the [Naksha.cache] before actually loading the [Tuple] from the storage _(without asking the cache to load from storage, otherwise this would be a recursion)_ .
     *
     * @param featureTuples a list of result-tuples to fetch.
     * @since 3.0
     * @see [Naksha.cache]
     */
    @JsName("loadAllTuples")
    fun loadTuples(featureTuples: List<FeatureTuple?>)

    /**
     * Load all tuples into the given [feature-tuples][FeatureTuple].
     *
     * [Tuple] that can't be fetched will still be `null` after the method returns. The method should query the [Naksha.cache] before actually loading the [Tuple] from the storage _(without asking the cache to load from storage, otherwise this would be a recursion)_ .
     *
     * @param featureTuples a list of result-tuples to fetch.
     * @param from the index of the first result-tuples to fetch; default is `0`.
     * @param to the index of the first result-tuples to ignore; default is `featureTuples.size`.
     * @param mode the fetch mode; default is [FETCH_ALL].
     * @since 3.0
     * @see [Naksha.cache]
     */
    fun loadTuples(
        featureTuples: List<FeatureTuple?>,
        from: Int = 0,
        to: Int = featureTuples.size,
        mode: FetchMode = FETCH_ALL
    )
}
