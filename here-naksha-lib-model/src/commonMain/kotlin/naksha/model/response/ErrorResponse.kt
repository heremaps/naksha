@file:Suppress("OPT_IN_USAGE")

package naksha.model.response

import naksha.model.NakshaError
import kotlin.js.JsExport
import kotlin.jvm.JvmField

/**
 * Error response, means at least one operation failed. Transaction should be rolled back.
 * @property error the error code as returned by the storage.
 */
@JsExport
open class ErrorResponse(@JvmField val error: NakshaError) : Response() {
    override fun size(): Int = 0

    override fun toString(): String = error.toString()
}