@file:Suppress("OPT_IN_USAGE")

package naksha.model

import kotlin.js.JsExport

/**
 * An storage exception.
 * @property error the error that happened.
 */
@JsExport
open class StorageException(val error: NakshaError) : Exception(error.message, error.exception)
