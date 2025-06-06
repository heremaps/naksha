@file:Suppress("OPT_IN_USAGE")

package naksha.geo

import naksha.base.ListProxy
import kotlin.js.JsExport

@JsExport
open class GeoFeatureList : ListProxy<GeoFeature>(GeoFeature::class) {
}