@file:Suppress("OPT_IN_USAGE")

package naksha.auth.check

import naksha.base.AnyList
import kotlin.js.JsExport

/**
 * A test operation.
 */
@JsExport
abstract class CompiledCheck: AnyList() {
    abstract fun matches(value: Any?): Boolean
}