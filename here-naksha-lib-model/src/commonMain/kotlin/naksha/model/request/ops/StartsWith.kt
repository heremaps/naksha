@file:Suppress("OPT_IN_USAGE")

package naksha.model.request.ops

import naksha.base.NotNullProperty
import naksha.model.objects.Member
import kotlin.js.JsExport

/**
 * Tests if the member at [at] starts with the given [value].
 * @since 3.0
 */
@JsExport
class StartsWith() : Op() {
    companion object StartsWith_C {
        private val VALUE = NotNullProperty<StartsWith, String>(String::class) { _,_ -> "" }
    }

    constructor(at: String, value: String) : this() {
        this.op = STARTS_WITH
        this.at = at
        this.value = value
    }

    constructor(at: Member, value: String) : this(at.name, value)

    var value: String by VALUE
}
