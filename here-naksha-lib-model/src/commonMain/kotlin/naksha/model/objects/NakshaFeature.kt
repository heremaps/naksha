package naksha.model.objects

import naksha.base.*
import naksha.geo.SpBoundingBox
import naksha.geo.SpFeature
import naksha.geo.SpGeometry
import naksha.geo.SpPoint
import naksha.base.NakshaError.NakshaErrorCompanion.ILLEGAL_ARGUMENT
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.js.JsStatic
import kotlin.jvm.JvmStatic

/**
 * The Naksha feature extends the default [SpFeature] with all members of the collection expected to be in [XYZ][XyzMembers] layout.
 *
 * ### Note
 * If an own member layout is designed, an own feature class should be created similar to this one.
 */
@Suppress("LeakingThis", "OPT_IN_USAGE")
@JsExport
open class NakshaFeature() : SpFeature() {

    /**
     * Create a new feature with the given `id`.
     * @param id the identifier to set.
     * @since 3.0
     */
    @JsName("newFeatureWithId")
    constructor(id: Id) : this() {
        withType(typeDefaultValue())
        this.id = id
        withFeatureType(featureTypeDefaultValue())
    }

    /**
     * Create a new feature with the given `id`.
     * @param id the identifier to set.
     * @since 3.0
     */
    @JsName("newFeature")
    constructor(id: String?) : this() {
        withType(typeDefaultValue())
        this.id = Id(id ?: BaseUtil.randomAtoZ())
        withFeatureType(featureTypeDefaultValue())
    }

    companion object NakshaFeature_C {
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
            val raw = Base.fromJSON(json)
            if (raw !is PlatformMap) throw NakshaException(ILLEGAL_ARGUMENT, "The given JSON is no object")
            return raw.proxy(NakshaFeature::class)
        }

        private val REFERENCE_POINT_NULL = NullableProperty<NakshaFeature, SpPoint>(SpPoint::class)
        private val PROPERTIES = NotNullProperty<NakshaFeature, NakshaProperties>(NakshaProperties::class)
        private val TITLE_NULL = NullableProperty<NakshaFeature, String>(String::class)
        private val DESCRIPTION_NULL = NullableProperty<NakshaFeature, String>(String::class)
    }

    override fun withId(value: Id?): NakshaFeature = super.withId(value) as NakshaFeature
    override fun withBbox(value: SpBoundingBox?): NakshaFeature = super.withBbox(value) as NakshaFeature
    override fun withGeometry(value: SpGeometry?): NakshaFeature = super.withGeometry(value) as NakshaFeature
    override fun withType(value: String?): NakshaFeature = super.withType(value) as NakshaFeature
    override fun withFeatureType(value: FeatureType?): NakshaFeature = super.withFeatureType(value) as NakshaFeature
    override fun featureTypeDefaultValue(): FeatureType? = FeatureType.FEATURE

    /**
     * Reference point of the feature. Used for grid calculation.
     * @since 3.0
     */
    open var referencePoint by REFERENCE_POINT_NULL

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

    /**
     * Helper to get/set the [TupleNumber] of the feature.
     *
     * This is actually the state of the feature when unmodified, and after modification it represents the state that was modified, so actually _BASE_ in a [3-way-merge](https://en.wikipedia.org/wiki/Merge_(version_control)). Normally clients should never mutate this property!
     *
     * All [NakshaFeature] _(and extending classes)_ follow the [XYZ member layout][XyzMembers], therefore the location of the [TupleNumber] is well known at:
     *
     * `properties->@ns:com:here:xyz->uuid`
     * @since 3.0
     * @see XyzMembers.XyzTn
     */
    open var tupleNumber: TupleNumber?
        get() = XyzMembers.XyzTn.get(this)
        set(value) {
            XyzMembers.XyzTn.set(this, value)
        }

    /**
     * Helper to get/set the raw [TupleNumber] of the feature.
     *
     * This is actually the state of the feature when unmodified, and after modification it represents the state that was modified, so actually _BASE_ in a [3-way-merge](https://en.wikipedia.org/wiki/Merge_(version_control)). Normally clients should never mutate this property!
     *
     * All [NakshaFeature] _(and extending classes)_ follow the [XYZ member layout][XyzMembers], therefore the location of the [TupleNumber] is well known at:
     *
     * `properties->@ns:com:here:xyz->uuid`
     * @since 3.0
     * @see XyzMembers.XyzTn
     */
    open var tupleNumberRaw: Any?
        get() = XyzMembers.XyzTn.read(this)
        set(value) {
            XyzMembers.XyzTn.write(this, value)
        }

    /**
     * Sets the [tupleNumber] to the given value and returns this feature.
     * @param tn the [TupleNumber] to set.
     * @return this.
     * @since 3.0
     */
    open fun withTupleNumber(tn: TupleNumber?): NakshaFeature {
        tupleNumber = tn
        return this
    }

    /**
     * Tests if this feature is a tombstone, so deleted.
     * @return `true` if this feature is a tombstone; `false` otherwise.
     * @since 3.0
     */
    open fun isDeleted(): Boolean {
        val tn = this.tupleNumber
        return tn != null && tn.isDeleted()
    }
}