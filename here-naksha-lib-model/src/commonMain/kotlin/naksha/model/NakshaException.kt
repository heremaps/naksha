@file:Suppress("OPT_IN_USAGE")

package naksha.model

import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.jvm.JvmField

/**
 * A Naksha exception.
 * @property error the error that happened.
 * @since 3.0.0
 */
@JsExport
open class NakshaException(@JvmField val error: NakshaError) : RuntimeException(error.msg, error.cause) {
    /**
     * Create an exception with error details individually specified.
     * @param code the error code.
     * @param msg the human-readable error message.
     * @param id the optional identifier related to the error; if any.
     * @param cause the cause (exception) of this error; if any.
     * @since 3.0.0
     */
    @JsName("of")
    constructor(code: String, msg: String, id: String? = null, cause: Throwable? = null) : this(NakshaError(code, msg, id, cause))
}
