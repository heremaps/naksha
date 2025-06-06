@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package naksha.base

/**
 * A generalized exception used everywhere within the Naksha framework.
 *
 * Having a generalized exception class makes error handling more easy, especially within services, microservices, and in the cloud. This allows not only to pass errors upwards, and when being unhandled, to return the original error back to the client, but as the [NakshaError] class is a standard JSON object, it allows to serialize errors, and pass them easy through the whole service chain, even while some services them self may not be aware of the specific error-type. In other words, it allows better remote error handling.
 *
 * This allows applications and client to generate own error-types, but the try-catch flow becomes much simpler, by all exceptions being runtime exceptions. In other words, if your method can't handle downstream errors, just don't do it. Someone upstream will take care, if not, eventually the application or service should log the error and stack-trace, and report a generalized error message back to the client.
 * @property error the error that happened.
 * @since 3.0
 */
expect class NakshaException : RuntimeException {
    companion object NakshaExceptionCompanion {
        /**
         * The [PlatformType] of [NakshaException].
         * @since 3.0
         */
        val TYPE: PlatformType<NakshaException>
    }

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
     * @param msg the human-readable error message, put into [Exception.message] and into [NakshaError.msg].
     */
    constructor(code: String, msg: String)

    /**
     * Create an exception based upon individual values, which will be assembled to an [NakshaError].
     * @param code the error code, put into [NakshaError.code].
     * @param msg the human-readable error message, put into [Exception.message] and into [NakshaError.msg].
     * @param streamId the stream-id of this error, put into [NakshaError.streamId]
     */
    constructor(code: String, msg: String, streamId: String)

    /**
     * Create an exception based upon individual values, which will be assembled to an [NakshaError].
     * @param code the error code, put into [NakshaError.code].
     * @param msg the human-readable error message, put into [Exception.message] and into [NakshaError.msg].
     * @param cause the cause of this error, put into [Exception.cause].
     */
    constructor(code: String, msg: String, cause: Throwable)

    /**
     * Create an exception based upon individual values, which will be assembled to an [NakshaError].
     * @param code the error code, put into [NakshaError.code].
     * @param msg the human-readable error message, put into [Exception.message] and into [NakshaError.msg].
     * @param cause the optional cause of this error, put into [Exception.cause].
     * @param streamId the optional stream-id of this error, put into [NakshaError.streamId]
     */
    constructor(code: String, msg: String, cause: Throwable? = null, streamId: String? = null)
}