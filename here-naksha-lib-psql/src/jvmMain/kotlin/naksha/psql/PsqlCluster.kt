package naksha.psql

import naksha.base.Platform
import naksha.model.NakshaError
import naksha.model.NakshaError.NakshaErrorCompanion.EXCEPTION
import naksha.model.NakshaException
import naksha.model.SessionOptions
import kotlin.math.min

/**
 * A PostgresQL cluster that persists out of one master instance and optional read-replicas.
 * @property master the master instance.
 * @property replicas a mutable list of read-replicas, can be changed at runtime.
 */
class PsqlCluster(override val master: PgInstance, override var replicas: MutableList<PgInstance> = mutableListOf()) : PgCluster {
    override val connectionLimit: Int
        get() {
            var limit = master.connectionLimit
            for (instance in replicas) {
                limit += instance.connectionLimit
            }
            return limit
        }

    override fun newConnection(options: SessionOptions, readOnly: Boolean): PsqlConnection {
        if (!readOnly || options.useMaster || replicas.isEmpty()) {
            val master = this.master
            if (master !is PsqlInstance) throw NakshaException(EXCEPTION, "This implementation requires PsqlInstance's")
            return master.openConnection(options, readOnly)
        }
        // Read-Only connection.
        val i = min((Platform.random() * replicas.size).toInt(), replicas.size - 1)
        val pgInstance = replicas[i]
        check(pgInstance is PsqlInstance) { "This implementation requires PsqlInstance's"}
        return pgInstance.openConnection(options, true)
    }
}