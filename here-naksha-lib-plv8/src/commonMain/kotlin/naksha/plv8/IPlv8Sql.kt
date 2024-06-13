@file:OptIn(ExperimentalJsExport::class)

package naksha.plv8

import naksha.base.P_JsMap
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/**
 * The API that grants access to native PLV8 SQL interface.
 */
@Suppress("DuplicatedCode")
@JsExport
interface IPlv8Sql {
    /**
     * Returns general information about the database to which this API grants access.
     */
    fun info(): PgDbInfo

    /**
     * Quotes a string literal, so a custom string. For PostgresQL database this means to replace all single quotes
     * (`'`) with two single quotes (`''`). This encloses the string with quotation characters, when needed.
     * @param parts The literal parts to merge and quote.
     * @return The quoted literal.
     */
    fun quoteLiteral(vararg parts: String): String {
        val sb = StringBuilder()
        sb.append("E'")
        for (part in parts) {
            for (c in part) {
                when (c) {
                    '\'' -> sb.append('\'').append('\'')
                    '\\' -> sb.append('\\').append('\\')
                    else -> sb.append(c)
                }
            }
        }
        sb.append('\'')
        return sb.toString()
    }

    /**
     * Quotes an identifier, so a database internal name. For PostgresQL database this means to replace all double quotes
     * (`"`) with two double quotes (`""`). This encloses the string with quotation characters, when needed.
     */
    fun quoteIdent(vararg parts: String): String {
        val sb = StringBuilder()
        sb.append('"')
        for (part in parts) {
            for (c in part) {
                when (c) {
                    '"' -> sb.append('"').append('"')
                    '\\' -> sb.append('\\').append('\\')
                    else -> sb.append(c)
                }
            }
        }
        sb.append('"')
        return sb.toString()
    }

    /**
     * Tests if the given parameter stores the number of affected rows and if it does, returns
     * the number. If not, returns _null_.
     * @param any The object to test.
     * @return The number of affected rows or _null_.
     */
    fun affectedRows(any: Any): Int?

    /**
     * Tests if the given parameter is an array of rows, and if it is, returns the rows
     * as array of native maps.
     * @param any The object to test.
     * @return The array of native maps or _null_, if _any_ is no valid rows.
     */
    fun rows(any: Any): Array<P_JsMap>?

    /**
     * Execute an SQL query with the given arguments. The placeholder should be **$1** to **$n**.
     * @param sql The SQL query to execute.
     * @param args The arguments to be set at $n position, where $1 is the first array element.
     * @return Either the number of affected rows or the fetched rows.
     */
    fun execute(sql: String, args: Array<Any?>? = null): Any

    /**
     * Prepare the given SQL statement using parameters of the given types.
     * @param sql The SQL query to execute.
     * @param typeNames The name of the types of the arguments, to be at $n position, where $1 is the first array element.
     * @return The prepared plan.
     */
    fun prepare(sql: String, typeNames: Array<String>? = null):IPlv8Plan

    fun executeBatch(plan:IPlv8Plan, bulkParams: Array<Array<Param>>): IntArray

    /**
     * Compress bytes using GZip.
     * @param raw The bytes to compress.
     * @return The deflated (compressed) bytes.
     */
    fun gzipCompress(raw: ByteArray): ByteArray

    /**
     * Decompress bytes compressed by GZip.
     * @param compressed The bytes to decompress.
     * @return The inflated (decompress) bytes.
     */
    fun gzipDecompress(raw: ByteArray): ByteArray
}