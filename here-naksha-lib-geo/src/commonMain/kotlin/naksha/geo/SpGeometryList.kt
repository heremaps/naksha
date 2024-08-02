package naksha.geo

import naksha.base.ListProxy
import kotlin.js.JsExport

@Suppress("OPT_IN_USAGE")
@JsExport
class SpGeometryList : ListProxy<SpGeometry>(SpGeometry::class)