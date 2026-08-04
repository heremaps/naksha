@file:Suppress("OPT_IN_USAGE")

package naksha.auth.check

import naksha.base.PAnyArray
import kotlin.js.JsExport

/**
 * A test operation.
 */
@JsExport
abstract class CompiledCheck: PAnyArray() {
    abstract fun matches(value: Any?): Boolean
}