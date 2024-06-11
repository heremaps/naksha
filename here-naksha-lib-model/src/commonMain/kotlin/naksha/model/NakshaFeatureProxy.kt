@file:Suppress("OPT_IN_USAGE")

package naksha.model

import naksha.base.NullableProperty
import kotlin.js.JsExport
import kotlin.jvm.JvmStatic

/**
 * The Naksha Feature extending the default [GeoFeature].
 */
@JsExport
open class NakshaFeatureProxy : com.here.naksha.lib.base.GeoFeature() {

    companion object {
        @JvmStatic
        val REFERENCE_POINT =  NullableProperty<Any, NakshaFeatureProxy, PointProxy>(
            PointProxy::class)
    }

    fun getNakshaProperties(): NakshaPropertiesProxy = properties.proxy(NakshaPropertiesProxy::class)

    /**
     * Reference point of the feature. Used for grid calculation.
     */
    var referencePoint: PointProxy? by REFERENCE_POINT

}