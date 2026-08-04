@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package naksha.base

import naksha.base.NakshaError.NakshaErrorCompanion.COLLECTION_EXISTS
import naksha.base.NakshaError.NakshaErrorCompanion.COLLECTION_NOT_FOUND
import naksha.base.NakshaError.NakshaErrorCompanion.CONFLICT
import naksha.base.NakshaError.NakshaErrorCompanion.EXCEPTION
import naksha.base.NakshaError.NakshaErrorCompanion.FEATURE_EXISTS
import naksha.base.NakshaError.NakshaErrorCompanion.FEATURE_NOT_FOUND
import naksha.base.NakshaError.NakshaErrorCompanion.FORBIDDEN
import naksha.base.NakshaError.NakshaErrorCompanion.ILLEGAL_ARGUMENT
import naksha.base.NakshaError.NakshaErrorCompanion.ILLEGAL_ID
import naksha.base.NakshaError.NakshaErrorCompanion.ILLEGAL_STATE
import naksha.base.NakshaError.NakshaErrorCompanion.CATALOG_EXISTS
import naksha.base.NakshaError.NakshaErrorCompanion.INTERNAL_ERROR
import naksha.base.NakshaError.NakshaErrorCompanion.MAP_NOT_FOUND
import naksha.base.NakshaError.NakshaErrorCompanion.UNINITIALIZED
import naksha.base.NakshaError.NakshaErrorCompanion.UNSUPPORTED_OPERATION
import kotlin.jvm.JvmOverloads

/**
 * A Naksha exception.
 * @property error the error that happened.
 * @since 3.0
 */
expect class NakshaException : RuntimeException {
    /**
     * The [NakshaError] that causes this exception.
     * @since 3.0
     */
    val error: NakshaError

    /**
     * Create exception based upon an [NakshaError].
     * @param error the [NakshaError].
     * @since 3.0
     */
    constructor(error: NakshaError)

    /**
     * Create an exception based upon individual values, which will be assembled to an [NakshaError].
     * @param code the error code, put into [NakshaError.code].
     * @param msg the human-readable error message, put into [message] and into [NakshaError.msg].
     */
    constructor(code: String, msg: String)

    /**
     * Create an exception based upon individual values, which will be assembled to an [NakshaError].
     * @param code the error code, put into [NakshaError.code].
     * @param msg the human-readable error message, put into [message] and into [NakshaError.msg].
     * @param cause the optional cause of this error, put into [Exception.cause].
     */
    constructor(code: String, msg: String, cause: Throwable? = null)
}

/**
 * Create [UNINITIALIZED] exception.
 * @param msg the message.
 * @return the [NakshaException].
 * @since 3.0
 */
fun uninitialized(msg: String): NakshaException = NakshaException(UNINITIALIZED, msg)

/**
 * Create [ILLEGAL_ID] exception.
 * @param msg the message.
 * @return the [NakshaException].
 * @since 3.0
 */
fun illegalId(msg: String): NakshaException = NakshaException(ILLEGAL_ID, msg)

/**
 * Create [ILLEGAL_ARGUMENT] exception.
 * @param msg the message.
 * @param cause the reason for the exception, if being another exception.
 * @return the [NakshaException].
 * @since 3.0
 */
fun illegalArg(msg: String, cause: Exception? = null): NakshaException = NakshaException(ILLEGAL_ARGUMENT, msg, cause)

/**
 * Create [ILLEGAL_STATE] exception.
 * @param msg the message.
 * @return the [NakshaException].
 * @since 3.0
 */
fun illegalState(msg: String): NakshaException = NakshaException(ILLEGAL_STATE, msg)

/**
 * Create [ILLEGAL_STATE] exception.
 * @param msg the message.
 * @param reason the exception that caused this exception.
 * @return the [NakshaException].
 * @since 3.0
 */
fun illegalState(msg: String, reason: Exception): NakshaException = NakshaException(ILLEGAL_STATE, msg, reason)

/**
 * Create [INTERNAL_ERROR] exception.
 * @param msg the message.
 * @param reason the exception that caused this exception, if nay.
 * @return the [NakshaException].
 * @since 3.0
 */
@JvmOverloads
fun internalError(msg: String, reason: Exception? = null): NakshaException = NakshaException(INTERNAL_ERROR, msg, reason)

/**
 * Create [FORBIDDEN] exception.
 * @param msg the message.
 * @return the [NakshaException].
 * @since 3.0
 */
fun forbidden(msg: String): NakshaException = NakshaException(FORBIDDEN, msg)

/**
 * Create [EXCEPTION] exception.
 * @param msg the message.
 * @param cause the cause, if any.
 * @return the [NakshaException].
 * @since 3.0
 */
@JvmOverloads
fun generalException(msg: String, cause: Throwable? = null): NakshaException = NakshaException(EXCEPTION, msg, cause)

/**
 * Create [MAP_NOT_FOUND] exception.
 * @param msg the message.
 * @return the [NakshaException].
 * @since 3.0
 */
fun mapNotFound(msg: String): NakshaException = NakshaException(MAP_NOT_FOUND, msg)

/**
 * Create [CATALOG_EXISTS] exception.
 * @param msg the message.
 * @return the [NakshaException].
 * @since 3.0
 */
fun catalogExists(msg: String): NakshaException = NakshaException(CATALOG_EXISTS, msg)

/**
 * Create [COLLECTION_NOT_FOUND] exception.
 * @param msg the message.
 * @return the [NakshaException].
 * @since 3.0
 */
fun collectionNotFound(msg: String): NakshaException = NakshaException(COLLECTION_NOT_FOUND, msg)

/**
 * Create [COLLECTION_EXISTS] exception
 * @param msg the message.
 * @return the [NakshaException].
 * @since 3.0
 */
fun collectionExists(msg: String): NakshaException = NakshaException(COLLECTION_EXISTS, msg)

/**
 * Create [FEATURE_NOT_FOUND] exception.
 * @param msg the message.
 * @return the [NakshaException].
 * @since 3.0
 */
fun featureNotFound(msg: String): NakshaException = NakshaException(FEATURE_NOT_FOUND, msg)

/**
 * Create [FEATURE_EXISTS] exception.
 * @param msg the message.
 * @return the [NakshaException].
 * @since 3.0
 */
fun featureExists(msg: String): NakshaException = NakshaException(FEATURE_EXISTS, msg)

/**
 * Create [CONFLICT] exception
 * @param msg the message.
 * @return the [NakshaException].
 * @since 3.0
 */
fun conflict(msg: String): NakshaException = NakshaException(CONFLICT, msg)

/**
 * Create [UNSUPPORTED_OPERATION] exception
 * @param msg the message.
 * @return the [NakshaException].
 * @since 3.0
 */
fun unsupportedOp(msg: String): NakshaException = NakshaException(UNSUPPORTED_OPERATION, msg)
