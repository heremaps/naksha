package naksha.psql

import naksha.base.ObjectProxy
import naksha.base.Platform
import naksha.base.Platform.PlatformCompanion.longToInt64
import java.sql.ResultSet
import java.sql.Statement
import kotlin.reflect.KClass

/**
 * Internal helper class to handle results as if they were returned by PLV8 engine.
 * @property stmt the statement to which this cursor is bound.
 * @property closeStmt if the statement should be closed, when the cursor is closed.
 */
class PsqlCursor internal constructor(private val stmt: Statement, private val closeStmt: Boolean) : PgCursor, AutoCloseable {
    private var row = 0
    private var affectedRows = 0
    private var resultSets: Array<ResultSet>
    private var columnCount = 0
    private var columnIndices: MutableMap<String, Int>? = null
    private var columnNames: Array<String>? = null
    private var columnTypes: Array<String>? = null
    private var isRow = false
    private var rsNext = 0
    private var rs: ResultSet? = null

    init {
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
     * @throws IllegalStateException if this cursor is not result-set or
     */
    private fun rsAtRow(): ResultSet {
        val rs = rs()
        check(isRow) { "The cursor is not positioned at a row" }
        return rs
    }

    /**
     * The names of the columns, indexed from 0, while in a SQL statement the index starts with 1.
     */
    private fun columnNames(): Array<String> =
        columnNames ?: throw IllegalStateException("Initialization error: Missing column names array")

    /**
     * The types of the columns, indexed from 0, while in a SQL statement the index starts with 1.
     */
    private fun columnTypes(): Array<String> =
        columnTypes ?: throw IllegalStateException("Initialization error: Missing column type array")

    /**
     * A map between the name of a column and the index in [columnNames] and [columnTypes].
     */
    private fun columnIndices(): MutableMap<String, Int> =
        columnIndices ?: throw IllegalStateException("Initialization error: Missing column indices map")

    /**
     * Returns the number of affected row when this is the cursor of an update query.
     * @return the number of affected row; -1 if the query was a SELECT or UPDATE with RETURNS.
     */
    override fun affectedRows(): Int = affectedRows

    /**
     * Move the cursor to the next row.
     * @return _true_ if the cursor is positioned at a row; _false_ is the cursor is behind the last row.
     */
    override tailrec fun next(): Boolean {
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

    /**
     * Move the cursor to the next row.
     * @return this.
     */
    override fun fetch(): PgCursor {
        next()
        return this
    }

    /**
     * Tests if the cursor is currently positioned at a valid row.
     * @return _true_ if the cursor is positioned at a row; _false_ is the cursor is behind the last row or before the first.
     */
    override fun isRow(): Boolean = isRow

    /**
     * Returns the current row number. The first row is 1, the row before the first row is 0.
     * @return the current row number (0 to n, where 0 is before the first row).
     */
    override fun rowNumber(): Int = rs?.row ?: -1

    /**
     * Tests if the current row has the given colum.
     * @param name the name of the column
     * @return _true_ if the current row contains a column with the given name.
     * @throws IllegalStateException if the cursor is not positioned above a valid row, [isRow] returns _false_.
     */
    override fun contains(name: String): Boolean {
        rsAtRow()
        return columnIndices()[name] != null
    }

    /**
     * Returns the value as returned by the database.
     * @param index the SQL index, starting from 1.
     * @param type the type to read.
     * @param rs the result from which to read.
     * @return the value.
     */
    private fun columnValue(index: Int, type: String, rs: ResultSet): Any? = when (type) {
        "null" -> null
        "text", "varchar", "character", "char", "json", "uuid", "inet", "cidr", "macaddr", "xml", "internal",
        "point", "line", "lseg", "box", "path", "polygon", "circle", "int4range", "int8range", "numrange",
        "tsrange", "tstzrange", "daterange" -> rs.getString(index)

        "smallint", "int2" -> rs.getShort(index).toInt()
        "integer", "int4", "xid4", "oid" -> rs.getInt(index)
        "bigint", "int8", "xid8" -> longToInt64(rs.getLong(index))
        "real" -> rs.getFloat(index).toDouble()
        "double precision" -> rs.getDouble(index)
        "numeric" -> rs.getBigDecimal(index)
        "boolean" -> rs.getBoolean(index)
        "timestamp" -> longToInt64(rs.getTimestamp(index).toInstant().toEpochMilli())
        "date" -> longToInt64(rs.getDate(index).toInstant().toEpochMilli())
        "bytea" -> rs.getBytes(index)
        "jsonb" -> Platform.fromJSON(rs.getString(index))
        "array" -> rs.getArray(index)
        else -> rs.getObject(index)
    }

    /**
     * Returns the column value of the current row.
     * @param name the name of the column
     * @return the value of the column; _null_ if the value is _null_ or no such column exists.
     * @throws IllegalStateException if the cursor is not positioned above a valid row, [isRow] returns _false_.
     */
    override fun column(name: String): Any? {
        val rs = rsAtRow()
        val i = columnIndices()[name] ?: return null
        val type = columnTypes()[i]
        return columnValue(i + 1, type, rs)
    }

    /**
     * Returns the column value of the current row.
     * @param name the name of the column
     * @return the value of the column.
     * @throws IllegalStateException if the cursor is not positioned above a valid row, [isRow] returns _false_.
     * @throws ClassCastException if casting the value failed.
     * @throws NullPointerException if the value is null.
     */
    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> get(name: String): T = column(name) as T

    /**
     * Reads the current row into a proxy object.
     * @param klass the type of the proxy object to create.
     * @return the created proxy object.
     */
    override fun <T : ObjectProxy> map(klass: KClass<T>): T {
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

    override fun close() {
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
