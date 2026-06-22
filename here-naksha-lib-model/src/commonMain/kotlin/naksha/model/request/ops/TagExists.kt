@file:Suppress("OPT_IN_USAGE")

package naksha.model.request.ops

import naksha.base.NotNullProperty
import naksha.model.objects.Member
import kotlin.js.JsExport

/**
 * Tests if the tag [key] exists on the member at [at].
 * @since 3.0
 */
@JsExport
class TagExists() : Op() {
    companion object TagExists_C {
        private val KEY = NotNullProperty<TagExists, String>(String::class) { _,_ -> "" }
    }

    constructor(at: String, key: String) : this() {
        this.op = TAG_EXISTS
        this.at = at
        this.key = key
    }

    constructor(at: Member, key: String) : this(at.name, key)

    var key: String by KEY
}
