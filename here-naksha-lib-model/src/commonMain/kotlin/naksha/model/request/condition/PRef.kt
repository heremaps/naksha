package naksha.model.request.condition

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/**
 * All property operations.
 */
@OptIn(ExperimentalJsExport::class)
@JsExport
enum class PRef {
    ID, APP_ID, AUTHOR, UID, GRID, TXN, TXN_NEXT, TAGS
}