package naksha.psql

import naksha.model.SessionOptions

object Plv8Cluster : PgCluster {
    override val master: PgInstance
        get() = TODO("Not yet implemented")

    override var replicas: MutableList<PgInstance>
        get() = TODO("Not yet implemented")
        set(value) {}

    override val connectionLimit: Int
        get() = TODO("Not yet implemented")

    override fun newConnection(options: SessionOptions, readOnly: Boolean): PgConnection {
        TODO("Not yet implemented")
    }
}