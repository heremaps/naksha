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
     * A list of read-replicas about which the read-only connections should be distributed. If a specific instance should be preferred,
     * it can simply be added multiple times into the list.
     */
    var replicas: MutableList<PgInstance>

    /**
     * Open a PostgresQL session from the session pool of either [master], or a random [replica][replicas], dependent on the [options]
     * given. This method is normally called from [PgStorage].
     * @param options the session options.
     * @param readOnly if the connection should be read-only.
     * @return the PostgresQL session.
     */
    fun newConnection(options: SessionOptions, readOnly: Boolean): PgConnection
}