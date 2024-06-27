package naksha.plv8

import naksha.base.Platform
import naksha.plv8.PgSessionOptions
import kotlin.math.min

/**
 * A PostgresQL cluster that persists out of one master instance and optional read-replicas.
 * @property master the master instance.
 * @property replicas a mutable list of read-replicas, can be changed at runtime.
 */
class PsqlCluster(val master: PsqlInstance, var replicas: MutableList<PsqlInstance> = mutableListOf()) {
    /**
     * Open a PostgresQL session from the session pool of either [master], or a random [replica][replicas], dependent on the
     * [options] given.
     * @param options the session options.
     * @return the PostgresQL session.
     */
    fun openSession(options: PgSessionOptions): PsqlSession {
        if (!options.readOnly || options.useMaster || replicas.isEmpty()) return master.openSession(options)
        val i = min((Platform.random() * replicas.size).toInt(), replicas.size - 1)
        return replicas[i].openSession(options)
    }
}