@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package naksha.model

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
     * @param msg the human-readable error message, put into [Exception.message] and into [NakshaError.msg].
     */
    constructor(code: String, msg: String)

    /**
     * Create an exception based upon individual values, which will be assembled to an [NakshaError].
     * @param code the error code, put into [NakshaError.code].
     * @param msg the human-readable error message, put into [Exception.message] and into [NakshaError.msg].
     * @param cause the optional cause of this error, put into [Exception.cause].
     */
    constructor(code: String, msg: String, cause: Throwable? = null)
}