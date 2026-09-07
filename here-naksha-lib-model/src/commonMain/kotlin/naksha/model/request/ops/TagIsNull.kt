@file:Suppress("OPT_IN_USAGE")

package naksha.model.request.ops

import naksha.base.NotNullProperty
import naksha.model.objects.Member
import kotlin.js.JsExport
import kotlin.js.JsName

/**
 * Tests if the value of the tag [key] on the member [at] is `null`.
 * @since 3.0
 */
@JsExport
class TagIsNull() : Op() {
    companion object TagIsNull_C {
        private val KEY = NotNullProperty<TagIsNull, String>(String::class) { _,_ -> "" }
    }

    @JsName("forName")
    constructor(at: String, key: String) : this() {
        this.op = TAG_IS_NULL
        this.at = at
        this.key = key
    }

    @JsName("forMember")
    constructor(at: Member, key: String) : this(at.name, key)

    var key: String by KEY
}
