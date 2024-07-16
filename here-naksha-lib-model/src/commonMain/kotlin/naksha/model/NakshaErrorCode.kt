package naksha.model

import naksha.base.JsEnum
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmField
import kotlin.reflect.KClass

/**
 * An enumeration about the possible well-known error codes returned by storages. Every storage can provide own special errors by simply
 * extend this class.
 */
@Suppress("OPT_IN_USAGE")
@JsExport
open class NakshaErrorCode : JsEnum() {
    @Suppress("NON_EXPORTABLE_TYPE")
    override fun namespace(): KClass<out JsEnum> = NakshaErrorCode::class

    override fun initClass() {}

    companion object StorageErrorCompanion {
        /**
         * Returned when an already initialized storage is initialized, providing a wrong identifier.
         */
        @JsStatic
        @JvmField
        val STORAGE_ID_MISMATCH = def(NakshaErrorCode::class, "storageIdMismatch") {
            self -> self.defaultMessage = "Storage identifier does not match the provided expected one"
        }
        /**
         * Returned when trying to create a collection that exists already.
         */
        @JsStatic
        @JvmField
        val COLLECTION_EXISTS = def(NakshaErrorCode::class, "collectionExists") {
                self -> self.defaultMessage = "The collection with the given identifier exists already"
        }
        /**
         * The collection accessed does not exist.
         */
        @JsStatic
        @JvmField
        val COLLECTION_NOT_FOUND = def(NakshaErrorCode::class, "CollectionNotFound") {
            self -> self.defaultMessage = "No collection found"
        }
        /**
         * An unexpected error (not further specified) happened while processing the request.
         *
         * <p>This can result in a 500 Internal Server Error.
         */
        @JsStatic
        @JvmField
        val EXCEPTION = def(NakshaErrorCode::class, "Exception") {
            self -> self.defaultMessage = "Unexpected exception occurred"
        }
        /**
         * An event that was sent to the connector failed, because the connector cannot process it.
         *
         * <p>This will result in an 501 Not Implemented response.
         */
        @JsStatic
        @JvmField
        val NOT_IMPLEMENTED = def(NakshaErrorCode::class, "NotImplemented")
        /**
         * A conflict occurred when updating a feature.
         *
         * <p>This will result in an 409 Conflict response.
         */
        @JsStatic
        @JvmField
        val CONFLICT = def(NakshaErrorCode::class, "Conflict")
        /**
         * Indicates an authorization error.
         *
         * <p>This will result in a 401 Unauthorized response.
         */
        @JsStatic
        @JvmField
        val UNAUTHORIZED = def(NakshaErrorCode::class, "Unauthorized")
        /**
         * Indicates an authorization error.
         *
         * <p>This will result in an 403 Forbidden response.
         */
        @JsStatic
        @JvmField
        val FORBIDDEN = def(NakshaErrorCode::class, "Forbidden")
        /**
         * The connector cannot handle the request due to a processing limitation in an upstream service or a database.
         *
         * <p>This will result in an 429 Too Many Requests response.
         */
        @JsStatic
        @JvmField
        val TOO_MANY_REQUESTS = def(NakshaErrorCode::class, "TooManyRequests")
        /**
         * A provided argument is invalid or missing.
         *
         * <p>This will lead to a HTTP 400 Bad Request response.
         */
        @JsStatic
        @JvmField
        val ILLEGAL_ARGUMENT = def(NakshaErrorCode::class, "IllegalArgument")
        /**
         * Any service or remote function required to process the request was not reachable.
         *
         * <p>This will result in a 502 Bad Gateway response.
         */
        @JsStatic
        @JvmField
        val BAD_GATEWAY = def(NakshaErrorCode::class, "BadGateway")
        /**
         * The request was aborted due to a timeout.
         *
         * <p>This will result in a HTTP 504 Gateway Timeout response.
         */
        @JsStatic
        @JvmField
        val TIMEOUT = def(NakshaErrorCode::class, "Timeout")
        /**
         * The request was aborted due to PayloadTooLarge.
         *
         * <p>This will result in a HTTP 513 response.
         */
        @JsStatic
        @JvmField
        val PAYLOAD_TOO_LARGE = def(NakshaErrorCode::class, "PayloadTooLarge")
        /**
         * The requested feature was not available.
         *
         * <p>This will result in a HTTP 404 response.
         */
        @JsStatic
        @JvmField
        val NOT_FOUND = def(NakshaErrorCode::class, "NotFound")
    }

    /**
     * The error-code of this error.
     */
    val code: String = toString()

    /**
     * The default message to use, if no explicit message provided, when throwing an [StorageException].
     */
    var defaultMessage: String = toString()
        protected set
}
