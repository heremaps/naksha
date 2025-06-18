@file:Suppress("OPT_IN_USAGE")

package naksha.model.request

import naksha.base.NotNullProperty
import naksha.base.NakshaError
import naksha.base.NakshaException
import naksha.base.Platform.Platform_C.forKClass
import naksha.base.PlatformType
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.js.JsStatic
import kotlin.jvm.JvmField
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
    @JsName("ErrorResponseOf")
    constructor(code: String, msg: String, cause: Throwable? = null) : this() {
        this.error = NakshaError(code, msg, cause)
    }

    /**
     * Create an error response from a {@link NakshaError}.
     * @param error the error from which to generate the error response.
     */
    @JsName("ErrorResponseFromError")
    constructor(error: NakshaError) : this() {
        this.error = error
    }

    /**
     * Create an error response from an [NakshaException].
     * @param e the exception from which to generate the error response.
     */
    @Suppress("NON_EXPORTABLE_TYPE")
    @JsName("ErrorResponseFromException")
    constructor(e: NakshaException) : this() {
        this.error = e.error
    }

    companion object ErrorResponse_C {
        /**
         * The [PlatformType] of [ErrorResponse].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE = forKClass(ErrorResponse::class).withPackageName(PACKAGE_NAME)

        private val ERROR = NotNullProperty<ErrorResponse, NakshaError>(NakshaError.TYPE) { _, _ ->
            NakshaError(NakshaError.EXCEPTION, "Unknown error")
        }
    }

    /**
     * The error reason.
     * @since 3.0
     */
    var error by ERROR

    override fun toString(): String = error.toString()
}
