@file:Suppress("OPT_IN_USAGE")

package naksha.auth.check

import kotlin.js.JsExport
import kotlin.js.JsName

/**
 * Tests if the attribute value equals at least one of the arguments.
 */
@JsExport
class EqualsCheck() : CompiledCheck() {

    @JsName("withArgs")
    constructor(vararg args: Any?): this(){
        addAll(args)
    }
    override fun matches(value: Any?): Boolean =
        any { arg -> arg == value }
}