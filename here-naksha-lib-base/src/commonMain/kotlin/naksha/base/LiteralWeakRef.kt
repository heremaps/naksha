package naksha.base

import kotlin.js.JsExport

/**
 * A weak reference to an [Literal]
 * @since 3.0
 */
@JsExport
class LiteralWeakRef internal constructor(referent: Literal?): BaseWeakRef<Literal>(referent) {
    init {
        atomic = false
        readOnly = true
        immutable = true
    }
}
