package com.here.naksha.lib.plv8.naksha.plv8

import naksha.base.Platform
import kotlin.math.min

/**
 * A PostgresQL cluster that persists out of one master instance and optional read-replicas.
 * @property master the master instance.
 * @property replicas a mutable list of read-replicas, can be changed at runtime.
 */
class PsqlCluster(val master: PsqlInstance, var replicas: MutableList<PsqlInstance> = mutableListOf()) {
    /**
     * Returns a connection from the connection pool of either [master] or a random [replica][replicas].
     * @param options the connect-options.
     * @param useMaster if a connection to the _master_ is needed, for example to avoid replication lag. Only taken into consideration
     * for read-only connections.
     * @return the connection.
     */
    fun getConnection(options: PsqlConnectOptions, useMaster: Boolean = false): PsqlConnection {
        if (!options.readOnly || useMaster || replicas.isEmpty()) return master.getConnection(options)
        val i = min((Platform.random() * replicas.size).toInt(), replicas.size - 1)
        return replicas[i].getConnection(options)
    }
}