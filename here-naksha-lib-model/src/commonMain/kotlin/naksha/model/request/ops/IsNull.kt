@file:Suppress("OPT_IN_USAGE")

package naksha.model.request.ops

import naksha.model.objects.Member
import kotlin.js.JsExport

/**
 * Tests if the member at [at] is null.
 * @since 3.0
 */
@JsExport
class IsNull() : Op() {
    constructor(at: String) : this() {
        this.op = IS_NULL
        this.at = at
    }

    constructor(at: Member) : this(at.name)
}
