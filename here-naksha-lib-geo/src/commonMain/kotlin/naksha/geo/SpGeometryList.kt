package naksha.geo

import naksha.base.PTypedArray
import kotlin.js.JsExport

@Suppress("OPT_IN_USAGE")
@JsExport
class SpGeometryList : PTypedArray<SpGeometry>(SpGeometry::class)