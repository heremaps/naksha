@file:Suppress("OPT_IN_USAGE")

package naksha.geo

import naksha.base.NotNullProperty
import naksha.base.NullableProperty
import naksha.base.P_Object
import kotlin.js.JsExport

/**
 *
 */
@JsExport
open class GeoFeature : P_Object() {

    companion object {
        private val ID = NotNullProperty<Any, GeoFeature, String>(String::class)
        private val PROPERTIES =
            NotNullProperty<Any, GeoFeature, P_Object>(P_Object::class, defaultValue = { P_Object() })
        private val GEOMETRY =
            NullableProperty<Any, GeoFeature, GeometryProxy>(GeometryProxy::class)
    }

    var id by ID
    var properties by PROPERTIES
    var geometry by GEOMETRY
}