@file:Suppress("OPT_IN_USAGE")

package naksha.model.request.ops

import naksha.base.NotNullProperty
import naksha.base.NullableProperty
import naksha.model.objects.Member
import kotlin.js.JsExport

/**
 * Tests if the member at [at] is less than the given [value] (LessThan).
 * @since 3.0
 */
@JsExport
class Lt() : Op() {
    companion object Lt_C {
        private val VALUE = NotNullProperty<Lt, Any>(Any::class)
    }

    constructor(at: String, value: Any) : this() {
        this.op = LT
        this.at = at
        this.value = value
    }

    constructor(at: Member, value: Any) : this(at.name, value)

    var value: Any by VALUE
}
