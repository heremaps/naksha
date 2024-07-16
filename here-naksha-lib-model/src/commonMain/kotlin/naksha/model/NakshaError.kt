@file:Suppress("OPT_IN_USAGE", "NON_EXPORTABLE_TYPE")

package naksha.model

import naksha.base.JsEnum.JsEnumCompanion.get
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.jvm.JvmField

/**
 * An error class.
 */
@JsExport
open class NakshaError(
    /**
     * Error code.
     */
    @JvmField
    val code: NakshaErrorCode,
    /**
     * Human-readable message.
     */
    @JvmField
    val message: String = code.defaultMessage,
    /**
     * ID of object related to error.
     */
    @JvmField
    val id: String? = null,
    /**
     * Original exception; if any.
     */
    @JvmField
    val exception: Throwable? = null
) {
    /**
     * Create an [NakshaError] from the given error code and message.
     * @param code the error code.
     * @param message the error message.
     * @param id the feature-id; if any.
     * @param exception the cause; if any.
     */
    @JsName("of")
    constructor(code: String, message: String? = null, id: String? = null, exception: Exception? = null)
        : this(get(code, NakshaErrorCode::class), message ?: get(code, NakshaErrorCode::class).defaultMessage, id, exception)

}