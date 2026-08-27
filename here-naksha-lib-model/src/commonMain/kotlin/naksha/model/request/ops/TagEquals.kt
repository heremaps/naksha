@file:Suppress("OPT_IN_USAGE")

package naksha.model.request.ops

import naksha.base.NotNullProperty
import naksha.base.NullableProperty
import naksha.model.objects.Member
import kotlin.js.JsExport
import kotlin.js.JsName

/**
 * Tests if the value of the tag [key] on the member [at] equals the given [value]. The value may be `null`, in that case this is the same as [TagIsNull].
 * @since 3.0
 */
@JsExport
class TagEquals() : Op() {
    companion object TagEquals_C {
        private val KEY = NotNullProperty<TagEquals, String>(String::class) { _,_ -> "" }
        private val VALUE = NullableProperty<TagEquals, Any>(Any::class)
    }

    @JsName("forName")
    constructor(at: String, key: String, value: Any?) : this() {
        this.op = TAG_EQ
        this.at = at
        this.key = key
        this.value = value
    }

    @JsName("forMember")
    constructor(at: Member, key: String, value: Any?) : this(at.name, key, value)

    var key: String by KEY
    var value: Any? by VALUE
}
