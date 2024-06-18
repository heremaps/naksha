@file:OptIn(ExperimentalJsExport::class)

package naksha.jbon

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/**
 * A thread local reader for a storage row. Note that the reader is not thread-safe, only the row data is.
 */
@JsExport
@Deprecated("Please use new class from lib-model", level = DeprecationLevel.WARNING)
class NkStorageRowReader {
}