@file:Suppress("OPT_IN_USAGE")

package naksha.geo

import naksha.base.NullableProperty
import naksha.base.PAnyMap
import naksha.base.Id
import naksha.base.FeatureType
import naksha.base.FeatureType.FeatureType_C.FEATURE
import naksha.base.NotNullIdProperty
import kotlin.js.JsExport
import kotlin.js.JsName

/**
 * A GeoJSON feature.
 * @since 3.0
 */
@JsExport
open class SpFeature() : PAnyMap() {

    companion object SpFeature_C {
        private val ID = NotNullIdProperty<SpFeature>(randomId = true)
        private val BBOX_NULL = NullableProperty<SpFeature, SpBoundingBox>(SpBoundingBox::class)
        private val GEOMETRY_NULL = NullableProperty<SpFeature, SpGeometry>(SpGeometry::class)
    }

    /**
     * Creates a new feature with the given `id`.
     * @param id the identifier of the feature.
     * @param
     */
    @JsName("of")
    constructor(id: Id) : this() {
        withType(typeDefaultValue())
        this.id = id
        withFeatureType(featureTypeDefaultValue())
    }

    /**
     * The default value for the `type` property, when creating new objects; if any.
     * @since 3.0
     * @see type
     */
    protected open fun typeDefaultValue(): String? = "Feature"

    /**
     * The default value for the `featureType` property; if any.
     * @since 3.0
     * @see featureType
     */
    protected open fun featureTypeDefaultValue(): FeatureType? = null

    /**
     * Returns the [FeatureType] of this feature by mainly reading the property `featureType`, falling back to [featureTypeDefaultValue] or `type` interpolation.
     *
     * This has become more or less an inofficial standard used many times:
     * - [Microsoft Maps](https://learn.microsoft.com/de-de/javascript/api/@azure/planetarycomputer/featuretype?view=azure-node-latest)
     * - [Google Maps](https://developers.google.com/maps/documentation/javascript/reference/data-driven-styling)
     * - [Geospatial Data Abstraction Library](https://github.com/OSGeo/gdal/issues/9946)
     * @since 3.0
     */
    open var featureType: FeatureType
        get() {
            var raw = getRaw("featureType")
            if (raw is FeatureType) return raw

            if (raw !is String) raw = featureTypeDefaultValue()
            if (raw is String) {
                val featureType = FeatureType.fromString(raw)
                if (featureType != null) {
                    setRaw("featureType", featureType)
                    return featureType
                }
            }
            return FEATURE
        }
        set(value) {
            setRaw("featureType", value)
        }

    /**
     * @see featureType
     * @since 3.0
     */
    open fun withFeatureType(value: FeatureType?): SpFeature {
        if (value == null) remove("featureType") else this.featureType = value
        return this
    }

    /**
     * The unique identifier of the feature.
     * @since 3.0
     */
    open var id: Id by ID

    /**
     * @see id
     * @since 3.0
     */
    open fun withId(value: Id?): SpFeature {
        if (value == null) remove("id") else this.id = value
        return this
    }

    /**
     * Tests if this feature does have the property `id` set.
     * @since 3.0
     */
    fun hasId(): Boolean = hasIdValue("id")

    /**
     * The bounding box.
     * @since 3.0
     */
    open var bbox by BBOX_NULL

    /**
     * @see bbox
     */
    open fun withBbox(value: SpBoundingBox?): SpFeature {
        bbox = value
        return this
    }
    /**
     * The geometry of the feature.
     * @since 3.0
     */
    open var geometry by GEOMETRY_NULL

    /**
     * @see geometry
     */
    open fun withGeometry(value: SpGeometry?): SpFeature {
        geometry = value
        return this
    }

    /**
     * The type of the feature, to be [GeoJSON](https://datatracker.ietf.org/doc/html/rfc7946) compatible, one of the following is expected:
     * - `FeatureCollection`
     * - `Feature`
     *
     * Beware, no other values are allowed in the [GeoJSON specification, section 7](https://datatracker.ietf.org/doc/html/rfc7946#section-7).
     * @since 3.0
     */
    open var type: String
        get() = getAs("type", String::class) ?: typeDefaultValue() ?: "Feature"
        set(value) {
            setRaw("type", value)
        }

    /**
     * @see type
     * @since 3.0
     */
    open fun withType(value: String?): SpFeature {
        if (value == null) remove("type") else type = value
        return this
    }

    /**
     * Calculate the bounding box from the geometry and updated the [bbox] property.
     * @since 3.0
     */
    open fun updateBoundingBox(): SpBoundingBox {
        TODO("GeoFeature::updateBoundingBox is not yet implemented")
    }
}
