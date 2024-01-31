@file:OptIn(ExperimentalJsExport::class)

package com.here.naksha.lib.jbon

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/**
 * The API that grants access to native SQL.
 */
@Suppress("DuplicatedCode")
@JsExport
interface ISql {
    /**
     * Quotes a string literal, so a custom string. For PostgresQL database this means to replace all double quotes
     * (`"`) with two double quotes (`""`).
     * @param parts The literal parts to merge and quote.
     * @return The quoted literal.
     */
    fun quoteLiteral(vararg parts: String): String {
        val sb = StringBuilder()
        for (part in parts) {
            for (c in part) {
                sb.append(c)
                if (c == '"') {
                    sb.append('"')
                }
            }
        }
        return sb.toString()
    }

    /**
     * Quotes an identifier, so a database internal name. For PostgresQL database this means to replace all single quotes
     * (`'`) with two single quotes (`''`).
     */
    fun quoteIdent(vararg parts: String): String {
        val sb = StringBuilder()
        for (part in parts) {
            for (c in part) {
                sb.append(c)
                if (c == '\'') {
                    sb.append('\'')
                }
            }
        }
        return sb.toString()
    }

    /**
     * Execute an SQL query with the given arguments. The placeholder should be **$1** to **$n**.
     * @param sql The SQL query to execute.
     * @param args The arguments to be set at $n position, where $1 is the first array element.
     * @return The result-set.
     */
    fun execute(sql: String, args: Array<Any?>): ISqlResultSet

    /**
     * Prepare the given SQL statement using parameters of the given types.
     * @param sql The SQL query to execute.
     * @param typeNames The name of the types of the arguments, to be at $n position, where $1 is the first array element.
     * @return The prepared plan.
     */
    fun prepare(sql: String, typeNames: Array<String>): ISqlPlan
}