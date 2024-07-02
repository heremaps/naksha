@file:Suppress("OPT_IN_USAGE")

package naksha.geo

import naksha.base.NotNullProperty
import naksha.base.NullableProperty
import naksha.base.ObjectProxy
import kotlin.js.JsExport

/**
 * The data mode for a GeoJSON feature.
 */
@JsExport
open class GeoFeatureProxy : ObjectProxy() {

    companion object {
        private val ID = NotNullProperty<Any, GeoFeatureProxy, String>(String::class)
        private val BBOX = NullableProperty<Any, GeoFeatureProxy, BoundingBoxProxy>(BoundingBoxProxy::class)
    }

    /**
     * The unique identifier of the feature.
     */
    var id by ID

    /**
     * The bounding box.
     */
    var bbox by BBOX

    /**
     * Calculate the bounding box from the geometry and updated the [bbox] property.
     */
    fun updateBoundingBox(): BoundingBoxProxy {
        TODO("GeoFeature::updateBoundingBox is not yet implemented")
    }

    /**
     * The geometry of the feature.
     */
    fun getGeometry(): GeometryProxy {
        TODO("")
    }
}
