package naksha.psql

import naksha.model.SessionOptions

/**
 * A thin wrapper around [Plv8] to make it API compatible.
 */
class Plv8Connection : PgConnection {
    override var options: SessionOptions
        get() = TODO("Not yet implemented")
        set(value) {}

    override var autoCommit: Boolean
        get() = TODO("Not yet implemented")
        set(value) {}

    override fun toUri(showPassword: Boolean): String {
        TODO("Not yet implemented")
    }

    override fun execute(sql: String, args: Array<Any?>?): Plv8Cursor {
        TODO("Not yet implemented")
    }

    override fun prepare(sql: String, typeNames: Array<String>?): Plv8Plan {
        TODO("Not yet implemented")
    }

    override fun commit() {
        TODO("Not yet implemented")
    }

    override fun rollback() {
        TODO("Not yet implemented")
    }

    override fun isClosed(): Boolean {
        TODO("Not yet implemented")
    }

    override fun close() {
        TODO("Not yet implemented")
    }

    override fun terminate() {
        TODO("Not yet implemented")
    }
}