@file:Suppress("OPT_IN_USAGE")

package naksha.model.request.ops

import naksha.base.NotNullProperty
import naksha.model.objects.Member
import kotlin.js.JsExport
import kotlin.js.JsName

/**
 * Tests if the member at [at] is greater than or equal to the given [value] (GreaterThanOrEqual).
 * @since 3.0
 */
@JsExport
class Gte() : Op() {
    companion object Gte_C {
        private val VALUE = NotNullProperty<Gte, Any>(Any::class)
    }

    @JsName("forName")
    constructor(at: String, value: Any) : this() {
        this.op = GTE
        this.at = at
        this.value = value
    }

    @JsName("forMember")
    constructor(at: Member, value: Any) : this(at.id, value)

    var value: Any by VALUE
}
