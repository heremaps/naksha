@file:Suppress("OPT_IN_USAGE")

package naksha.model.request.ops

import naksha.base.NotNullProperty
import naksha.model.objects.Member
import kotlin.js.JsExport
import kotlin.js.JsName

/**
 * Tests if the tag [key] exists on the member at [at].
 * @since 3.0
 */
@JsExport
class TagMapHasKey() : Op() {
    companion object TagExists_C {
        private val KEY = NotNullProperty<TagMapHasKey, String>(String::class) { _, _ -> "" }
    }

    @JsName("forName")
    constructor(at: String, key: String) : this() {
        this.op = TAGMAP_HAS_KEY
        this.at = at
        this.key = key
    }

    @JsName("forMember")
    constructor(at: Member, key: String) : this(at.id, key)

    var key: String by KEY
}
