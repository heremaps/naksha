@file:Suppress("OPT_IN_USAGE")

package naksha.model.request

import naksha.base.NotNullProperty
import naksha.model.NakshaError
import naksha.model.NakshaException
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.jvm.JvmOverloads

/**
 * An error response, means at least one operation failed.
 * @property error the error code as returned by the storage.
 */
@JsExport
open class ErrorResponse() : Response() {

    /**
     * Create an error response from individual error parts.
     * @param code the error code.
     * @param msg a human-readable message.
     * @param cause the origin exception that caused this error; if any.
     */
    @JvmOverloads
    @JsName("of")
    constructor(code: String, msg: String, cause: Throwable? = null) : this() {
        this.error = NakshaError(code, msg, cause)
    }

    /**
     * Create an error response from a {@link NakshaError}.
     * @param error the error from which to generate the error response.
     */
    @JsName("fromError")
    constructor(error: NakshaError) : this() {
        this.error = error
    }

    /**
     * Create an error response from an [NakshaException].
     * @param e the exception from which to generate the error response.
     */
    @Suppress("NON_EXPORTABLE_TYPE")
    @JsName("fromException")
    constructor(e: NakshaException) : this() {
        this.error = e.error
    }

    companion object ErrorResponse_C {
        private val ERROR = NotNullProperty<ErrorResponse, NakshaError>(NakshaError::class) { _,_ ->
            NakshaError(NakshaError.EXCEPTION, "Unknown error")
        }
    }

    /**
     * The error reason.
     */
    var error by ERROR

    override fun toString(): String = error.toString()
}