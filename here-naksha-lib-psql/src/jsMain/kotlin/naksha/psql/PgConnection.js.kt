package naksha.psql

import naksha.base.unsupportedOp
import naksha.model.SessionOptions

@Suppress(names = ["DuplicatedCode"])
actual class PgConnection internal actual constructor(id: Long, instance: PgInstance, options: SessionOptions) : AutoCloseable {

    actual val id: Long = id
    actual val instance: PgInstance = instance
    actual val options: SessionOptions = options
    actual var autoCommit: Boolean
        get() = false
        set(value) {
            if (value) throw unsupportedOp("AutoCommit is not supported in PLv8")
        }

    actual fun toUri(showPassword: Boolean): String
        = if (showPassword) instance.url else instance.toString()

    actual fun execute(sql: String, args: Array<Any?>?): PgCursor {
        val result = if (args != null) plv8.execute(sql, args) else plv8.execute(sql)
        if (result is Number) return PgCursor(result.toInt())
        if (result is Array<*>) return PgCursor(result as Array<dynamic>)
        return PgCursor(arrayOf(result))
    }

    actual fun prepare(sql: String, typeNames: Array<String>?): PgPlan
        = PgPlan(if (typeNames != null) plv8.prepare(sql, typeNames) else plv8.prepare(sql))

    actual fun commit() {
    }

    actual fun rollback() {
    }

    private var closed: Boolean = false

    actual fun isClosed(): Boolean = closed

    actual override fun close() {
        this.closed = true
    }

    actual fun terminate() {
        this.closed = true
    }
}