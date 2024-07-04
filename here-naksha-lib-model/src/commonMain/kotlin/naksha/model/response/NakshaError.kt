package naksha.model.response

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlin.js.JsName

@OptIn(ExperimentalJsExport::class)
@JsExport
class NakshaError(
    /**
     * Error code.
     */
    val error: String,
    /**
     * Human-readable message.
     */
    val message: String,
    /**
     * ID of object related to error.
     */
    val id: String? = null,
    /**
     * Original exception.
     */
    val exception: Throwable? = null
) {
    @JsName("lazyNakshaError")
    constructor(error: String, message: String) :
            this(error,message,null,null)
}