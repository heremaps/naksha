package naksha.psql

import naksha.base.Base
import naksha.base.fn.Fx2
import naksha.base.NakshaError.NakshaErrorCompanion.EXCEPTION
import naksha.base.NakshaException
import naksha.model.SessionOptions
import kotlin.math.min

/**
 * A PostgresQL cluster that persists out of one master instance and optional read-replicas.
 * @property master the master instance.
 */
class PsqlCluster @JvmOverloads constructor(override val master: PgInstance, replicas: MutableList<PgInstance>? = null) : PgCluster {
    /**
     * The replicas a mutable list of read-replicas, can be changed at runtime.
     * @see [addReader]
     */
    override var replicas: MutableList<PgInstance> = replicas ?: mutableListOf()
    override val connectionLimit: Int
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
    fun addReader(instance: PgInstance) : PsqlCluster {
        if (!replicas.contains(instance)) replicas.add(instance)
        return this
    }

    override fun newConnection(options: SessionOptions, readOnly: Boolean, init: Fx2<PgConnection, String>?): PsqlConnection {
        if (!readOnly || options.useMaster || replicas.isEmpty()) {
            val master = this.master
            if (master !is PsqlInstance) throw NakshaException(EXCEPTION, "This implementation requires PsqlInstance's")
            return master.openConnection(options, readOnly, init)
        }
        // Read-Only connection.
        val i = min((Base.random() * replicas.size).toInt(), replicas.size - 1)
        val pgInstance = replicas[i]
        check(pgInstance is PsqlInstance) { "This implementation requires PsqlInstance's"}
        return pgInstance.openConnection(options, true, init)
    }
}
