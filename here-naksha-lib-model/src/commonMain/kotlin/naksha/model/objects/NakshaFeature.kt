package naksha.model.objects

import naksha.base.NotNullProperty
import naksha.base.NullableProperty
import naksha.geo.GeoFeatureProxy
import naksha.geo.PointGeometry
import kotlin.js.JsExport
import kotlin.js.JsName

/**
 * The Naksha Feature extending the default [GeoFeatureProxy].
 */
@Suppress("OPT_IN_USAGE")
@JsExport
open class NakshaFeature() : GeoFeatureProxy() {

    /**
     * Create a new feature with the given ID.
     * @param id the identifier to set.
     */
    @JsName("of")
    constructor(id: String?) : this() {
        setRaw(ID, id)
    }

    companion object {
        const val ID = "id"

        private val REFERENCE_POINT = NullableProperty<NakshaFeature, PointGeometry>(PointGeometry::class)
        private val PROPERTIES = NotNullProperty<NakshaFeature, NakshaProperties>(NakshaProperties::class)
    }

    /**
     * Reference point of the feature. Used for grid calculation.
     */
    open var referencePoint by REFERENCE_POINT

    /**
     * The properties of the feature.
     */
    open var properties by PROPERTIES
}