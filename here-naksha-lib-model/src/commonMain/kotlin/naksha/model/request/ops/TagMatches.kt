@file:Suppress("OPT_IN_USAGE")

package naksha.model.request.ops

import naksha.base.NotNullProperty
import naksha.model.objects.Member
import kotlin.js.JsExport
import kotlin.js.JsName

/**
 * Tests if the value of the tag [key] on the member at [at] matches the given regular expression [regex].
 * @since 3.0
 */
@JsExport
class TagMatches() : Op() {
    companion object TagMatches_C {
        private val KEY = NotNullProperty<TagMatches, String>(String::class) { _,_ -> "" }
        private val REGEX = NotNullProperty<TagMatches, String>(String::class) { _,_ -> ".*" }
    }

    @JsName("forName")
    constructor(at: String, key: String, regex: String) : this() {
        this.op = TAG_MATCHES
        this.at = at
        this.key = key
        this.regex = regex
    }

    @JsName("forMember")
    constructor(at: Member, key: String, regex: String) : this(at.id, key, regex)

    var key: String by KEY
    var regex: String by REGEX
}
