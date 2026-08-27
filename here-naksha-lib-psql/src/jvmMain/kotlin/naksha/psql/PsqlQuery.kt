package naksha.psql

import naksha.base.Int64
import naksha.base.JvmInt64
import naksha.base.ListProxy
import naksha.base.MapProxy
import naksha.base.Platform
import naksha.base.illegalArg
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet.*
import kotlin.reflect.KClass

/**
 * A helper to parse SQL queries and find the dollar-placeholders, replacing them with `?`, escape question-marks, and finally provide a
 * way to bind the arguments.
 */
class PsqlQuery(query: String, private val typeNames: Array<String>?) {

    /**
     * A map of placeholder positions (`1..n`) in the prepared statement.
     *
     * For example
     * ```sql
     * SELECT * FROM table WHERE a=$1 OR b=$1
     * ```
     * In that case JDBC requires:
     * ```sql
     * SELECT * FROM table WHERE a=? OR b=?
     * ```
     * This map will then contain a key `1` _(which is the dollar-index)_, assigned to an array with the JDBC indices to bind. In this case `1` and `2`. When binding the arguments the code needs to bind it to all indices. Beware that JDBC positions start at index `1` not `0` and the index array contains already the correct JDBC values, so all values will be between `1` and `n` _(inclusive)_!
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
            Platform.logger.warn("Potentially inefficient query detected (${dollarToIndices.size} arguments): $sql")
        }
    }

    /**
     * Binds the given argument in the given prepared statement.
     *
     * Beware that we support the natural PostgresQL syntax, which allows to refer the same value multiple times, so for example `WHERE a = $1 OR b = $1` is allowed, however, in JDBC we have to translate this into `WHERE a = ? OR b = ?` and we now need to bind both placeholders to the same value. Therefore, the argument `indices` provides the zero-based indices in the query where the corresponding question mark is located. This means, we may need to bind the same argument multiple times.
     * @param stmt the statement.
     * @param arg the value to bind.
     * @param typeName the type name as specified in the prepared statement; if any is available.
     * @param indices a list of indices to bind the argument to.
     */
    private fun setArgument(stmt: PreparedStatement, arg: Any?, typeName: String?, indices: ArrayList<Int>) {
        val conn = stmt.connection
        when (arg) {
            // Note: `index` in JDBC starts with 1, NOT 0!
            null -> for (index in indices) stmt.setNull(index, 0)
            // Recursive call, simply convert the list into an array, then it's handled below.
            is ListProxy<*> -> setArgument(stmt, arg.toArray(), typeName, indices)
            // Wrapped primitives.
            is Boolean -> for (index in indices) stmt.setBoolean(index, arg)
            is Byte -> for (index in indices) stmt.setShort(index, arg.toShort())
            is Short -> for (index in indices) stmt.setShort(index, arg)
            is Int -> for (index in indices) stmt.setInt(index, arg)
            is Long -> for (index in indices) stmt.setLong(index, arg)
            is Int64 -> for (index in indices) stmt.setLong(index, arg.toLong())
            is Float -> for (index in indices) stmt.setFloat(index, arg)
            is Double -> for (index in indices) stmt.setDouble(index, arg)
            is String -> for (index in indices) stmt.setString(index, arg)
            is CharSequence -> for (index in indices) stmt.setString(index, arg.toString())
            // Handle Primitive Arrays.
            // https://jdbc.postgresql.org/documentation/server-prepare/#arrays
            is ByteArray -> for (index in indices) stmt.setBytes(index, arg)
            is BooleanArray -> for (index in indices) stmt.setObject(index, arg)
            is ShortArray -> for (index in indices) stmt.setObject(index, arg)
            is IntArray -> for (index in indices) stmt.setObject(index, arg)
            is LongArray -> for (index in indices) stmt.setObject(index, arg)
            is FloatArray -> for (index in indices) stmt.setObject(index, arg)
            is DoubleArray -> for (index in indices) stmt.setObject(index, arg)
            // Handle Array<*>.
            is Array<*> -> {
                // https://jdbc.postgresql.org/documentation/server-prepare/#arrays
                var componentType = arg.javaClass.componentType!!
                var array: Array<*> = arg
                if (componentType == Any::class.java || componentType == Int64::class.java || componentType == JvmInt64::class.java) {
                    // Special handling for `Array<Any>`:
                    // Array of objects need to be turned either into a `java.sql.Array` or converted into a `Array<Type>`.
                    // The reason is that the driver only accepts arrays with same content, and if the content is null,
                    // it still needs to know the type.
                    // We treat Int64 as Long until we switched finally!
                    if (componentType == Int64::class.java || componentType == JvmInt64::class.java) {
                        componentType = Long::class.javaObjectType
                    } else for (value in arg) {
                        if (value == null) continue
                        componentType = value.javaClass
                        if (componentType == Int64::class.java || componentType == JvmInt64::class.java) {
                            componentType = Long::class.javaObjectType
                        }
                        break
                    }
                    if (componentType == Any::class.java) {
                        // Try to detect the component type from what the client provided in the prepared statement.
                        if (typeName != null) {
                            val pgType = PgType.of(typeName)
                            if (pgType != null) {
                                val klass: KClass<*>? = if (pgType.isArray) pgType.componentType?.klass else pgType.klass
                                if (klass != null) componentType = klass.javaObjectType
                            }
                        }
                    }
                    // Convert into component array.
                    array = when (componentType) {
                        Boolean::class.javaObjectType -> Array(array.size) {
                            val v = arg[it]
                            if (v != null && v !is Boolean) throw illegalArg("Illegal value in Boolean array at $it: '$v'")
                            v
                        }
                        Short::class.javaObjectType -> Array(array.size) {
                            val v = arg[it]
                            if (v != null && v !is Short) throw illegalArg("Illegal value in Short array at $it: '$v'")
                            v
                        }
                        Int::class.javaObjectType -> Array(array.size) {
                            val v = arg[it]
                            if (v != null && v !is Int) throw illegalArg("Illegal value in Int array at $it: '$v'")
                            v
                        }
                        Long::class.javaObjectType -> Array(array.size) {
                            var v = arg[it]
                            if (v is Int64) v = v.toLong()
                            if (v != null && v !is Long) throw illegalArg("Illegal value in Long array at $it: '$v'")
                            v
                        }
                        Float::class.javaObjectType -> Array(array.size) {
                            val v = arg[it]
                            if (v != null && v !is Float) throw illegalArg("Illegal value in Float array at $it: '$v'")
                            v
                        }
                        Double::class.javaObjectType -> Array(array.size) {
                            val v = arg[it]
                            if (v != null && v !is Double) throw illegalArg("Illegal value in Double array at $it: '$v'")
                            v
                        }
                        String::class.java -> Array(array.size) {
                            val v = arg[it]
                            if (v != null && v !is String) throw illegalArg("Illegal value in String array at $it: '$v'")
                            v
                        }
                        ByteArray::class.java -> Array(array.size) {
                            val v = arg[it]
                            if (v != null && v !is ByteArray) throw illegalArg("Illegal value in ByteArray array at $it: '$v'")
                            v
                        }
                        else -> throw illegalArg("Can't detect correct component type of empty arrays or arrays with only null values")
                    }
                }
                if (!validComponentJavaTypes.contains(componentType)) {
                    throw illegalArg("Unsupported array type: ${componentType.name}")
                }
                for (index in indices) stmt.setObject(index, array)
            }
            is MapProxy<*, *> -> {
                val json = Platform.toJSON(arg)
                for (index in indices) stmt.setString(index, json)
            }
            else -> throw illegalArg("Failed to bind argument to query, unsupported type: ${arg::class.qualifiedName}")
        }
    }

    fun bindArguments(stmt: PreparedStatement, args: Array<Any?>) {
        var i = 0
        while (i < args.size) {
            val arg = args[i]
            val typeName = typeNames?.get(i)
            val indices = dollarToIndices[i + 1]
            check(indices != null) { "Indices must not be null - could not resolve index for \$${i + 1}" }
            setArgument(stmt, arg, typeName, indices)
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
        private val validComponentJavaTypes = setOf<Class<*>>(
            Short::class.javaObjectType, Short::class.javaPrimitiveType!!,
            Int::class.javaObjectType, Int::class.javaPrimitiveType!!,
            Long::class.javaObjectType, Long::class.javaPrimitiveType!!,
            Float::class.javaObjectType, Float::class.javaPrimitiveType!!,
            Double::class.javaObjectType, Double::class.javaPrimitiveType!!,
            Boolean::class.javaObjectType, Boolean::class.javaPrimitiveType!!,
            String::class.java,
            ByteArray::class.java
        )
    }
}
