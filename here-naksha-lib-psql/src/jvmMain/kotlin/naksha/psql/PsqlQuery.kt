package naksha.psql

import naksha.base.PAnyArray
import naksha.base.Int64
import naksha.base.Base
import naksha.base.illegalArg
import naksha.psql.PgType.Companion.BOOLEAN
import naksha.psql.PgType.Companion.BOOLEAN_ARRAY
import naksha.psql.PgType.Companion.BYTE_ARRAY
import naksha.psql.PgType.Companion.BYTE_ARRAY_ARRAY
import naksha.psql.PgType.Companion.DOUBLE
import naksha.psql.PgType.Companion.DOUBLE_ARRAY
import naksha.psql.PgType.Companion.FLOAT
import naksha.psql.PgType.Companion.FLOAT_ARRAY
import naksha.psql.PgType.Companion.INT
import naksha.psql.PgType.Companion.INT64
import naksha.psql.PgType.Companion.INT64_ARRAY
import naksha.psql.PgType.Companion.INT_ARRAY
import naksha.psql.PgType.Companion.JSONB
import naksha.psql.PgType.Companion.JSONB_ARRAY
import naksha.psql.PgType.Companion.SHORT
import naksha.psql.PgType.Companion.SHORT_ARRAY
import naksha.psql.PgType.Companion.STRING
import naksha.psql.PgType.Companion.STRING_ARRAY
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet.*

/**
 * A helper to parse SQL queries and find the dollar-placeholders, replacing them with `?`, escape question-marks, and finally provide a
 * way to bind the arguments.
 */
class PsqlQuery(query: String, private val typeNames: Array<String>?) {

    /**
     * We map "$1" to a list of positions (`1..n`) in the prepared statement. For example
     * `SELECT * FROM table WHERE a = $1 OR b = $1`. In that case, we replace it with
     * `SELECT * FROM table WHERE a = ? OR b = ?` and the map holds one entry 1 and a
     * list of two elements 1 and 2.
     */
    val dollarToIndices: HashMap<Int, ArrayList<Int>> = HashMap()
    val sql: String

    init {
        val sb = StringBuilder()
        var targetIndex = 1
        var charIndex = 0
        var inDoubleQuote = false
        var inSingleQuote = false
        while (charIndex < query.length) {
            val c = query[charIndex]
            when {
                c == '"' && !inSingleQuote -> {
                    inDoubleQuote = !inDoubleQuote
                    sb.append(c)
                    charIndex++
                }
                c == '\'' && !inDoubleQuote -> {
                    inSingleQuote = !inSingleQuote
                    sb.append(c)
                    charIndex++
                }
                c == '$' && !inDoubleQuote && !inSingleQuote
                        && charIndex < query.length - 1
                        && query[charIndex + 1] in positiveNums -> {
                    charIndex++
                    var gatheredNum = "${query[charIndex++]}"
                    while (charIndex < query.length && query[charIndex] in nums) {
                        gatheredNum += query[charIndex++]
                    }
                    sb.append("?")
                    val dollar = gatheredNum.toInt()
                    dollarToIndices
                        .computeIfAbsent(dollar) { ArrayList() }
                        .add(targetIndex++)
                }
                else -> {
                    sb.append(c)
                    charIndex++
                }
            }
        }
        sql = sb.toString()
        if (dollarToIndices.size >= SUSPICIOUSLY_MANY_ARGS) {
            Base.logger.warn("Potentially inefficient query detected (${dollarToIndices.size} arguments): $sql")
        }
    }

