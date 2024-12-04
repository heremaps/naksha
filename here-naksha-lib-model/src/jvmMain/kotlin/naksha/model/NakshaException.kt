package naksha.model

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
actual open class NakshaException(@JvmField val error: NakshaError) : RuntimeException(error.msg, error.cause) {
    /**
     * Create an exception with error details individually specified.
     * @param code the error code.
     * @param msg the human-readable error message.
     * @param id the optional identifier related to the error; if any.
     * @param cause the cause (exception) of this error; if any.
     * @since 3.0.0
     */
    constructor(code: String, msg: String, id: String? = null, cause: Throwable? = null) : this(NakshaError(code, msg, id, cause))
}