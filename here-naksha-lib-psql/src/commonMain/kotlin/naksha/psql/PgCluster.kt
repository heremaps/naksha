package naksha.psql

import naksha.model.SessionOptions
import kotlin.js.JsExport

/**
 * A PostgresQL cluster, which is a set of PostgresQL servers of at least one **master** node and a variable amount of read-replicas.
 */
@Suppress("OPT_IN_USAGE")
@JsExport
interface PgCluster {
    /**
     * The **master** node.
     */
    val master: PgInstance

    /**
     * A list of read-replicas about which the read-only connections should be distributed. If a specific instance should be preferred, it can simply be added multiple times into the list.
     */
    var replicas: MutableList<PgInstance>

    /**
     * The maximum amount of connections this cluster can handle.
     *
     * For more details, each instance should be reviewed individually, this is an accumulated value.
     */
    val connectionLimit: Int

    /**
     * Get a new PostgresQL connection from the connection pool of either [master], or a random [replica][replicas], dependent on the [options] given. This method is normally called from [PgStorage].
     *
     * - Throws [naksha.model.NakshaError.TOO_MANY_CONNECTIONS], if no more connections are available.
     * @param options the session options.
     * @param readOnly if the connection should be read-only.
     * @return the PostgresQL connection.
     */
    fun newConnection(options: SessionOptions, readOnly: Boolean): PgConnection
}