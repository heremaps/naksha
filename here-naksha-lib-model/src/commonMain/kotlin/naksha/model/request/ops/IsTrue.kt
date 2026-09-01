@file:Suppress("OPT_IN_USAGE")

package naksha.model.request.ops

import naksha.model.objects.Member
import kotlin.js.JsExport
import kotlin.js.JsName

/**
 * Tests if the member at [at] is true.
 * @since 3.0
 */
@JsExport
class IsTrue() : Op() {
    @JsName("forName")
    constructor(at: String) : this() {
        this.op = IS_TRUE
        this.at = at
    }

    @JsName("forMember")
    constructor(at: Member) : this(at.name)
}
