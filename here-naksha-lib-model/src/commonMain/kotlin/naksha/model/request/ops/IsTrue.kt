@file:Suppress("OPT_IN_USAGE")

package naksha.model.request.ops

import naksha.model.objects.Member
import kotlin.js.JsExport

/**
 * Tests if the member at [at] is true.
 * @since 3.0
 */
@JsExport
class IsTrue() : Op() {
    constructor(at: String) : this() {
        this.op = IS_TRUE
        this.at = at
    }

    constructor(at: Member) : this(at.name)
}
