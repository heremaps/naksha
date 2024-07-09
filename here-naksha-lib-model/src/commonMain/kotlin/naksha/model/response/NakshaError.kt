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

    companion object {
        /**
         * The storage is not initialized.
         */
        const val STORAGE_NOT_INITIALIZED = "StorageNotInitialized"
        /**
         * The collection accessed does not exist.
         */
        const val COLLECTION_NOT_FOUND = "CollectionNotFound"
        /**
         * An unexpected error (not further specified) happened while processing the request.
         *
         * <p>This can result in a 500 Internal Server Error.
         */
        const val EXCEPTION = "Exception"
        /**
         * An event that was sent to the connector failed, because the connector cannot process it.
         *
         * <p>This will result in an 501 Not Implemented response.
         */
        const val NOT_IMPLEMENTED = "NotImplemented"
        /**
         * A conflict occurred when updating a feature.
         *
         * <p>This will result in an 409 Conflict response.
         */
        const val CONFLICT = "Conflict"
        /**
         * Indicates an authorization error.
         *
         * <p>This will result in a 401 Unauthorized response.
         */
        const val UNAUTHORIZED = "Unauthorized"
        /**
         * Indicates an authorization error.
         *
         * <p>This will result in an 403 Forbidden response.
         */
        const val FORBIDDEN = "Forbidden"
        /**
         * The connector cannot handle the request due to a processing limitation in an upstream service or a database.
         *
         * <p>This will result in an 429 Too Many Requests response.
         */
        const val TOO_MANY_REQUESTS = "TooManyRequests"
        /**
         * A provided argument is invalid or missing.
         *
         * <p>This will lead to a HTTP 400 Bad Request response.
         */
        const val ILLEGAL_ARGUMENT = "IllegalArgument"
        /**
         * Any service or remote function required to process the request was not reachable.
         *
         * <p>This will result in a 502 Bad Gateway response.
         */
        const val BAD_GATEWAY = "BadGateway"
        /**
         * The request was aborted due to a timeout.
         *
         * <p>This will result in a HTTP 504 Gateway Timeout response.
         */
        const val TIMEOUT = "Timeout"
        /**
         * The request was aborted due to PayloadTooLarge.
         *
         * <p>This will result in a HTTP 513 response.
         */
        const val PAYLOAD_TOO_LARGE = "PayloadTooLarge"
        /**
         * The requested feature was not available.
         *
         * <p>This will result in a HTTP 404 response.
         */
        const val NOT_FOUND = "NotFound"
    }
}