
package naksha.model

/**
 * A Naksha exception.
 * @property error the error that happened.
 * @since 3.0.0
 */
actual class NakshaException actual constructor(
    /**
     * The [NakshaError] that causes this exception.
     * @since 3.0.0
     */
    actual val error: NakshaError
) : RuntimeException(error.msg, error.cause) {

    /**
     * Secondary constructor to directly
     */
    actual constructor(code: String, msg: String, id: String?, cause: Throwable?) : this(NakshaError(code, msg, id, cause))
}