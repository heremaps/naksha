@file:Suppress("OPT_IN_USAGE")

package naksha.geo

import naksha.base.*
import naksha.base.Platform.Platform_C.forKClass
import naksha.base.PlatformMapApi.PlatformMapApi_C.map_get
import naksha.base.PlatformMapApi.PlatformMapApi_C.map_set
import naksha.base.bugs.KT_68775_infinite_loop_for_calling_super_getter
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
        val TYPE = forKClass(GeoFeature::class)
            .withPackageName(PACKAGE_NAME)
            .withJsonType(FEATURE)
            .withIsFeature(true)

        private val BBOX_NULL_MEMBER = NullableProperty<GeoFeature, BBox>(BBox.TYPE)

        init {
            initialize()
        }
    }

    // We know, it will always be at least "Feature"
    override val type: String
        get() = get_type() ?: FEATURE

    /**
     * The bounding box.
     * @since 3.0
     */
    var bbox: BBox? by BBOX_NULL_MEMBER

    /**
     * Calculate the bounding box from the geometry.
     *
     * Can be used to update the [bbox] property, Kotlin example:
     * ```kotlin
     * val geo = GeoFeature().apply {
     *   id = "demo"
     *   geometry = someGeometry
     *   bbox = calculateBBox()
     * }
     * ```
     * Java example:
     * ```java
     * import static naksha.base.Platform.apply;
     * final var geo = apply(new GeoFeature(), (self)-> {
     *   self.setId("demo");
     *   self.setGeometry(someGeometry);
     *   self.setBBox( self.calculateBBox() );
     * });
     * ```
     * @return the calculated and set bounding box.
     * @since 3.0
     */
    open fun calculateBBox(): BBox = BBox(geometry)

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

    /**
     * The properties of the feature.
     * @since 3.0
     * @see setProperties
     * @see getProperties
     */
    @KT_68775_infinite_loop_for_calling_super_getter
    open val properties: AnyObject
        get() = getProperties(AnyObject.TYPE)

    /**
     * Read properties in a specific type.
     * @param type the type that should be returned.
     * @return the properties.
     */
    @KT_68775_infinite_loop_for_calling_super_getter
    fun <T : MapProxy<String,*>> getProperties(type: PlatformType<out T>): T {
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
     * @see properties
     */
    open fun setProperties(properties: AnyObject) {
        set("properties", unbox(properties))
    }

}
