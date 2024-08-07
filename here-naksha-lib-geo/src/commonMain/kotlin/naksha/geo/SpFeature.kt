@file:Suppress("OPT_IN_USAGE")

package naksha.geo

import naksha.base.NotNullProperty
import naksha.base.NullableProperty
import naksha.base.AnyObject
import naksha.base.PlatformUtil
import kotlin.js.JsExport

/**
 * The GeoJSON feature.
 */
@JsExport
open class SpFeature : AnyObject() {

    companion object GeoFeatureProxyCompanion {
        private val ID = NotNullProperty<SpFeature, String>(String::class) { _, _ -> PlatformUtil.randomString(12) }
        private val TYPE = NotNullProperty<SpFeature, String>(String::class) { self, _ -> self.defaultFeatureType() }
        private val BBOX_NULL = NullableProperty<SpFeature, SpBoundingBox>(SpBoundingBox::class)
        private val GEOMETRY_NULL = NotNullProperty<SpFeature, SpGeometry>(SpGeometry::class) { _, _ ->
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
    open fun updateBoundingBox(): SpBoundingBox {
        TODO("GeoFeature::updateBoundingBox is not yet implemented")
    }
}
