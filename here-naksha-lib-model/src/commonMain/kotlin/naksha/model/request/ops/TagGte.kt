@file:Suppress("OPT_IN_USAGE")

package naksha.model.request.ops

import naksha.base.NotNullProperty
import naksha.base.NullableProperty
import naksha.model.objects.Member
import kotlin.js.JsExport

/**
 * Tests if the tag [key] on the member at [at] is greater than or equal to the given [value].
 * @since 3.0
 */
@JsExport
class TagGte() : Op() {
    companion object TagGte_C {
        private val KEY = NotNullProperty<TagGte, String>(String::class) { _,_ -> "" }
        private val VALUE = NotNullProperty<TagGte, Any>(Any::class)
    }

    constructor(at: String, key: String, value: Any) : this() {
        this.op = TAG_GTE
        this.at = at
        this.key = key
        this.value = value
    }

    constructor(at: Member, key: String, value: Any) : this(at.name, key, value)

    var key: String by KEY
    var value: Any by VALUE
}
