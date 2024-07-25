package naksha.geo

import naksha.base.ListProxy
import kotlin.js.JsExport

@Suppress("OPT_IN_USAGE")
@JsExport
class GeometriesProxy : ListProxy<GeometryProxy>(GeometryProxy::class) {
}