package naksha.model.request.condition

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/**
 * All property operations.
 */
@OptIn(ExperimentalJsExport::class)
@JsExport
sealed class PRef {
    data object ID: PRef()
    data object APP_ID: PRef()
    data object AUTHOR: PRef()
    data object UID: PRef()
    data object GRID: PRef()
    data object TXN: PRef()
    data object TXN_NEXT: PRef()
    data object TAGS: PRef()
    class NON_INDEXED_PREF(vararg val path: String): PRef()
}