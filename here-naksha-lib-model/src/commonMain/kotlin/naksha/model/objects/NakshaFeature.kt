package naksha.model.objects

import naksha.base.*
import naksha.geo.SpBoundingBox
import naksha.geo.SpFeature
import naksha.geo.SpGeometry
import naksha.geo.SpPoint
import naksha.model.NakshaError.NakshaErrorCompanion.ILLEGAL_ARGUMENT
import naksha.model.NakshaException
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.js.JsStatic
import kotlin.jvm.JvmStatic

/**
 * The Naksha Feature extending the default [SpFeature].
 * @since 3.0.0
 */
@Suppress("OPT_IN_USAGE")
@JsExport
open class NakshaFeature() : AnyObject() {

    /**
     * Create a new feature with the given ID.
     * @param id the identifier to set.
     * @since 3.0.0
     */
    @JsName("of")
    constructor(id: String?) : this() {
        setRaw("id", id)
        @Suppress("LeakingThis")
        setRaw("type", defaultType())
    }

    companion object NakshaFeature_C {
        /**
         * The feature-type of this feature itself.
         * @since 3.0.0
         */
        const val TYPE = "Feature"

        /**
         * The key of geometry (`geometry`).
         * @since 3.0.0
         */
        const val GEOMETRY = "geometry"

        /**
         * Read the feature from a JSON string.
         * @return the [NakshaFeature] deserialized from the given JSON.
         * @since 3.0.0
         */
        @JvmStatic
        @JsStatic
        fun fromJson(json: String): NakshaFeature {
            val raw = Platform.fromJSON(json)
            if (raw !is PlatformMap) throw NakshaException(ILLEGAL_ARGUMENT, "The given JSON is no object")
            return raw.proxy(NakshaFeature::class)
        }

        private val ID_RANDOM = NotNullProperty<NakshaFeature, String>(String::class) { _, _ -> PlatformUtil.randomString(12) }
        private val TYPE_DEFAULT = NotNullProperty<NakshaFeature, String>(String::class) { self, _ -> self.defaultType() }
        private val BBOX_NULL = NullableProperty<NakshaFeature, SpBoundingBox>(SpBoundingBox::class)
        private val GEOMETRY_NULL = NullableProperty<NakshaFeature, SpGeometry>(SpGeometry::class)
        private val REFERENCE_POINT_NULL = NullableProperty<NakshaFeature, SpPoint>(SpPoint::class)
        private val PROPERTIES = NotNullProperty<NakshaFeature, NakshaProperties>(NakshaProperties::class)
        private val ATTACHMENT_NULL = NullableProperty<NakshaFeature, ByteArray>(ByteArray::class)
        private val STRING_NULL = NullableProperty<NakshaFeature, String>(String::class)
    }

    /**
     * The default type.
     * @since 3.0.0
     */
    protected open fun defaultType(): String = TYPE

    /**
     * The default feature-type; if any.
     * @since 3.0.0
     */
    protected open fun defaultFeatureType(): String? = null

    /**
     * The unique identifier of the feature.
     * @since 3.0.0
     */
    open var id by ID_RANDOM

    /**
     * @see id
     */
    open fun withId(value: String): NakshaFeature {
        id = value
        return this
    }

    /**
     * The type of the feature, must be one of: `FeatureCollection`, `Feature`, `Point`, `LineString`, `MultiPoint`, `Polygon`, `MultiLineString`, `MultiPolygon`, and `GeometryCollection`. Beware, no other values are allowed in the [GeoJSON specification section 7](https://datatracker.ietf.org/doc/html/rfc7946#section-7), therefore we introduce a [customer feature-type][featureType], that is stored in [properties].
     * @since 3.0.0
     */
    var type by TYPE_DEFAULT

    /**
     * @see type
     */
    open fun withType(value: String): NakshaFeature {
        type = value
        return this
    }

    /**
     * The custom feature-type; reads [properties.featureType][NakshaProperties.featureType], then [momType], and eventually [type]. Modifications will change [properties.featureType][NakshaProperties.featureType].
     *
     * If [MOM](https://www.here.com/learn/blog/unimap-map-object-model) should be used, please rather access [momType].
     * @since 3.0.0
     */
    var featureType: String
        get() = properties.featureType ?: momType
        set(value) {
            properties.featureType = value
        }

    /**
     * @see featureType
     */
    open fun withFeatureType(value: String): NakshaFeature {
        featureType = value
        return this
    }

    /**
     * The bounding box; if the feature has any.
     * @since 3.0.0
     */
    var bbox by BBOX_NULL

    /**
     * @see bbox
     */
    open fun withBbox(value: SpBoundingBox?): NakshaFeature {
        bbox = value
        return this
    }

    /**
     * The geometry of the feature, if it has any.
     * @since 3.0.0
     */
    var geometry by GEOMETRY_NULL

    /**
     * @see geometry
     */
    open fun withGeometry(value: SpGeometry?): NakshaFeature {
        geometry = value
        return this
    }

    /**
     * Reference point of the feature. Used for grid calculation.
     * @since 3.0.0
     */
    var referencePoint by REFERENCE_POINT_NULL

    /**
     * @see referencePoint
     */
    open fun withReferencePoint(value: SpPoint?): NakshaFeature {
        referencePoint = value
        return this
    }

    /**
     * The properties of the feature.
     * @since 3.0.0
     */
    open var properties by PROPERTIES

    /**
     * @see properties
     */
    open fun withProperties(value: NakshaProperties): NakshaFeature {
        properties = value
        return this
    }

    /**
     * The attachment of the feature.
     * @since 3.0.0
     */
    open var attachment by ATTACHMENT_NULL

    /**
     * @see attachment
     */
    open fun withAttachment(value: ByteArray?): NakshaFeature {
        attachment = value
        return this
    }

    /**
     * The mom-type; if _null_ or _undefined_, reads [properties.featureType][NakshaProperties.featureType], then [type]. If modified, writes as well [properties.featureType][NakshaProperties.featureType].
     * - [UniMap: How the Map-Object-Model enables a frictionless future](https://www.here.com/learn/blog/unimap-map-object-model)
     * - [What is The Map-Object-Model (MOM) ?](https://www.linkedin.com/pulse/what-map-object-model-mom-kiran-kumar-mj1yf)
     * @since 3.0.0
     */
    var momType: String
        get() {
            val raw = getRaw("momType")
            if (raw is String) return raw
            return properties.featureType ?: type
        }
        set(value) {
            properties.featureType = value
            setRaw("momType", value)
        }

    /**
     * @see momType
     */
    open fun withMomType(value: String): NakshaFeature {
        momType = value
        return this
    }
}