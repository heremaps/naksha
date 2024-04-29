package com.here.naksha.lib.plv8

import com.here.naksha.lib.jbon.BigInt64
import com.here.naksha.lib.jbon.SQL_BYTE_ARRAY
import com.here.naksha.lib.jbon.SQL_INT16
import com.here.naksha.lib.jbon.SQL_INT32
import com.here.naksha.lib.jbon.SQL_INT64
import com.here.naksha.lib.jbon.SQL_STRING
import com.here.naksha.lib.nak.GZip
import java.io.Closeable
import java.sql.Connection

/**
 * Java JDBC binding to grant access to PostgresQL.
 */
@Suppress("MemberVisibilityCanBePrivate", "UNCHECKED_CAST")
class JvmPlv8Sql(var conn: Connection?) : IPlv8Sql, Closeable {
    private val dbInfo = PgDbInfo(this)

    override fun info(): PgDbInfo = dbInfo

    override fun newTable(): ITable = JvmPlv8Table()

    override fun affectedRows(any: Any): Int? = if (any is Int) any else null

    override fun rows(any: Any): Array<Any>? = if (any is Array<*>) any as Array<Any> else null

    override fun execute(sql: String, args: Array<Any?>?): Any {
        val conn = this.conn
        check(conn != null)
        if (args.isNullOrEmpty()) {
            val stmt = conn.createStatement()
            stmt.use {
                return if (stmt.execute(sql)) JvmPlv8ResultSet(stmt.resultSet).toArray() else stmt.updateCount
            }
        }
        val query = JvmPlv8SqlQuery(sql)
        val stmt = query.prepare(conn)
        stmt.use {
            if (!args.isNullOrEmpty()) query.bindArguments(stmt, args)
            return if (stmt.execute()) JvmPlv8ResultSet(stmt.resultSet).toArray() else stmt.updateCount
        }
    }

    override fun prepare(sql: String, typeNames: Array<String>?): IPlv8Plan {
        val conn = this.conn
        check(conn != null)
        return JvmPlv8Plan(JvmPlv8SqlQuery(sql), conn)
    }

    override fun close() {
        conn?.close()
        conn = null
    }

    override fun executeBatch(plan: IPlv8Plan, bulkParams: Array<Array<Param>>): IntArray {
        plan as JvmPlv8Plan
        for (singleQueryParams in bulkParams) {
            for (p in singleQueryParams) {
                when (p.type) {
                    SQL_BYTE_ARRAY -> plan.setBytes(p.idx, p.value as ByteArray?)
                    SQL_STRING -> plan.setString(p.idx, p.value as String?)
                    SQL_INT16 -> plan.setShort(p.idx, p.value as Short?)
                    SQL_INT32 -> plan.setInt(p.idx, p.value as Int?)
                    SQL_INT64 -> plan.setLong(p.idx, p.value as BigInt64?)
                }
            }
            plan.addBatch()
        }

        return plan.executeBatch()
    }

    override fun gzipCompress(raw: ByteArray): ByteArray = GZip.gzip(raw)

    override fun gzipDecompress(raw: ByteArray): ByteArray = GZip.gunzip(raw)
}