package naksha.model.objects

import naksha.base.*
import naksha.geo.SpBoundingBox
import naksha.geo.SpFeature
import naksha.geo.SpGeometry
import naksha.geo.SpPoint
import naksha.model.*
import naksha.model.NakshaError.NakshaErrorCompanion.ILLEGAL_ARGUMENT
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.js.JsStatic
import kotlin.jvm.JvmStatic

/**
 * The Naksha Feature extending the default [SpFeature].
 */
@Suppress("LeakingThis", "OPT_IN_USAGE")
@JsExport
open class NakshaFeature() : AnyObject() {

    /**
     * Create a new feature with the given ID.
     * @param id the identifier to set.
     * @since 3.0
     */
    @JsName("of")
    constructor(id: String) : this() {
        setRaw("id", id)
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
        get() = properties.xyz.guid?.tupleNumber ?: TupleNumber.HEAD

    private var cachedId: String? = null
    private var cachedFeatureNumber: Int64? = null

    /**
     * Calculates the feature number from the given identifier.
     * @param id the identifier.
     * @return the feature-number.
     */
    protected open fun featureNumberOfId(id: String): Int64 = Naksha.featureNumber(id)

    /**
     * The feature-number of the feature.
     *
     * If the feature is in [HEAD][TupleNumber.HEAD] state, so not yet persisted, and no custom feature number was set, then the method will calculate the feature-number from the [id]. A custom feature-number
     * @since 3.0
     */
    var featureNumber: Int64
        get() {
            val id = this.id
            val internalNumber = Naksha.internalIdToNumber[id]
            if (internalNumber != null) return internalNumber.toInt64()
            val cachedId = this.cachedId
            var cachedFeatureNumber = this.cachedFeatureNumber

            // If the user changed the id.
            if (id === cachedId && cachedFeatureNumber != null) return cachedFeatureNumber

            // If the feature exists already, and the `id` was not changed, return existing feature number.
            val guid = properties.xyz.guid
            if (tupleNumber != TupleNumber.HEAD && guid != null && id == guid.id) return guid.tupleNumber.featureNumber

            // Calculate a feature number for new feature.
            cachedFeatureNumber = featureNumberOfId(id)
            this.cachedId = id
            this.cachedFeatureNumber = cachedFeatureNumber
            return cachedFeatureNumber
        }
        set(value) {
            if (value < 0) {
                throw illegalArg(
                    "The given feature-number is invalid, must be positive 0 to 9,223,372,036,854,775,807, but was $value"
                )
            }
            // When the user sets the feature-number, this means the `id` becomes the feature-number too!
            val newId = value.toString()
            cachedFeatureNumber = featureNumberOfId(newId)
            cachedId = newId
            id = newId
        }

    /**
     * Sets a custom feature-number, which in fact changes the [id] of the feature, and must be a 63-bit unsigned integer (`0 .. 9,223,372,036,854,775,807`).
     * @see [featureNumber]
     */
    open fun withFeatureNumber(value: Int64): NakshaFeature {
        this.featureNumber = value
        return this
    }

    /**
     * The global unique identifier of the feature, exists only if the feature is already persisted in a storage.
     * @since 3.0
     */
    val guid: Guid?
        get() = properties.xyz.guid

    /**
     * Returns the collection-number of the collection in which the feature is currently persisted; `null` if the feature is not yet persisted.
     * @since 3.0
     */
    open val collectionNumber: Int?
        get() = guid?.tupleNumber?.collectionNumber

    /**
     * Returns the map-number of the map in which the feature is currently persisted; `null` if the feature is not yet persisted.
     * @since 3.0
     */
    open val mapNumber: Int?
        get() = guid?.tupleNumber?.mapNumber

    /**
     * Returns the storage-number of the storage in which the feature is currently persisted; `null` if the feature is not yet persisted.
     * @since 3.0
     */
    open val storageNumber: Int64?
        get() = guid?.tupleNumber?.storageNumber

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
     * A virtual property that reads [properties.featureType][NakshaProperties.featureType], then [momType], and eventually [type]. Modifications will change `momType` and `properties.featureType`, keeping them in sync.
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
     * The mom-type; if `null` or _undefined_, reads [properties.featureType][NakshaProperties.featureType]. If modified, writes as well [properties.featureType][NakshaProperties.featureType].
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
