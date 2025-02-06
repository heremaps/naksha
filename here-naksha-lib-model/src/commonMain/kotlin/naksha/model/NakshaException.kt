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
expect class NakshaException : RuntimeException {
    val error: NakshaError

    constructor(error: NakshaError)

    constructor(code: String, msg: String, id: String? = null, cause: Throwable? = null)
}
