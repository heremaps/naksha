package naksha.model.objects

import naksha.base.*
import naksha.geo.SpBoundingBox
import naksha.geo.SpFeature
import naksha.geo.SpGeometry
import naksha.geo.SpPoint
import naksha.model.Metadata
import naksha.model.Naksha
import naksha.model.NakshaError.NakshaErrorCompanion.ILLEGAL_ARGUMENT
import naksha.model.NakshaError.NakshaErrorCompanion.ILLEGAL_STATE
import naksha.model.NakshaException
import naksha.model.TupleNumber
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.js.JsStatic
import kotlin.jvm.JvmStatic

/**
 * The Naksha Feature extending the default [SpFeature].
 */
@Suppress("OPT_IN_USAGE")
@JsExport
open class NakshaFeature() : AnyObject() {

    /**
     * Create a new feature with the given ID.
     * @param id the identifier to set.
     * @since 3.0
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
         * @since 3.0
         */
        const val TYPE = "Feature"
        /**
         * The key of geometry (`geometry`).
         * @since 3.0
         */
        const val GEOMETRY = "geometry"

        /**
         * The JSON keys
         */
        const val ID_KEY = "id"
        const val PROPERTIES_KEY = "properties"
        const val TITLE_KEY = "title"
        const val DESCRIPTION_KEY = "description"
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
        private val TITLE_NULL = NullableProperty<NakshaFeature, String>(String::class)
        private val DESCRIPTION_NULL = NullableProperty<NakshaFeature, String>(String::class)
        private val ATTACHMENT_NULL = NullableProperty<NakshaFeature, ByteArray>(ByteArray::class)
    }

    /**
     * The default type.
     * @since 3.0
     */
    protected open fun defaultType(): String = TYPE

    /**
     * The default feature-type; if any.
     * @since 3.0
     */
    protected open fun defaultFeatureType(): String? = null

    /**
     * The unique identifier of the feature.
     * @since 3.0
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
     * Returns the [tuple-number][TupleNumber] of this feature, may be [TupleNumber.HEAD], if the feature is not yet persisted.
     * @since 3.0
     */
    val tupleNumber: TupleNumber
        get() = this.properties.xyz.guid?.tupleNumber ?: TupleNumber.HEAD

    private var cachedId: String? = null
    private var cachedFeatureNumber: Int64? = null

    /**
     * Returns the feature-number of the feature.
     *
     * If the feature is in [HEAD][TupleNumber.HEAD] state, so not yet persisted, and no custom feature number was set, then the method will calculate the feature-number from the [id].
     * @since 3.0
     */
    var featureNumber: Int64
        get() {
            val tupleNumber = this.tupleNumber
            if (tupleNumber != TupleNumber.HEAD) return tupleNumber.featureNumber
            val raw = getRaw("featureNumber")
            if (raw is Int64) return raw
            val id = this.id
            var cached_id = cachedId
            var cached_featureNumber = cachedFeatureNumber
            if (id === cached_id && cached_featureNumber != null) return cached_featureNumber
            val md5 = Naksha.hashId(id)
            cached_featureNumber = Naksha.featureNumber(md5)
            cachedId = id
            cachedFeatureNumber = cached_featureNumber
            return cached_featureNumber
        }
        set(value) {
            withFeatureNumber(value)
        }

    /**
     * Sets a custom feature-number.
     *
     * If `null` is given, then a previously set custom feature-number is removed, if any was set.
     *
     * - Throws [ILLEGAL_STATE] if the feature does have already a feature-number (these numbers are immutable).
     * @see [featureNumber]
     */
    open fun withFeatureNumber(featureNumber: Int64?): NakshaFeature {
        if (featureNumber == null) {
            removeRaw("featureNumber")
        } else if (tupleNumber != TupleNumber.HEAD && featureNumber != tupleNumber.featureNumber) {
            throw NakshaException(
                ILLEGAL_STATE,
                "The feature does have a fixed feature-number (${tupleNumber.featureNumber}), deny overriding with $featureNumber"
            )
        } else {
            setRaw("featureNumber", featureNumber)
        }
        return this
    }

    /**
     * Tests if this feature does have an existing immutable feature-number, in that case no custom feature-number can be set.
     *
     * @return `true` if this feature does have an immutable feature-number; `false` otherwise.
     * @since 3.0
     */
    fun hasFeatureNumber(): Boolean = tupleNumber != TupleNumber.HEAD

    /**
     * The type of the feature, to be [GeoJSON](https://datatracker.ietf.org/doc/html/rfc7946) compatible, one of the following is expected:
     * - `FeatureCollection`
     * - `Feature`
     * - `Point`
     * - `LineString`
     * - `MultiPoint`
     * - `Polygon`
     * - `MultiLineString`
     * - `MultiPolygon`
     * - `GeometryCollection`
     *
     * Beware, no other values are allowed in the [GeoJSON specification, section 7](https://datatracker.ietf.org/doc/html/rfc7946#section-7), therefore we introduce a [customer feature-type][featureType], that is stored in [properties]. Later the [MOM](https://www.here.com/learn/blog/unimap-map-object-model) specification relocated the property into the object root, and renamed it to `momType`. For downward compatibility, this implementation will prefer `properties.featureType` and keep it in sync with `momType`.
     * @since 3.0
     * @see [featureType]
     * @see [NakshaProperties.featureType]
     * @see [momType]
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
     * A virtual property that reads [properties.featureType][NakshaProperties.featureType], then [momType], and eventually [type]. Modifications will change only `momType` and `properties.featureType`.
     *
     * @since 3.0
     * @see [momType]
     * @see [NakshaProperties.featureType]
     * @see [type]
     */
    var featureType: String
        get() = properties.featureType ?: momType ?: type
        set(value) {
            setRaw("momType", value)
            properties.setRaw("featureType", value)
        }

    /**
     * @see [featureType]
     */
    open fun withFeatureType(value: String): NakshaFeature {
        featureType = value
        return this
    }

    /**
     * The bounding box; if the feature has any.
     * @since 3.0
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
     * @since 3.0
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
     * @since 3.0
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
     * @since 3.0
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
     * @since 3.0
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
     * The mom-type; if _null_ or _undefined_, reads [properties.featureType][NakshaProperties.featureType]. If modified, writes as well [properties.featureType][NakshaProperties.featureType].
     * - [UniMap: How the Map-Object-Model enables a frictionless future](https://www.here.com/learn/blog/unimap-map-object-model)
     * - [What is The Map-Object-Model (MOM) ?](https://www.linkedin.com/pulse/what-map-object-model-mom-kiran-kumar-mj1yf)
     * @since 3.0
     * @see [featureType]
     * @see [NakshaProperties.featureType]
     * @see [type]
     */
    var momType: String?
        get() {
            val raw = getRaw("momType")
            if (raw is String) return raw
            return properties.featureType
        }
        set(value) {
            if (value == null) {
                removeRaw("momType")
                properties.removeRaw("featureType")
            } else {
                setRaw("momType", value)
                properties.setRaw("featureType", value)
            }
        }

    /**
     * @see momType
     */
    open fun withMomType(value: String?): NakshaFeature {
        momType = value
        return this
    }

    /**
     * Human-readable title.
     */
    open var title by TITLE_NULL

    /**
     * Human-readable description.
     */
    open var description by DESCRIPTION_NULL
}
