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
        setRaw("type", FEATURE_TYPE)
    }

    companion object NakshaFeature_C {
        /**
         * The feature-type of this feature itself.
         * @since 3.0.0
         */
        const val FEATURE_TYPE = "Feature"

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

        private val ID = NotNullProperty<NakshaFeature, String>(String::class) { _, _ -> PlatformUtil.randomString(12) }
        private val TYPE = NotNullProperty<NakshaFeature, String>(String::class) { self, _ -> self.defaultFeatureType() }
        private val BBOX_NULL = NullableProperty<NakshaFeature, SpBoundingBox>(SpBoundingBox::class)
        private val GEOMETRY_NULL = NullableProperty<NakshaFeature, SpGeometry>(SpGeometry::class)
        private val REFERENCE_POINT_NULL = NullableProperty<NakshaFeature, SpPoint>(SpPoint::class)
        private val PROPERTIES = NotNullProperty<NakshaFeature, NakshaProperties>(NakshaProperties::class)
        private val ATTACHMENT_NULL = NullableProperty<NakshaFeature, ByteArray>(ByteArray::class)
        private val STRING_NULL = NullableProperty<NakshaFeature, String>(String::class)
    }

    /**
     * The default type to set, when the type is _null_.
     * @since 3.0.0
     */
    protected open fun defaultFeatureType(): String = FEATURE_TYPE

    /**
     * The unique identifier of the feature.
     * @since 3.0.0
     */
    open var id by ID

    /**
     * The type of the feature.
     * @since 3.0.0
     */
    var type by TYPE

    /**
     * The bounding box; if the feature has any.
     * @since 3.0.0
     */
    var bbox by BBOX_NULL

    /**
     * The geometry of the feature, if it has any.
     * @since 3.0.0
     */
    var geometry by GEOMETRY_NULL

    /**
     * Reference point of the feature. Used for grid calculation.
     * @since 3.0.0
     */
    var referencePoint by REFERENCE_POINT_NULL

    /**
     * The properties of the feature.
     * @since 3.0.0
     */
    open var properties by PROPERTIES

    /**
     * The attachment of the feature.
     * @since 3.0.0
     */
    open var attachment by ATTACHMENT_NULL

    /**
     * The mom-type; if any.
     * @since 3.0.0
     */
    var momType by STRING_NULL
}