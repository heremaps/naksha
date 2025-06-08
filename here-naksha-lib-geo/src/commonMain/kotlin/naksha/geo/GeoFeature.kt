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
 *
 * To be [GeoJSON](https://datatracker.ietf.org/doc/html/rfc7946) compatible, only one of the following two values are allowed in the GeoJSON feature root:
 * - `FeatureCollection`
 * - `Feature`
 *
 * For the `geometry` only the types `Point`, `LineString`, `MultiPoint`, `Polygon`, `MultiLineString`, `MultiPolygon`, and `GeometryCollection` are allowed. Other limits apply, see [GeoJSON specification, section 7](https://datatracker.ietf.org/doc/html/rfc7946#section-7).
 *
 * Long before [RFC-7946](https://datatracker.ietf.org/doc/html/rfc7946#section-7), [HERE Technologies](https://here.com) introduced a custom type property named `featureType`, located in the `properties` of GeoJSON features, used by internal services. At that time, there was no formal GeoJSON standard. Later the [MOM specification](https://www.here.com/learn/blog/unimap-map-object-model) appeared, and the governance board decided to deprecate the `properties.featureType`, and to relocate the `type` information into the GeoJSON feature, naming it into `momType`.
 *
 * Naksha team decided to support, as good as possible, the old style `properties.featureType`, the new style `momType`, and the standard way of storing type information, with `type` property used as discriminator. While Naksha supports the old and new [HERE Technologies](https://here.com) type locations, it works best with the standard way of storing the type in the `type` property, and comes with corresponding first class support.
 *
 * All features with `type` being `Feature` are parsed into [GeoFeature], `FeatureCollection` should be parsed automatically into a [GeoCollection], `Point` into [SpPoint], and so on.
 *
 * As Naksha is based upon [Duck Typing](https://en.wikipedia.org/wiki/Duck_typing), and the `lib-geo` automatically registers a [GeoTypeDetector] with [Platform.globalDetectors], so that all types extending [GeoFeature] or [GeoCollection], and which have a `momType`, `featureType`, or `properties.featureType` are detected as expected.
 *
 * Example:
 * ```kotlin
 * class Foo : GeoFeature() {
 *   companion object Foo_C {
 *     @JvmField
 *     val TYPE = forKClass(Foo::class).withJsonType("foo")
 *   }
 * }
 * fun demo() {
 *   val json = """{
 *     "id": "demo",
 *     "type": "Feature",
 *     "featureType": "foo"
 *   }"""
 *   val foo = fromJson(json)
 *   // foo should be of type `Foo`
 * }
 * ```
 * @since 3.0
 * @see GeoTypeDetector
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

        init {
            initialize()
        }
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
