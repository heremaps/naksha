@file:Suppress("OPT_IN_USAGE")

package naksha.model.request.ops

import naksha.base.NullableProperty
import naksha.model.objects.Member
import kotlin.js.JsExport

/**
 * Tests if the member at [at] equals the given [value].
 * @since 3.0
 */
@JsExport
class Equals() : Op(), ICompare {
    companion object Equals_C {
        private val VALUE = NullableProperty<Equals, Any>(Any::class)
    }

    constructor(at: String, value: Any?) : this() {
        this.op = EQ
        this.at = at
        this.value = value
    }

    constructor(at: Member, value: Any?) : this(at.name, value)

    /**
     * The value to compare against.
     * @since 3.0
     */
    var value: Any? by VALUE
}
