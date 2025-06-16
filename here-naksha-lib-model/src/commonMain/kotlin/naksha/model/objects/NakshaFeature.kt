package naksha.model.objects

import naksha.base.*
import naksha.geo.BBox
import naksha.geo.GeoFeature
import naksha.geo.SpGeometry
import naksha.geo.SpPoint
import naksha.model.*
import naksha.base.Platform.Platform_C.forKClass
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

/**
 * The Naksha Feature extending the default [GeoFeature].
 * @since 3.0
 */
@Suppress("OPT_IN_USAGE")
@JsExport
open class NakshaFeature() : GeoFeature() {

    /**
     * Create a new feature with the given ID.
     * @param id the identifier to set.
     * @since 3.0
     */
    @JsName("NakshaFeatureOf")
    constructor(id: String) : this() {
        this.id = id
    }

    companion object NakshaFeature_C {
        /**
         * The [PlatformType] of [NakshaFeature].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE = forKClass(NakshaFeature::class).withPackageName(PACKAGE_NAME)

        const val PROPERTIES_KEY = "properties"

        private val REFERENCE_POINT_MEMBER = NullableProperty<NakshaFeature, SpPoint>(SpPoint.TYPE)
    }

    override fun withId(id: String): NakshaFeature = super.withId(id) as NakshaFeature
    override fun withBBox(bbox: BBox): NakshaFeature = super.withBBox(bbox) as NakshaFeature
    override fun withAutoBBox(): NakshaFeature = super.withAutoBBox() as NakshaFeature
    override fun withGeometry(geometry: SpGeometry?): NakshaFeature = super.withGeometry(geometry) as NakshaFeature
    override val properties: NakshaProperties
        get() = get_properties(NakshaProperties.TYPE)
    override fun withProperties(properties: AnyObject): NakshaFeature = super.withProperties(properties) as NakshaFeature

    /**
     * Sets a custom feature-number, which in fact changes the [id] of the feature, and must be a 63-bit unsigned integer (`0 .. 9,223,372,036,854,775,807`).
     * @see [featureNumber]
     */
    open fun withFeatureNumber(value: Int64): NakshaFeature {
        this.featureNumber = value
        return this
    }

    /**
     * @see referencePoint
     */
    open fun withReferencePoint(value: SpPoint?): NakshaFeature {
        referencePoint = value
        return this
    }

    /**
     * Reference point of the feature. Used for grid calculation.
     * @since 3.0
     */
    var referencePoint: SpPoint? by REFERENCE_POINT_MEMBER

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
}