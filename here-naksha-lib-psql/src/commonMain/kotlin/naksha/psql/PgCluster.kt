package naksha.psql

import naksha.base.Platform
import naksha.base.fn.Fx2
import naksha.model.SessionOptions
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.math.min

/**
 * A collection of [PostgresQL database servers][PgInstance].
 *
 * In the PostgresQL world each database server manages a _database cluster_. A _database cluster_ is a collection of databases that is managed by a single instance of a running database server.
 *
 * The `PgCluster` is a collection of PostgresQL database server with one [PgInstance] being the **master** node, and a variable amount of [PgInstance] used as read-replicas.
 * @since 3.0
 */
@JsExport
class PgCluster(
    /**
     * The **master** node.
     * @since 3.0
     */
    var master: PgInstance,

    /**
     * A list of read-replicas about which the read-only connections should be distributed. If a specific instance should be preferred, it can simply be added multiple times into the list.
     * @since 3.0
     */
    val replicas: MutableList<PgInstance> = mutableListOf(),
) {
    /**
     * Creates a new collection of [PostgresQL database servers][PgInstance] as defined within the given [PgConfig].
     * @param config the [PgConfig] to use to build the cluster.
     * @since 3.0
     */
    @JsName("newPgClusterByConfig")
    constructor(config: PgConfig) : this(PgInstance.of(config.master)) {
        for (replica in config.replicas) {
            if (replica != null) withReader(PgInstance.of(replica))
        }
    }

    /**
     * The maximum amount of connections this cluster can handle.
     *
     * For more details, each instance should be reviewed individually, this is an accumulated value.
     * @since 3.0
     */
    val connectionLimit: Int
        get() {
            var limit = master.connectionLimit
            for (instance in replicas) {
                limit += instance.connectionLimit
            }
            return limit
        }

    /**
     * Add a reader to the [replicas] list.
     * @param instance the instance to add.
     * @return this.
     */
    fun withReader(instance: PgInstance): PgCluster {
        if (!replicas.contains(instance)) replicas.add(instance)
        return this
    }

    /**
     * Get a new PostgresQL connection from the connection pool of either [master], or a random [replica][replicas], dependent on the [options] given. This method is normally called from [PgStorage].
     *
     * - Throws [naksha.base.NakshaError.TOO_MANY_CONNECTIONS], if no more connections are available.
     * @param options the session options.
     * @param readOnly if the connection should be read-only; only if being _true_, read-replica are contacted, except [SessionOptions.useMaster] is explicit set.
     * @param init an optional initialization function, if given, then it will be called with the string to be used to initialize the connection. It may just use this string, perform arbitrary additional work, or suppress initialization completely.
     * @return the PostgresQL connection.
     * @since 3.0
     */
    fun newConnection(options: SessionOptions, readOnly: Boolean, init: Fx2<PgConnection, String>?): PgConnection {
        if (!readOnly || options.useMaster || replicas.isEmpty()) {
            return master.openConnection(options, readOnly, init)
        }
        // Read-Only connection.
        val i = min((Platform.random() * replicas.size).toInt(), replicas.size - 1)
        val pgInstance = replicas[i]
        return pgInstance.openConnection(options, true, init)
    }
}