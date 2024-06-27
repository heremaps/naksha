package naksha.psql
import kotlin.js.JsExport

@JsExport
data class Param(val idx: Int, val type: String, val value: Any?)