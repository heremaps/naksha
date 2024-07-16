@file:Suppress("OPT_IN_USAGE")

package naksha.model

import kotlin.js.JsExport

/**
 * Tests if the given identifier is a valid Naksha identifier.
 * @param id the identifier to test.
 * @return _true_ if the identifier is valid; _false_ otherwise.
 */
@JsExport
fun isValidId(id: String?): Boolean {
    if (id.isNullOrEmpty() || "naksha" == id || id.length > 32) return false
    var i = 0
    var c = id[i++]
    // First character must be a-z
    if (c.code < 'a'.code || c.code > 'z'.code) return false
    while (i < id.length) {
        c = id[i++]
        when (c.code) {
            in 'a'.code..'z'.code -> continue
            in '0'.code..'9'.code -> continue
            '_'.code, ':'.code, '-'.code -> continue
            else -> return false
        }
    }
    return true
}

/**
 * Quote the given identifier.
 * @param id the identifier.
 */
fun quoteId(id: String): String {
    val sb = StringBuilder(id.length)
    var i = 0
    while (i < id.length) {
        when (val c = id[i++]) {
            ':' -> sb.append('|')
            '|' -> sb.append('|').append('|')
            else -> sb.append(c)
        }
    }
    return sb.toString()
}

fun unquoteId(id: String): String {
    val sb = StringBuilder(id.length)
    var i = 0
    while (i < id.length) {
        val c = id[i++]
        require(c != ':') { "Invalid character found, there should be no colon in an quoted identifier!" }
        if (c == '|') if (i < id.length || id[i] != '|') { sb.append(':'); i++ } else sb.append('|') else sb.append(c)
    }
    return sb.toString()
}