    private fun setArgument(stmt: PreparedStatement, arg: Any?, indices: ArrayList<Int>) {
        var i = 0
        while (i < indices.size) {
            // Note: `index` starts with 1, NOT 0 !!!
            val index = indices[i++]
            when (arg) {
                is Boolean -> stmt.setBoolean(index, arg)
                is Short -> stmt.setShort(index, arg)
                is Int -> stmt.setInt(index, arg)
                is Long -> stmt.setLong(index, arg)
                is Int64 -> stmt.setLong(index, arg.toLong())
                is Float -> stmt.setFloat(index, arg)
                is Double -> stmt.setDouble(index, arg)
                is String -> stmt.setString(index, arg)
                is ByteArray -> stmt.setBytes(index, arg)
                is Array<*> -> {
                    // Note: Java array indices start at 0, NOT 1 !!!
                    val typeNameIndex = index - 1
                    val typeName = if (typeNames != null && typeNameIndex < typeNames.size) typeNames[typeNameIndex] else null
                    var type = PgType.of(typeName)
                    if (type == null) {
                        if (arg.size == 0) throw illegalArg("Can't detect type of empty array, declared type: $typeName")
                        var j = 0
                        while (type == null && j < arg.size) {
                            val testValue = arg[j++]
                            type = when (testValue) {
                                null -> null
                                is Boolean -> BOOLEAN_ARRAY
                                is Short -> SHORT_ARRAY
                                is Int -> INT_ARRAY
                                is Int64, is Long -> INT64_ARRAY
                                is Float -> FLOAT_ARRAY
                                is Double -> DOUBLE_ARRAY
                                is String -> STRING_ARRAY
                                is ByteArray -> BYTE_ARRAY_ARRAY
                                else -> throw illegalArg("Auto detection of array-type failed due to unknown value for \$$index, declared type: $typeName, found type: ${testValue::class.simpleName}")
                            }
                        }
                    }
                    when (type) {
                        BOOLEAN_ARRAY,
                        SHORT_ARRAY,
                        INT_ARRAY,
                        INT64_ARRAY,
                        FLOAT_ARRAY,
                        DOUBLE_ARRAY,
                        STRING_ARRAY,
                        JSONB_ARRAY -> {
                            stmt.setArray(index, stmt.connection.createArrayOf(type.childType!!.string, arg))
                        }
                        BYTE_ARRAY_ARRAY -> {
                            // This is a hack, because we need a `Byte[][]`, JDBC does not support an `Object[][]`,
                            // even while the content may be the same, and it knows the type, still
                            // Note: I guess the driver supports Object[][] for other types, because it can invoke
                            //   helpers like `toString`, `toInt`, `toLong`, ... on them, but there is no such thing
                            //   for byte-arrays (byte[]), and instead of writing an own toByteArray, they fail!
                            val arr = Array(arg.size) { arg[it] as ByteArray? }
                            stmt.setArray(index, stmt.connection.createArrayOf(type.childType!!.string, arr))
                        }
                        BOOLEAN,
                        SHORT,
                        INT,
                        INT64,
                        FLOAT,
                        DOUBLE,
                        STRING,
                        BYTE_ARRAY,
                        JSONB -> throw illegalArg("The argument is $type, but an array was provided as value")
                        null -> throw illegalArg("Failed to detect array type, no type-name was provided (null)")
                        else -> throw illegalArg("Failed to detect array type, and invalid type-name was provided: $typeName")
                    }
                }
                is PAnyArray -> setArgument(stmt, arg.toArray(), indices)
                null -> stmt.setNull(index, 0)
                else -> throw illegalArg("Unable to set argument: args[${index - 1}], unknown type: ${arg.javaClass.name}")
            }
        }
    }

    fun bindArguments(stmt: PreparedStatement, args: Array<Any?>) {
        var i = 0
        while (i < args.size) {
            val arg = args[i]
            val indices = dollarToIndices[i + 1]
            check(indices != null) { "Indices must not be null - could not resolve index for \$${i + 1}" }
            setArgument(stmt, arg, indices)
            i++
        }
    }

    fun prepare(conn: Connection): PreparedStatement {
        return conn.prepareStatement(sql, TYPE_FORWARD_ONLY, CONCUR_READ_ONLY, CLOSE_CURSORS_AT_COMMIT)
    }

    companion object {
        private const val SUSPICIOUSLY_MANY_ARGS = 1_000
        private val positiveNums = '1'..'9'
        private val nums = '0'..'9'
    }
}
