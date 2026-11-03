package naksha.psql

import naksha.base.illegalState
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

  internal actual fun tryMaintenance(action: () -> Boolean): Boolean {
    if (autoCommit) throw illegalState("Maintenance requires a caller-owned transaction")
    var completed = false
    var skipped = false
    var original: Throwable? = null
    try {
      plv8.subtransaction {
        try {
          skipped = !action()
        } catch (failure: Throwable) {
          // PLV8 strips native SPI fields when an error crosses the subtransaction boundary.
          if (failure.asDynamic().sqlerrcode == "55P03") skipped = true else original = failure
        }
        if (skipped || original != null) throw MaintenanceSkipped()
        completed = true
      }
    } catch (failure: Throwable) {
      if (skipped) return false
       throw original ?: failure
    }
    return completed
  }

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

private class MaintenanceSkipped : RuntimeException()
