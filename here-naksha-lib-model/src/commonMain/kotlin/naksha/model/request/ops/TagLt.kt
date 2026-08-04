@file:Suppress("OPT_IN_USAGE")

package naksha.model.request.ops

import naksha.base.NotNullProperty
import naksha.model.objects.Member
import kotlin.js.JsExport
import kotlin.js.JsName

/**
 * Tests if the tag [key] on the member at [at] is less than the given [value].
 * @since 3.0
 */
@JsExport
class TagLt() : Op() {
    companion object TagLt_C {
        private val KEY = NotNullProperty<TagLt, String>(String::class) { _,_ -> "" }
        private val VALUE = NotNullProperty<TagLt, Any>(Any::class)
    }

    @JsName("forName")
    constructor(at: String, key: String, value: Any) : this() {
        this.op = TAG_LT
        this.at = at
        this.key = key
        this.value = value
    }

    @JsName("forMember")
    constructor(at: Member, key: String, value: Any) : this(at.id, key, value)

    var key: String by KEY
    var value: Any by VALUE
}
