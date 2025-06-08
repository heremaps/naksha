@file:Suppress("OPT_IN_USAGE")

package naksha.geo

import naksha.base.*
import naksha.base.Platform.PlatformCompanion.forInstance
import naksha.base.Platform.PlatformCompanion.forKClass
import naksha.base.PlatformMapApi.PlatformMapApiCompanion.map_get
import naksha.base.PlatformMapApi.PlatformMapApiCompanion.map_set
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

    init {
        initTypeAndId()
    }

    /**
     * If this is a [MOM](https://www.here.com/learn/blog/unimap-map-object-model) type.
     * @since 3.0
     */
    protected open fun isMomType(): Boolean = false

    /**
     * If this is an old Data-Hub type.
     * @since 3.0
     */
    protected open fun isDataHubType(): Boolean = false

    /**
     * Automatically invoked by the constructor of [GeoFeature].
     *
     * The default implementation checks the [PlatformType.jsonType] of this instance, it any is available:
     * - If [isMomType], set [momType] and `properties.featureType` to [PlatformType.jsonType].
     * - If [isDataHubType], set [featureType] and `properties.featureType` to [PlatformType.jsonType].
     * - Otherwise, sets [featureType] to [PlatformType.jsonType].
     * @since 3.0
     */
    protected fun initTypeAndId() {
        val po = platformObject()
        map_set(po, "type", FEATURE)
        map_set(po, "id", randomString())
        val jsonType = forInstance(this).jsonType
        if (jsonType != null && jsonType != FEATURE) {
            if (isMomType()) setMomType(jsonType)
            else if (isDataHubType()) setFeatureType(jsonType, true)
            else setFeatureType(jsonType, false)
        }
    }

    /**
     * Should only be called from constructors, initializes the feature-type.
     * @param featureType The value to set.
     * @param set_properties if _true_, copies the [featureType] into `properties.featureType`.
     * @since 3.0
     */
    private fun setFeatureType(featureType: String, set_properties: Boolean) {
        val po = platformObject()
        map_set(po, "type", FEATURE)
        map_set(po, "featureType", featureType)
        if (set_properties) {
            var properties = map_get(po, "properties")
            if (properties !is PlatformMap) {
                properties = Platform.newMap()
                map_set(po, "properties", properties)
            }
            map_set(properties, "featureType" ,featureType)
        }
    }

    /**
     * Should only be called from constructors, initializes [momType] and `properties.featureType`.
     * @param momType The [MOM](https://www.here.com/learn/blog/unimap-map-object-model)-type.
     * @since 3.0
     */
    private fun setMomType(momType: String) {
        val po = platformObject()
        map_set(po, "type", FEATURE)
        map_set(po, "momType", momType)
        var properties = map_get(po, "properties")
        if (properties !is PlatformMap) {
            properties = Platform.newMap()
            map_set(po, "properties", properties)
        }
        map_set(properties, "featureType", momType)
    }

    companion object GeoFeatureCompanion {
        /**
         * The [PlatformType] of [GeoFeature].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE = forKClass(GeoFeature::class)
            .withPackageName(PACKAGE_NAME)
            .withJsonType(FEATURE)

        private val ID_MEMBER = NotNullProperty<GeoFeature, String>(String_TYPE) { _, _ -> randomString() }
        private val TYPE_MEMBER = NotNullProperty<GeoFeature, String>(String_TYPE) { _, _ -> FEATURE }
        private val BBOX_NULL_MEMBER = NullableProperty<GeoFeature, BBox>(BBox.TYPE)

        init {
            initialize()
        }
    }

    /**
     * The unique identifier of the feature.
     * @since 3.0
     */
    var id: String by ID_MEMBER

    open fun withId(id: String): GeoFeature {
        this.id = id
        return this
    }

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
     * @since 3.0
     */
    open var geometry: SpGeometry
        get() = SpGeometry.forValue(getRaw("geometry"))
        set(value) { set("geometry", value) }

    open fun withGeometry(geometry: SpGeometry): GeoFeature {
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
        if (properties == null) {
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

    /**
     * The type of the feature, reads only `type` property.
     * @since 3.0
     */
    val type: String by TYPE_MEMBER

    /**
     * The feature-type of the feature, custom [HERE Technologies](https://here.com) extension, normally returns [PlatformType.jsonType].
     *
     * Checks:
     * - `momType` _(if [isMomType])_
     * - `properties.featureType` _(if [isMomType] or [isDataHubType])_
     * - `featureType`
     * - `type`
     * @since 3.0
     */
    val featureType: String
        get() = momType

    /**
     * The mom-type of the feature, custom [HERE Technologies](https://here.com) extension.
     *
     * Checks:
     * - `momType` _(if [isMomType])_
     * - `properties.featureType` _(if [isMomType] or [isDataHubType])_
     * - `featureType`
     * - `type`
     * @since 3.0
     */
    val momType: String
        get() {
            val po = platformObject()
            var type: Any?

            // momType
            if (isMomType()) {
                type = map_get(po, "momType")
                if (type is String) return type
            }

            if (isMomType() || isDataHubType()) {
                // properties.featureType
                val properties = map_get(po, "properties")
                if (properties is PlatformMap) {
                    type = map_get(properties, "featureType")
                    if (type is String) return type
                }
            }

            // featureType
            type = map_get(po, "featureType")
            if (type is String) return type

            // type
            type = map_get(po, "type")
            if (type is String) return type

            // Eventually, everything is a "Feature"
            return FEATURE
        }
}
