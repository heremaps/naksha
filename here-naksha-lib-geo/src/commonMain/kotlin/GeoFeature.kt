@file:Suppress("OPT_IN_USAGE")

package naksha.geo

import naksha.base.NotNullProperty
import naksha.base.P_Object
import kotlin.js.JsExport

/**
 *
 */
@JsExport
open class GeoFeature : P_Object() {
    var id: String by NotNullProperty(String::class)
    var properties: P_Object by NotNullProperty(P_Object::class)
}