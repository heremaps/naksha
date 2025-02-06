@file:Suppress("OPT_IN_USAGE")

package naksha.model

import kotlin.js.JsExport
import kotlin.js.JsName

/**
 * A Naksha exception.
 * @property error the error that happened.
 * @since 3.0.0
 */
@JsExport
class NakshaException(
    /**
     * The [NakshaError] that causes this exception.
     * @since 3.0.0
     */
    val error: NakshaError
) : RuntimeException(error.msg, error.cause) {

    /**
     * Secondary constructor to directly
     */
    @JsName("of")
    constructor(code: String, msg: String, id: String? = null, cause: Throwable? = null) : this(NakshaError(code, msg, id, cause))
}