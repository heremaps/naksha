@file:Suppress("OPT_IN_USAGE", "NON_EXPORTABLE_TYPE")

package naksha.model

import naksha.base.AnyObject
import naksha.base.NotNullProperty
import naksha.base.NullableProperty
import kotlin.js.JsExport
import kotlin.js.JsName

/**
 * An error class.
 * @since 3.0.0
 */
@JsExport
open class NakshaError() : AnyObject() {

    /**
     * Create a new error from the given arguments.
     * @param code the error code.
     * @param msg a human-readable message.
     * @param id the identifier of the object that relates to the error; if any.
     * @param cause the origin exception that caused this error; if any.
     */
    @JsName("of")
    constructor(code: String, msg: String, id: String? = null, cause: Throwable? = null) : this() {
        this.code = code
        this.msg = msg
        this.id = id
        this.cause = cause
    }

    companion object NakshaErrorCompanion {
        /**
         * A general error (not further specified) happened while processing the request.
         *
         * This results in a 500 Internal Server Error.
         * @since 3.0.0
         */
        const val EXCEPTION = "Exception"

        /**
         * Returned when an already initialized storage is initialized, providing a wrong identifier.
         * @since 3.0.0
         */
        const val STORAGE_ID_MISMATCH = "StorageIdMismatch"

        /**
         * Returned something requires initialisation before some method can be invoked.
         * @since 3.0.0
         */
        const val UNINITIALIZED = "Uninitialized"

        /**
         * A provided identifier is not allowed.
         * @since 3.0.0
         */
        const val ILLEGAL_ID = "IllegalId"

        /**
         * Returned when trying to create a collection that exists already.
         * @since 3.0.0
         */
        const val COLLECTION_EXISTS = "CollectionExists"

        /**
         * The collection accessed does not exist.
         * @since 3.0.0
         */
        const val COLLECTION_NOT_FOUND = "CollectionNotFound"

        /**
         * A specific partition was not found.
         * @since 3.0.0
         */
        const val PARTITION_NOT_FOUND = "PartitionNotFound"

        /**
         * An event that was sent to the connector failed, because the connector cannot process it.
         *
         * This will result in an 501 Not Implemented response.
         * @since 3.0.0
         */
        const val NOT_IMPLEMENTED = "NotImplemented"

        /**
         * An error being thrown when an optional operation is not supported.
         * @since 3.0.0
         */
        const val UNSUPPORTED_OPERATION = "UnsupportedOperation"

        /**
         * A conflict occurred when updating a feature.
         *
         * This will result in an 409 Conflict response.
         * @since 3.0.0
         */
        const val CONFLICT = "Conflict"

        /**
         * Indicates an authorization error.
         *
         * This will result in a 401 Unauthorized response.
         * @since 3.0.0
         */
        const val UNAUTHORIZED = "Unauthorized"

        /**
         * Indicates an authorization error.
         *
         * This will result in an 403 Forbidden response.
         * @since 3.0.0
         */
        const val FORBIDDEN = "Forbidden"

        /**
         * The connector cannot handle the request due to a processing limitation in an upstream service or a database.
         *
         * This will result in an 429 Too Many Requests response.
         * @since 3.0.0
         */
        const val TOO_MANY_REQUESTS = "TooManyRequests"

        /**
         * A provided argument is invalid or missing.
         *
         * This will lead to an HTTP 400 Bad Request response.
         * @since 3.0.0
         */
        const val ILLEGAL_ARGUMENT = "IllegalArgument"

        /**
         * Something is expected in a specific state, but was found differently.
         *
         * This will lead to an HTTP 500 Internal Server Error response.
         * @since 3.0.0
         */
        const val ILLEGAL_STATE = "IllegalState"

        /**
         * Any service or remote function required to process the request was not reachable.
         *
         * This will result in a 502 Bad Gateway response.
         * @since 3.0.0
         */
        const val BAD_GATEWAY = "BadGateway"

        /**
         * The request was aborted due to a timeout.
         *
         * This will result in an HTTP 504 Gateway Timeout response.
         * @since 3.0.0
         */
        const val TIMEOUT = "Timeout"

        /**
         * The request was aborted due to PayloadTooLarge.
         *
         * This will result in an HTTP 513 response.
         * @since 3.0.0
         */
        const val PAYLOAD_TOO_LARGE = "PayloadTooLarge"

        /**
         * The requested feature was not available.
         *
         * This will result in an HTTP 404 response.
         * @since 3.0.0
         */
        const val NOT_FOUND = "NotFound"

        /**
         * A feature does exist, but is expected to not exist.
         *
         * @since 3.0.0
         */
        const val FEATURE_EXISTS = "FeatureExists"

        /**
         * A feature does not exist, but is expected to exist.
         *
         * @since 3.0.0
         */
        const val FEATURE_NOT_FOUND = "FeatureNotFound"

        /**
         * A map does exist, but is expected to not exist.
         *
         * @since 3.0.0
         */
        const val MAP_EXISTS = "MapExists"

        /**
         * A map does not exist, but is expected to exist.
         *
         * @since 3.0.0
         */
        const val MAP_NOT_FOUND = "MapNotFound"

        private val CODE = NotNullProperty<NakshaError, String>(String::class) { _, _ -> EXCEPTION }
        private val MSG = NotNullProperty<NakshaError, String>(String::class) { self, _ -> self.code }
        private val ID = NullableProperty<NakshaError, String>(String::class)
        private val THROWABLE = NullableProperty<NakshaError, Throwable>(Throwable::class)
    }

    /**
     * The error code.
     */
    var code by CODE

    /**
     * A human-readable message.
     */
    var msg by MSG

    /**
     * The identifier of the object that relates to the error; if any.
     */
    var id by ID

    /**
     * The origin exception that caused this error; if any.
     */
    var cause by THROWABLE

    override fun hashCode(): Int = code.hashCode()
    override fun equals(other: Any?): Boolean {
        return other is NakshaError
                && code == other.code
                && msg == other.msg
                && id == other.id
                && cause == other.cause
    }

    override fun toString(): String = code
}