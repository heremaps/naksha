@file:Suppress("OPT_IN_USAGE")

package naksha.model.request.ops

import naksha.base.NotNullProperty
import naksha.model.objects.Member
import kotlin.js.JsExport
import kotlin.js.JsName

/**
 * Tests if the value of the tag [key] on the member [at] is a string and starts with the given [value].
 * @since 3.0
 */
@JsExport
class TagStartsWith() : Op() {
    companion object TagStartsWith_C {
        private val KEY = NotNullProperty<TagStartsWith, String>(String::class) { _,_ -> "" }
        private val VALUE = NotNullProperty<TagStartsWith, String>(String::class) { _,_ -> "" }
    }

    @JsName("forName")
    constructor(at: String, key: String, value: String) : this() {
        this.op = TAG_STARTS_WITH
        this.at = at
        this.key = key
        this.value = value
    }

    @JsName("forMember")
    constructor(at: Member, key: String, value: String) : this(at.name, key, value)

    var key: String by KEY
    var value: String by VALUE
}
