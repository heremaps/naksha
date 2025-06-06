@file:Suppress("OPT_IN_USAGE")

package naksha.geo

import naksha.base.*
import kotlin.js.JsExport

/**
 * The [GeoJSON feature](https://datatracker.ietf.org/doc/html/rfc7946#section-3.2).
 * @since 3.0
 */
@JsExport
open class GeoFeature : AnyObject() {

    companion object GeoFeatureProxyCompanion {
        private val ID = NotNullProperty<GeoFeature, String>(String_TYPE) { _, _ -> Platform.util.randomString(12) }
        private val TYPE = NotNullProperty<GeoFeature, String>(String_TYPE) { self, _ -> self.defaultFeatureType() }
        private val BBOX_NULL = NullableProperty<GeoFeature, GeoBoundingBox>(GeoBoundingBox.TYPE)
        private val GEOMETRY_NULL = NotNullProperty<GeoFeature, SpGeometry>(SpGeometry.TYPE) { _, _ ->
            throw IllegalStateException("geometry is null")
        }
    }

    /**
     * The default type to set, when the type is _null_.
     */
    protected open fun defaultFeatureType(): String = "Feature"

    /**
     * The unique identifier of the feature.
     */
    open var id by ID

    /**
     * The bounding box.
     */
    open var bbox by BBOX_NULL

    /**
     * The geometry of the feature.
     */
    open var geometry by GEOMETRY_NULL

    /**
     * The type of the feature.
     */
    open var type by TYPE

    /**
     * Calculate the bounding box from the geometry and updated the [bbox] property.
     */
    open fun updateBoundingBox(): GeoBoundingBox {
        TODO("GeoFeature::updateBoundingBox is not yet implemented")
    }
}
