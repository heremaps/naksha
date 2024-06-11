package naksha.model

import com.here.naksha.lib.naksha.request.ResultRow
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@OptIn(ExperimentalJsExport::class)
@JsExport
interface IReadRowFilter {

    fun filterRow(row: ResultRow): ResultRow
}