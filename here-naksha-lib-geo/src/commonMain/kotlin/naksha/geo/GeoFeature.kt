@file:Suppress("OPT_IN_USAGE")

package naksha.geo

import naksha.base.*
import naksha.base.Platform.PlatformCompanion.forInstance
import naksha.base.Platform.PlatformCompanion.forKClass
import naksha.base.PlatformUtil.PlatformUtilCompanion.randomString
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

/**
 * The [GeoJSON feature](https://datatracker.ietf.org/doc/html/rfc7946#section-3.2).
 * @since 3.0
 */
@JsExport
open class GeoFeature : AnyObject() {

    companion object GeoFeatureCompanion {
        /**
         * The [PlatformType] of [GeoFeature].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE: PlatformType<GeoFeature> = forKClass(GeoFeature::class)
            .withPackageName(PACKAGE_NAME)
            .withJsonType("Feature")

        private val ID_MEMBER = NotNullProperty<GeoFeature, String>(String_TYPE) { _, _ -> randomString() }
        private val TYPE_MEMBER = NotNullProperty<GeoFeature, String>(String_TYPE) { self, _ -> forInstance(self).jsonType }
        private val BBOX_NULL_MEMBER = NullableProperty<GeoFeature, BBox>(BBox.TYPE)
    }

    /**
     * The unique identifier of the feature.
     * @since 3.0
     */
    open var id: String by ID_MEMBER

    /**
     * The bounding box.
     * @since 3.0
     */
    open var bbox: BBox? by BBOX_NULL_MEMBER

    /**
     * The geometry of the feature.
     * @since 3.0
     */
    open var geometry: SpGeometry
        get() = SpGeometry.forValue(getRaw("geometry"))
        set(value) { set("geometry", value) }

    /**
     * The type of the feature.
     * @since 3.0
     */
    open var type by TYPE_MEMBER

    /**
     * Calculate the bounding box from the geometry and updated the [bbox] property.
     * @return this.
     * @since 3.0
     */
    open fun updateBoundingBox(): GeoFeature {
        this.bbox = BBox(this.geometry)
        return this
    }
}
