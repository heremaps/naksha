@file:Suppress("OPT_IN_USAGE")

package naksha.geo

import naksha.base.*
import naksha.base.Platform.Platform_C.forKClass
import naksha.base.Platform.Platform_C.isPlatformObject
import naksha.base.PlatformMapApi.PlatformMapApi_C.map_get
import naksha.base.PlatformMapApi.PlatformMapApi_C.map_set
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
 *   val foo = fromJson(json) as Foo
 * }
 * ```
 * @since 3.0
 * @see GeoTypeDetector
 */
@JsExport
open class GeoFeature : AnyTypedIdObject() {

    companion object GeoFeature_C {
        /**
         * The [PlatformType] of [GeoFeature].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE = forKClass(GeoFeature::class).withPackageName(PACKAGE_NAME)

        private val BBOX_NULL_MEMBER = NullableProperty<GeoFeature, BBox>(BBox.TYPE)

        init {
            initialize()
        }
    }

    override fun isFeature(): Boolean = true

    override fun withType(type: String?): GeoFeature = super.withType(type) as GeoFeature
    override fun withId(id: String): GeoFeature = super.withId(id) as GeoFeature

    // We know, it will always be at least "Feature"
    override val type: String
        get() = type_get() ?: FEATURE

    /**
     * The bounding box.
     * @since 3.0
     */
    var bbox: BBox? by BBOX_NULL_MEMBER

    open fun withBBox(bbox: BBox): GeoFeature {
        this.bbox = bbox
        return this
    }

    /**
     * Calculate the bounding box from the geometry and updated the [bbox] property.
     *
     * Example:
     * ```kotlin
     * val geo: GeoFeature = GeoFeature()
     *     .withId("demo")
     *     .withGeometry(geometry)
     *     .withAutoBBox()
     * ```
     * @return this.
     * @since 3.0
     */
    open fun withAutoBBox(): GeoFeature {
        this.bbox = BBox(geometry)
        return this
    }

    /**
     * The geometry of the feature.
     *
     * If set to `null`, removes the `geometry` property.
     * @since 3.0
     */
    open var geometry: SpGeometry?
        get() {
            val raw = getRaw("geometry")
            return if (raw != null) SpGeometry.forValue(raw) else null
        }
        set(value) {
            if (value == null) {
                removeRaw("geometry")
            } else {
                set("geometry", value)
            }
        }

    open fun withGeometry(geometry: SpGeometry?): GeoFeature {
        this.geometry = geometry
        return this
    }

    /**
     * The properties of the feature.
     * @since 3.0
     */
    open val properties: AnyObject
        get() = get_properties(AnyObject.TYPE)

    /**
     * Internal method to read properties.
     * @param type The type that should be returned.
     * @return the properties.
     */
    protected fun <T : MapProxy<String,*>> get_properties(type: PlatformType<out T>): T {
        val po = platformObject()
        var properties = map_get(po, "properties")
        if (properties !is PlatformMap) {
            properties = Platform.newMap()
            map_set(po, "properties", properties)
        }
        return type.proxy(properties)
    }

    /**
     * Set the [properties].
     * @param properties The properties to set.
     * @return this.
     * @see properties
     */
    open fun withProperties(properties: AnyObject): GeoFeature {
        setRaw("properties", unbox(properties))
        return this
    }

}
