package naksha.psql

import naksha.model.SessionOptions

object Plv8Instance : PgInstance {
    override val host: String
        get() = TODO("Not yet implemented")

    override val port: Int
        get() = TODO("Not yet implemented")

    override val database: String
        get() = TODO("Not yet implemented")

    override val user: String
        get() = TODO("Not yet implemented")

    override val password: String
        get() = TODO("Not yet implemented")

    override val readOnly: Boolean
        get() = TODO("Not yet implemented")

    override val url: String
        get() = TODO("Not yet implemented")

    override var connectionLimit: Int
        get() = TODO("Not yet implemented")
        set(value) {}

    override fun openConnection(options: SessionOptions, readOnly: Boolean): PgConnection {
        TODO("Not yet implemented")
    }
}