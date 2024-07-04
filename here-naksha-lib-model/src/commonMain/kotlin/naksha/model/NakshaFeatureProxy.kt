package naksha.model

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
open class NakshaFeatureProxy() : GeoFeatureProxy() {

    @JsName("of")
    constructor(id: String) : this() {
        this.id = id
    }

    companion object {
        private val REFERENCE_POINT = NullableProperty<Any, NakshaFeatureProxy, PointGeometry>(PointGeometry::class)
        private val PROPERTIES = NotNullProperty<Any, NakshaFeatureProxy, NakshaPropertiesProxy>(NakshaPropertiesProxy::class)
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