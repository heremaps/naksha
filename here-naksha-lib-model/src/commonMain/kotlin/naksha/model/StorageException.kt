@file:Suppress("OPT_IN_USAGE")

package naksha.model

import kotlin.js.JsExport
import kotlin.js.JsName

/**
 * An storage exception.
 * @property error the error that happened.
 */
@JsExport
open class StorageException(val error: NakshaError) : RuntimeException(error.message, error.exception) {
    @JsName("of")
    constructor(code: NakshaErrorCode, message: String = code.defaultMessage, id: String? = null, cause: Throwable? = null)
            : this(NakshaError(code, message, id, cause))
}
