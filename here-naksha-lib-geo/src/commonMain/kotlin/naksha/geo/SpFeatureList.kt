@file:Suppress("OPT_IN_USAGE")

package naksha.geo

import naksha.base.PTypedArray
import kotlin.js.JsExport

@JsExport
class SpFeatureList : PTypedArray<SpFeature>(SpFeature::class) {
}