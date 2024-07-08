package naksha.psql

import naksha.base.Platform
import kotlin.math.min

/**
 * A PostgresQL cluster that persists out of one master instance and optional read-replicas.
 * @property master the master instance.
 * @property replicas a mutable list of read-replicas, can be changed at runtime.
 */
class PsqlCluster(override val master: PgInstance, override var replicas: MutableList<PgInstance> = mutableListOf()) : PgCluster {
    /**
     * Open a PostgresQL connection from the connection pool of either [master], or a random [replica][replicas], dependent on the
     * [options] given.
     * @param options the connection options.
     * @return the PostgresQL connection.
     */
    override fun newConnection(options: PgOptions): PsqlConnection {
        if (!options.readOnly || options.useMaster || replicas.isEmpty()) {
            val master = this.master
            check(master is PsqlInstance) { "This implementation requires PsqlInstance's"}
            return master.openConnection(options)
        }
        val i = min((Platform.random() * replicas.size).toInt(), replicas.size - 1)
        val pgInstance = replicas[i]
        check(pgInstance is PsqlInstance) { "This implementation requires PsqlInstance's"}
        return pgInstance.openConnection(options)
    }
}