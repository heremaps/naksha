package naksha.model

import naksha.base.JsEnum
import kotlin.js.JsExport

/**
 * An storage exception.
 * @param msg the exception message.
 * @param cause the optional cause.
 * @param code the error-code, as defined in [StorageError]
 * @param err the error, as defined in [StorageError]
 */
@Suppress("OPT_IN_USAGE")
@JsExport
open class StorageException(
    msg: String? = null,
    cause: Throwable? = null,
    code: Int? = null,
    err: StorageError? = if (code != null) JsEnum.get(code, StorageError::class) else null
) : Exception(msg ?: err?.defaultMessage, cause) {

    /**
     * The error code, matches to [StorageError.code].
     */
    val code: Int = err?.code ?: code ?: 0

    /**
     * The error constant.
     */
    val error: StorageError
        get() = JsEnum.get(code, StorageError::class)

}