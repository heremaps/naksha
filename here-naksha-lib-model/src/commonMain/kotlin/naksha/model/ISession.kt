@file:Suppress("OPT_IN_USAGE")

package naksha.model

import naksha.jbon.IDictReader
import naksha.jbon.JbDictionary
import naksha.model.objects.NakshaCollection
import naksha.model.objects.NakshaMap
import naksha.model.request.*
import kotlin.js.JsExport

/**
 * When a session is opened, it is bound to the context in which the session shall operate. The read session will acquire a connection from a connection pools when read is called, and release the connections instantly after the read is done. The write session will acquire a connection, when the first read or write operation is done, and stick with it until `commit`, `rollback` or [close] invoked.
 */
@JsExport
interface ISession : IDictReader, AutoCloseable {
    /**
     * The storage to which the session is bound.
     * @since 3.0.0
     */
    val storage: IStorage

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
     * Execute the given [Request].
     *
     * The read-only session will only be able to execute [ReadRequest]'s and throw an [NakshaError.UNSUPPORTED_OPERATION], when a [WriteRequest] is provided.
     * @param request the request to execute.
     * @return the response.
     * @since 2.0.7
     */
    fun execute(request: Request): Response

    /**
     * Execute the given [Request] in parallel, if supported, otherwise fallback to a normal [execute]. This differs from [SessionOptions.parallel] in that it does not have such strong guarantee requirements, it is mainly for bulk loading or other situations, in which performance matters more than 100% safety.
     *
     * **Warning: There is a minor risk to create a broken state in the storage!**
     *
     * This depends on the exact implementation, but it needs to be an accepted risk, when using `executeParallel`.
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
     * Fetches all tuples in the given result-tuples.
     *
     * [Tuple] that can't be fetched will still be _null_ after the method returns.
     *
     * **The method is not thread safe!**
     *
     * @param featureTuples a list of result-tuples to fetch.
     * @param from the index of the first result-tuples to fetch; default is `0`.
     * @param to the index of the first result-tuples to ignore; default is `featureTuples.size`.
     * @param fetchFromHistory if the history should be queried; default is `false`.
     * @param mode the fetch mode; default is [FETCH_ALL].
     * @since 3.0.0
     */
    fun fetchTuples(featureTuples: List<FeatureTuple?>, from: Int = 0, to: Int = featureTuples.size, fetchFromHistory: Boolean = false, mode: FetchMode = FETCH_ALL)

    /**
     * Returns the map for the given identifier.
     *
     * This method does only access the internal caching, and may not be up-to-date. If invoked on a [write session][IWriteSession] before committing changes, it will return maps that were created in the current session, but beware that these maps may eventually fail to commit.
     * @param mapId the map-id for which to return the latest HEAD state.
     * @return the map; _null_ if no such map exists.
     * @since 3.0.0
     */
    fun getMapById(mapId: String): NakshaMap?

    /**
     * Returns the map for the given number.
     *
     * This method does only access the internal caching, and may not be up-to-date. If invoked on a [write session][IWriteSession] before committing changes, it will return maps that were created in the current session, but beware that these maps may eventually fail to commit.
     * @param mapNumber the map-number for which to return the latest HEAD state.
     * @return the map; _null_ if no such map exists.
     * @since 3.0.0
     */
    fun getMapByNumber(mapNumber: Int): NakshaMap?

    /**
     * Update the internal cache.
     *
     * Note, calling this method does not give a guarantee that everything is visible, because when the cache is refreshed while another client modifies the storage, there can be only microseconds between the read and write, which means, when the read returns, the information is already outdated.
     * @since 3.0.0
     */
    fun refreshMaps()

    /**
     * Returns the collection for the given identifier.
     *
     * This method does only access the internal caching, and may not be up-to-date. If invoked on a [write session][IWriteSession] before committing changes, it will return maps that were created in the current session, but beware that these collections may eventually fail to commit.
     * @param map the map to query.
     * @param collectionId the collection-id for which to return the latest HEAD state.
     * @return the collection; _null_ if no such collection exists.
     * @since 3.0.0
     */
    fun getCollectionById(map: NakshaMap, collectionId: String): NakshaCollection?

    /**
     * Returns the collection for the given number.
     *
     * This method does only access the internal caching, and may not be up-to-date. If invoked on a [write session][IWriteSession] before committing changes, it will return maps that were created in the current session, but beware that these collections may eventually fail to commit.
     * @param map the map to query.
     * @param collectionNumber the collection-number for which to return the latest HEAD state.
     * @return the collection; _null_ if no such collection exists.
     * @since 3.0.0
     */
    fun getCollectionByNumber(map: NakshaMap, collectionNumber: Int): NakshaCollection?

    /**
     * Update the internal cache.
     *
     * Note, calling this method does not give a guarantee that everything is visible, because when the cache is refreshed while another client modifies the storage, there can be only microseconds between the read and write, which means, when the read returns, the information is already outdated.
     * @since 3.0.0
     */
    fun refreshCollections(map: NakshaMap)

    /**
     * The best flags to encode the given feature.
     *
     * @param feature the feature to encode; _null_ if no specific one is available.
     * @param context the context in which the encoding happens (for example the [map][IMap] or [collection][ICollection]); _null_ if none is available.
     * @return best flags to use for encoding.
     * @since 3.0.0
     */
    fun getEncodingFlags(feature: Any?, context: Any? = null): Flags = storage.getEncodingFlags(feature, context)

    override fun getDictionary(id: String): JbDictionary? = storage.getDictionary(id)
    override fun getEncodingDictionary(feature: Any?, context: Any?): JbDictionary? = storage.getEncodingDictionary(feature, context)
}