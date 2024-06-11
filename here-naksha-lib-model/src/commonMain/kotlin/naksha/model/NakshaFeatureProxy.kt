@file:Suppress("OPT_IN_USAGE")

package naksha.model

import naksha.base.NullableProperty
import naksha.geo.GeoFeature
import kotlin.js.JsExport

/**
 * The Naksha Feature extending the default [GeoFeature].
 */
@JsExport
open class NakshaFeatureProxy : GeoFeature() {

    companion object {
        private val REFERENCE_POINT = NullableProperty<Any, NakshaFeatureProxy, PointProxy>(
            PointProxy::class
        )
    }

    fun getNakshaProperties(): NakshaPropertiesProxy = properties.proxy(NakshaPropertiesProxy::class)

    /**
     * Reference point of the feature. Used for grid calculation.
     */
    var referencePoint: PointProxy? by REFERENCE_POINT

}