@file:Suppress("OPT_IN_USAGE")

package naksha.model

import naksha.model.request.*
import kotlin.js.JsExport

/**
 * When a session is opened, it is bound to the context in which the session shall operate. The read session will acquire a connection from a connection pools when read is called, and release the connections instantly after the read is done. The write session will acquire a connection, when the first read or write operation is done, and stick with it until `commit`, `rollback` or [close] invoked. The dictionary manager will grab an idle read or read/write connection on demand, and release it to the connection pool as soon as possible.
 */
@JsExport
interface ISession : AutoCloseable {
    /**
     * The socket timeout in milliseconds.
     * @since 3.0.0
     */
    var socketTimeout: Int

    /**
     * The statement timeout in milliseconds.
     * @since 3.0.0
     */
    var stmtTimeout: Int

    /**
     * The lock timeout in milliseconds.
     * @since 3.0.0
     */
    var lockTimeout: Int

    /**
     * The map the session currently operates on.
     *
     * - If changing the map, may throw [NakshaError.UNSUPPORTED_OPERATION], if changing the map is not supported.
     * @since 3.0.0
     */
    var map: String

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
     * Execute the given [Request] in parallel, if supported, otherwise fallback to a normal [execute]. This differs from [SessionOptions.parallel] in that it no strong guarantee requirements, it is mainly for bulk loading or other siutation in which performance matter more than a 100% guarantee of safety.
     *
     * **Warning**: **There is a minor risk to create a broken state in the storage!** This depends on the exact implementation, but it needs to be an accepted risk, when using `executeParallel`.
     *
     * For example in `lib-psql`, even after all requests have been executed successfully, committing may fail partially, for example when only one connection aborts or the server crashes in the middle of the operation, while having committed already some connections, with others not yet to be done.
     *
     * Parallel executions on partitioned tables can be up to 256 times faster than sequential ones, depending on how many partitions there are, and on the available network bandwidth. This is, because all partitions are queried in parallel. The biggest PostgresQL database server available currently (at the time of writing, mid 2024) can be installed on an EC2 [r6idn.metal](https://aws.amazon.com/ec2/instance-types/r6i/) instance, providing 200 Gbps of networking, which would allow theoretically to satisfy 40 parallel connections or 20, when being in the same [placement group](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/placement-strategies.html#placement-groups-cluster), see [AWS ec2-instance-network-bandwidth](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/ec2-instance-network-bandwidth.html). So, with two `r6idn.metal` machines, one being the database, and the other being the client, in the same `placement group`, theoretically 20 concurrent connections will reach the limit of 200 Gbps. However, because the CPU load has a factor too, and the kernel configuration for TCP as well, it is recommended to use 64 partitions, so that each partition receives 3.125 Gbps of traffic only. The reason is, that this uses all CPU perfectly fine and works as well, when the clients are distributed on 64 EMR nodes. For all of this to be working eventually, especially when reading later, it is recommended to think always about the CPU limit, because reading requires nearly always to query all partitions in parallel to be efficient, this requires a lot of CPU resources on the server. Therefore, the best often is to find a middle ground between extreme write throughput, and query performance. In the above example, 8 to 16 partitions (and therefore 8 to 16 concurrent connections) would still be able to each between 40/80 and 80/160 Gbps of throughput, but make reading much more efficient. As the EBS volume is anyway limited to 100 Gbps, and for updates or deleted multiple writes are needed (move to history), and WAL logs are as well sharing this bandwidth, it is anyway unlikely to satisfy even 100 Gbps of the bandwidth for writing. This is not true for the temporary tablespace located on the ephemeral storage, where 200 Gbps can be fully satisfied. **In a nutshell, planning is essential!**
     * @since 3.0.0
     */
    fun executeParallel(request: Request): Response = execute(request)

    /**
     * Tests if the session is closed.
     * @return _true_ if the session is closed.
     * @since 3.0.0
     */
    fun isClosed(): Boolean

    /**
     * Closing a session will roll back the underlying connection, and then return it to their connection pool. After closing a session
     * any further methods invocation will raise an [IllegalStateException].
     * @since 2.0.7
     */
    override fun close()

    /**
     * Tests if the given handle is valid, and if it is, tries to extend its live-time to the given amount of milliseconds.
     *
     * Some handles may expire after some time. For example, when custom filters were applied, the generated result-set must be stored somewhere to guarantee that it is always the same (we can't store the filter code!), but we do not store this forever, so the handle does have an expiry. Some handles may not have an expiry, for example when the storage can reproduce them at any moment, using just the information from the handle.
     *
     * There is no guarantee that the life-time of the handle can be extended, especially when invoking this method on a read-only session.
     * @param handle the handle to test.
     * @param ttl if not _null_, the time-to-live of the handle should be extended by the given amount of milliseconds, if possible.
     * @return _true_ if the handle is valid, _false_ otherwise.
     * @since 3.0.0
     */
    fun validateHandle(handle: String, ttl: Int? = null): Boolean

    /**
     * Load the latest [tuples][Tuple] of the features with the given identifiers, from the given collection/map.
     *
     * The fetch modes are:
     * - [all][FETCH_ALL] (_**default**_) - all columns
     * - [all-no-cache][FETCH_ALL] - all columns, but do not access cache (but cache is updated)
     * - [id][FETCH_ID] - id and row-id, rest from cache, if available
     * - [meta][FETCH_META] - metadata and row-id, rest from cache, if available
     * - [cached-only][FETCH_CACHE] - only what is available in cache
     *
     * @param mapId the map from which to load.
     * @param collectionId the collection from to load.
     * @param featureIds a list of feature identifiers to load.
     * @param mode the fetch mode.
     * @return the list of the latest [tuples][Tuple], _null_, if no [tuple][Tuple] was not found.
     * @since 3.0.0
     */
    fun getLatestTuples(mapId: String, collectionId: String, featureIds: Array<String>, mode: FetchMode = FetchMode.FETCH_ALL): List<Tuple?>

    /**
     * Load specific [tuples][naksha.model.Tuple].
     *
     * The fetch modes are:
     * - [all][FETCH_ALL] (_**default**_) - all columns
     * - [all-no-cache][FETCH_ALL] - all columns, but do not access cache (but cache is updated)
     * - [id][FETCH_ID] - id and row-id, rest from cache, if available
     * - [meta][FETCH_META] - metadata and row-id, rest from cache, if available
     * - [cached-only][FETCH_CACHE] - only what is available in cache
     *
     * @param tupleNumbers a list of [tuple-numbers][TupleNumber] of the rows to load.
     * @param mode the fetch mode.
     * @return the list of the loaded [tuples][Tuple], _null_, if the tuple was not found.
     * @since 3.0.0
     */
    fun getTuples(tupleNumbers: Array<TupleNumber>, mode: FetchMode = FetchMode.FETCH_ALL): List<Tuple?>

    /**
     * Fetches a single result-tuple.
     *
     * The fetch modes are:
     * - [all][FETCH_ALL] (_**default**_) - all columns
     * - [all-no-cache][FETCH_ALL] - all columns, but do not access cache (but cache is updated)
     * - [id][FETCH_ID] - id and row-id, rest from cache, if available
     * - [meta][FETCH_META] - metadata and row-id, rest from cache, if available
     * - [cached-only][FETCH_CACHE] - only what is available in cache
     *
     * @param resultTuple the result-tuple into which to load the tuple.
     * @param mode the fetch mode.
     * @since 3.0.0
     */
    fun fetchTuple(resultTuple: ResultTuple, mode: FetchMode = FetchMode.FETCH_ALL)

    /**
     * Fetches all tuples in the given result-tuples.
     *
     * The fetch modes are:
     * - [all][FETCH_ALL] (_**default**_) - all columns
     * - [all-no-cache][FETCH_ALL] - all columns, but do not access cache (but cache is updated)
     * - [id][FETCH_ID] - id and row-id, rest from cache, if available
     * - [meta][FETCH_META] - metadata and row-id, rest from cache, if available
     * - [cached-only][FETCH_CACHE] - only what is available in cache
     *
     * @param resultTuples a list of result-tuples to fetch.
     * @param from the index of the first result-tuples to fetch.
     * @param to the index of the first result-tuples to ignore.
     * @param mode the fetch mode.
     * @since 3.0.0
     */
    fun fetchTuples(resultTuples: List<ResultTuple?>, from:Int = 0, to:Int = resultTuples.size, mode: FetchMode = FetchMode.FETCH_ALL)
}
