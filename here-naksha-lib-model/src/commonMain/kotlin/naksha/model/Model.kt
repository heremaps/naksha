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