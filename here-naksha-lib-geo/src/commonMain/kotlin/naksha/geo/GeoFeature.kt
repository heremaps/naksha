@file:Suppress("OPT_IN_USAGE")

package naksha.geo

import naksha.base.NotNullProperty
import naksha.base.NullableProperty
import naksha.base.ObjectProxy
import kotlin.js.JsExport

/**
 *
 */
@JsExport
open class GeoFeature : ObjectProxy() {

    companion object {
        private val ID = NotNullProperty<Any, GeoFeature, String>(String::class)
        private val PROPERTIES =
            NotNullProperty<Any, GeoFeature, ObjectProxy>(ObjectProxy::class, defaultValue = { ObjectProxy() })
        private val GEOMETRY =
            NullableProperty<Any, GeoFeature, GeometryProxy>(GeometryProxy::class)
    }

    var id by ID
    var properties by PROPERTIES
    var geometry by GEOMETRY
}
