package naksha.psql

import naksha.base.AnyObject
import naksha.base.Platform
import naksha.base.illegalState
import java.sql.ResultSet
import java.sql.Statement
import kotlin.reflect.KClass

/**
 * Internal helper class to handle results as if they were returned by PLV8 engine.
 * @property stmt the statement to which this cursor is bound.
 * @property closeStmt if the statement should be closed, when the cursor is closed.
 */
actual class PgCursor private actual constructor(): AutoCloseable {

    internal constructor(stmt: Statement, closeStmt: Boolean): this() {
        this.stmt = stmt
        this.closeStmt = closeStmt
        // Fetch all result sets, accumulate the affected rows.
        var resultSets = emptyArray<ResultSet>()
        val stmt = this.stmt
        do {
            val uc = stmt.updateCount
            if (uc > 0) affectedRows += uc
            val rs = stmt.resultSet
            if (rs != null) {
                val copy = resultSets.copyOf(resultSets.size + 1)
                copy[resultSets.size] = rs
                @Suppress("UNCHECKED_CAST")
                resultSets = copy as Array<ResultSet>
            }
        } while (stmt.getMoreResults(Statement.KEEP_CURRENT_RESULT))
        this.resultSets = resultSets
    }

    private lateinit var stmt: Statement
    private var closeStmt: Boolean = false

    private var row = 0
    private var affectedRows = 0
    private lateinit var resultSets: Array<ResultSet>
    private var columnCount = 0
    private var columnIndices: MutableMap<String, Int>? = null
    private var columnNames: Array<String>? = null
    private var columnTypes: Array<String>? = null
    private var isRow = false
    private var rsNext = 0
    private var rs: ResultSet? = null

    private fun updateColumns() {
        val rs = this.rs
        if (rs != null) {
            val columnCount = rs.metaData.columnCount
            val columnIndices = HashMap<String, Int>()
            // We hack this, because we do not want to run the loop twice!
            @Suppress("UNCHECKED_CAST")
            val columnNames = arrayOfNulls<String>(columnCount) as Array<String>

            @Suppress("UNCHECKED_CAST")
            val columnTypes = arrayOfNulls<String>(columnCount) as Array<String>
            val metaData = rs.metaData
            var i = 0
            while (i < columnCount) {
                val columnIndex = i + 1
                val columnName = metaData.getColumnLabel(columnIndex)
                columnNames[i] = columnName
                columnTypes[i] = metaData.getColumnTypeName(columnIndex)
                columnIndices[columnName] = i
                i++
            }
            this.columnCount = 0
            this.columnNames = columnNames
            this.columnTypes = columnTypes
            this.columnIndices = columnIndices
        } else {
            this.columnCount = 0
            this.columnNames = null
            this.columnTypes = null
            this.columnIndices = null
        }
    }

    private fun rs(): ResultSet {
        val rs = this.rs
        check(rs != null) { "The cursor is not positioned at a row" }
        return rs
    }

    /**
     * Returns the result-set and verifies that it is positioned above a row.
     * @return the result-set, positioned above a row.
     * @throws naksha.base.NakshaException with error [ILLEGAL_STATE][naksha.base.NakshaError.ILLEGAL_STATE] if this cursor is no result-set or no row is selected currently.
     * @see isRow
     */
    private fun rsAtRow(): ResultSet {
        val rs = rs()
        if(!isRow) throw illegalState("The cursor is not positioned at a row")
        return rs
    }

    actual fun columnNames(): Array<String> =
        columnNames ?: throw illegalState("Initialization error: Missing column names array")

    /**
     * The types of the columns, indexed from 0, while in a SQL statement the index starts with 1.
     */
    private fun columnTypes(): Array<String> =
        columnTypes ?: throw illegalState("Initialization error: Missing column type array")

    /**
     * A map between the name of a column and the index in [columnNames] and [columnTypes].
     */
    private fun columnIndices(): MutableMap<String, Int> =
        columnIndices ?: throw illegalState("Initialization error: Missing column indices map")

    actual fun affectedRows(): Int = affectedRows

    actual tailrec fun next(): Boolean {
        if (rs?.next() == true) {
            isRow = true
            return true
        }
        if (rsNext >= resultSets.size) {
            isRow = false
            return false
        }
        rs = resultSets[rsNext++]
        updateColumns()
        return next()
    }

    actual fun fetch(): PgCursor {
        next()
        return this
    }

    actual fun isRow(): Boolean = isRow

    actual fun rowNumber(): Int = rs?.row ?: -1

    actual operator fun contains(name: String): Boolean {
        rsAtRow()
        return columnIndices()[name] != null
    }

    actual operator fun contains(column: PgColumn): Boolean = contains(column.name)

    @Suppress("NOTHING_TO_INLINE")
    private inline fun valueOrNull(rs: ResultSet, value: Any?): Any?
            = if (rs.wasNull()) null else value

    private fun readArray(rs: ResultSet, pgArray: java.sql.Array): Array<Any?>? {
        if (rs.wasNull()) return null
        val nativeArray = pgArray.array
        if (nativeArray !is Array<*>) return null
        val result = arrayOfNulls<Any?>(nativeArray.size)
        for (i in nativeArray.indices) {
            when (val raw = nativeArray[i]) {
                null -> result[i] = null
                is Byte -> result[i] = raw.toInt()
                is Short -> result[i] = raw.toInt()
                is Int -> result[i] = raw
                is Long -> result[i] = raw
                is Float -> result[i] = raw.toDouble()
                is Double -> result[i] = raw
                is String -> result[i] = raw
                is Char, is CharSequence -> result[i] = raw.toString()
                is ByteArray -> result[i] = raw
                else -> result[i] = nativeArray[i]
            }
        }
        return result
    }

    /**
     * Returns the value as returned by the database.
     * @param index the SQL index, starting from 1.
     * @param type the type to read.
     * @param rs the result from which to read.
     * @return the value.
     */
    private fun columnValue(index: Int, type: String, rs: ResultSet): Any? {
        if (type=="array" || type.startsWith('_')) { // e.g. "_int8"
            val sqlArray = rs.getArray(index) ?: return null
            return readArray(rs, sqlArray)
        }
        return when (type) {
            // TODO: We need to check which values can simply read using getObject, so we can avoid the null-casting.
            //       However, we want to return Integer for short and Long for long, so some hacks are needed, just which?
            "null" -> null
            "text", "varchar", "character", "char", "json", "uuid", "inet", "cidr", "macaddr", "xml", "internal",
            "point", "line", "lseg", "box", "path", "polygon", "circle", "int4range", "int8range", "numrange",
            "tsrange", "tstzrange", "daterange" -> valueOrNull(rs, rs.getString(index))
            "smallint", "int2" -> valueOrNull(rs, rs.getShort(index).toInt())
            "integer", "int", "int4", "xid4", "oid" -> valueOrNull(rs, rs.getInt(index))
            "bigint", "int8", "xid8" -> valueOrNull(rs, rs.getLong(index))
            "real" -> valueOrNull(rs, rs.getFloat(index).toDouble())
            "double precision" -> valueOrNull(rs, rs.getDouble(index))
            "numeric" -> valueOrNull(rs, rs.getBigDecimal(index))
            "bool", "boolean" -> valueOrNull(rs, rs.getBoolean(index))
            "timestamp" -> valueOrNull(rs, rs.getTimestamp(index).toInstant().toEpochMilli())
            "date" -> valueOrNull(rs, rs.getDate(index).toInstant().toEpochMilli())
            "bytea" -> valueOrNull(rs, rs.getBytes(index))
            "jsonb" -> rs.getString(index)?.let { Platform.fromJSON(it) }
            else -> rs.getObject(index)
        }
    }

    actual fun column(name: String): Any? {
        val rs = rsAtRow()
        val i = columnIndices()[name] ?: return null
        val type = columnTypes()[i]
        return columnValue(i + 1, type, rs)
    }

    actual fun column(column: PgColumn): Any? = column(column.name)

    @Suppress("UNCHECKED_CAST")
    actual fun <T : Any> columnOr(name: String, alternative: T): T {
        val v = column(name)
        val klass = Platform.klassOf(alternative)
        return if (klass.isInstance(v)) v as T else alternative
    }

    @Suppress("UNCHECKED_CAST")
    actual operator fun <T : Any> get(name: String): T = column(name) as T

    actual operator fun <T : Any> get(column: PgColumn): T = get(column.name)

    actual fun <T : AnyObject> map(klass: KClass<T>): T {
        val rs = rsAtRow()
        val columnNames = columnNames()
        val columnTypes = columnTypes()
        val row = Platform.newInstanceOf(klass)
        var i = 0
        while (i < columnNames.size) {
            val name = columnNames[i]
            val type = columnTypes[i]
            // See: https://www.postgresql.org/message-id/AANLkTinsk4rwT7v-751bwQkgTN1rkA=8uE-jk69nape-@mail.gmail.com
            val value = columnValue(++i, type, rs)
            row[name] = value
        }
        return row
    }

    actual override fun close() {
        try {
            if (closeStmt) stmt.close()
            else for (rs in resultSets) rs.close()
        } catch (_: Throwable) {}
        resultSets = emptyArray()
        rs = null
        updateColumns()
        isRow = false
    }
}
