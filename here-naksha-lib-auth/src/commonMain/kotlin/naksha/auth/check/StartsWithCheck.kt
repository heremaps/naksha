@file:Suppress("OPT_IN_USAGE")

package naksha.auth.check

import kotlin.js.JsExport
import kotlin.js.JsName

/**
 * Tests if the attribute value is a [String] and starts with at least one of the given arguments.
 */
@JsExport
class StartsWithCheck() : CompiledCheck() {

    @JsName("withArgs")
    constructor(vararg args: Any?): this(){
        addAll(args)
    }

    override fun matches(value: Any?): Boolean {
        if (value !is String) {
            return false
        }
        return filterIsInstance<String>()
            .any { arg -> value.startsWith(arg) }
    }
}