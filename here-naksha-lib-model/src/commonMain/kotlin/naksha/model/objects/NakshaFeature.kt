package naksha.model.objects

import naksha.base.AnyObject
import naksha.base.NotNullProperty
import naksha.base.NullableProperty
import naksha.base.PlatformUtil
import naksha.geo.SpBoundingBox
import naksha.geo.SpFeature
import naksha.geo.SpGeometry
import naksha.geo.SpPoint
import kotlin.js.JsExport
import kotlin.js.JsName

/**
 * The Naksha Feature extending the default [SpFeature].
 */
@Suppress("OPT_IN_USAGE")
@JsExport
open class NakshaFeature() : AnyObject() {

    /**
     * Create a new feature with the given ID.
     * @param id the identifier to set.
     */
    @JsName("of")
    constructor(id: String?) : this() {
        setRaw("id", id)
    }

    companion object {
        /**
         * The feature-type of this feature itself.
         */
        const val FEATURE_TYPE = "Feature"

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
     */
    protected open fun defaultFeatureType(): String = FEATURE_TYPE

    /**
     * The unique identifier of the feature.
     */
    open var id by ID

    /**
     * The type of the feature.
     */
    var type by TYPE

    /**
     * The bounding box; if the feature has any.
     */
    var bbox by BBOX_NULL

    /**
     * The geometry of the feature, if it has any.
     */
    var geometry by GEOMETRY_NULL

    /**
     * Reference point of the feature. Used for grid calculation.
     */
    var referencePoint by REFERENCE_POINT_NULL

    /**
     * The properties of the feature.
     */
    open var properties by PROPERTIES

    /**
     * The attachment of the feature.
     */
    open var attachment by ATTACHMENT_NULL

    /**
     * The mom-type; if any.
     */
    var momType by STRING_NULL
}