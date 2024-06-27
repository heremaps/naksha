package naksha.model

import kotlin.js.JsExport

/**
 * When any
 */
@Suppress("OPT_IN_USAGE")
@JsExport
open class StorageException(message: String? = null, cause: Throwable? = null) : Exception(message, cause)
