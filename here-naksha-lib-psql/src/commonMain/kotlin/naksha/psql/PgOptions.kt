package naksha.psql

import kotlin.js.JsExport

/**
 * Options when acquiring PostgresQL database connections.
 *
 * Beware that parallel write executions do have a low risk of leaving the database in a broken state (repairable, but with higher effort). However, parallel executions on partitioned tables can be up to 256 times faster than sequential queries, depending on how many partitions there are and on the available network bandwidth. This is, because all partitions are queried in parallel. The biggest PostgresQL database server possible currently (at the time of writing, mid 2024) can be installed on an EC2 [r6idn.metal](https://aws.amazon.com/ec2/instance-types/r6i/) instance, providing 200 Gbps of networking, which would allow theoretically to satisfy 40 parallel connections or 20, when being in the same [placement group](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/placement-strategies.html#placement-groups-cluster), see [AWS ec2-instance-network-bandwidth](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/ec2-instance-network-bandwidth.html). So, with two `r6idn.metal` machines, one being the database, and the other being the client, in the same `placement group`, theoretically 20 concurrent connections will reach the limit of 200 Gbps. However, because the CPU load has a factor too, and the kernel configuration for TCP as well, it is recommended to use 64 partitions, so that each partition receives 3.125 Gbps of traffic only. The reason is, that this uses all CPU perfectly fine and works as well, when the clients are distributed on 64 EMR nodes. For all of this to be working eventually, especially when reading later, it is recommended to think always about the CPU limit, because reading requires nearly always to query all partitions in parallel to be efficient, this requires a lot of CPU resources on the server. Therefore, the best often is to find a middle ground between extreme write throughput, and query performance. In the above example, 8 to 16 partitions (and therefore 8 to 16 concurrent connections) would still be able to each between 40/80 and 80/160 Gbps of throughput, but make reading much more efficient. As the EBS volume is anyway limited to 100 Gbps, and for updates or deleted multiple writes are needed (move to history), and WAL logs are as well sharing this bandwidth, it is anyway unlikely to satisfy even 100 Gbps of the bandwidth for writing. This is not true for the temporary tablespace located on the ephemeral storage, where 200 Gbps can be fully satisfied. **In a nutshell, planning is essential!**
 * @property appName the application name to be registered against the PostgresQL database, appears in the
 * [pg_stat_activity](https://www.postgresql.org/docs/current/monitoring-stats.html#MONITORING-PG-STAT-ACTIVITY-VIEW) table as `name`.
 * @property schema the schema to use.
 * @property appId the application identifier of the change, stored in the [naksha.model.Metadata.appId].
 * @property author the author of the change, stored in the [naksha.model.Metadata.author]. Special rules apply for author handling.
 * @property readOnly if the connection should be read-only.
 * @property connectTimeout the time in milliseconds to wait for the TCP handshake.
 * @property socketTimeout the time in milliseconds to wait for the TCP socket when reading or writing from it.
 * @property stmtTimeout the statement-timeout in milliseconds.
 * @property lockTimeout the lock-timeout in milliseconds.
 * @property useMaster if connections should be established against the master node; only relevant for [readOnly] mode to avoid
 * replication lag.
 * @property parallel if _true_, then parallel write queries should be executed.
 */
@Suppress("OPT_IN_USAGE")
@JsExport
data class PgOptions(
    val appName: String,
    val schema: String,
    var appId: String,
    var author: String? = null,
    val readOnly: Boolean = false,
    val connectTimeout: Int = 60_000,
    val socketTimeout: Int = 60_000,
    val stmtTimeout: Int = 60_000,
    val lockTimeout: Int = 10_000,
    val useMaster: Boolean = !readOnly,
    val parallel: Boolean = false
)