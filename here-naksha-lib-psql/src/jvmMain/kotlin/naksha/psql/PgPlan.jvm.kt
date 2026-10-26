package naksha.psql

import java.sql.Connection
import java.sql.PreparedStatement
import kotlin.math.max
import kotlin.math.min

actual class PgPlan private actual constructor(): AutoCloseable {

    internal constructor(query: PsqlQuery, conn: Connection): this() {
        this.query = query
        this.conn = conn
    }

    internal lateinit var query: PsqlQuery
    internal lateinit var conn: Connection

    val stmt: PreparedStatement = query.prepare(conn)
    var closed: Boolean = false

    actual fun setFetchSize(size: Int) {
        check(!closed)
        stmt.fetchSize = if (size <= 0) 0 else min(1_000_000, max(1_000, size))
    }

    actual fun execute(args: Array<Any?>?): PgCursor {
        try {
            check(!closed)
            if (!args.isNullOrEmpty()) query.bindArguments(stmt, args)
            stmt.execute()
            return PgCursor(stmt, false)
        } catch (throwable: Throwable) {
            throw PgExceptionMapper.map(throwable)
        }
    }

    actual fun addBatch(args: Array<Any?>?) {
        check(!closed)
        if (!args.isNullOrEmpty()) query.bindArguments(stmt, args)
        stmt.addBatch()
    }

    actual fun executeBatch(): IntArray = stmt.executeBatch()

    actual override fun close() {
        try {
            val closed = this.closed
            this.closed = true
            if (!closed) stmt.close()
        } catch (throwable: Throwable) {
            PgExceptionMapper.map(throwable).error.print()
        }
    }
}