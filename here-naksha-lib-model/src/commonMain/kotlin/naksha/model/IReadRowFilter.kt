package naksha.model

import naksha.model.request.ResultRow
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@OptIn(ExperimentalJsExport::class)
@JsExport
interface IReadRowFilter {

    fun filterRow(row: ResultRow): ResultRow
}