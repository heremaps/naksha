package naksha.model

/**
 * A Naksha exception.
 * @property error the error that happened.
 * @since 3.0.0
 */
@JsExport
actual class NakshaException actual constructor(actual val error: NakshaError) : RuntimeException(error.msg, error.cause) {
    /**
     * Secondary constructor to directly
     */
    @JsName("of")
    actual constructor(code: String, msg: String, id: String?, cause: Throwable?) : this(NakshaError(code, msg, id, cause))

}