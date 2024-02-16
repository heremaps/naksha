@file:OptIn(ExperimentalJsExport::class)

package com.here.naksha.lib.plv8

import com.here.naksha.lib.jbon.IMap
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/**
 * The API that grants access to native PLV8 SQL interface.
 */
@Suppress("DuplicatedCode")
@JsExport
interface IPlv8Sql {
    /**
     * Creates a new table for a function to return. When executed inside PostgresQL does only return a small object, that
     * allows invoking the native `plv8.return_next` function. For the JVM, it returns a container that can pick up rows.
     */
    fun newTable(): ITable

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
    fun rows(any: Any): Array<Any>?

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
    fun prepare(sql: String, typeNames: Array<String>? = null): IPlv8Plan
}