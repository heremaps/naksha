@file:Suppress("OPT_IN_USAGE")

package naksha.model

import naksha.base.NullableProperty
import naksha.geo.GeoFeature
import naksha.geo.cords.PointCoordsProxy
import kotlin.js.JsExport
import kotlin.js.JsName

/**
 * The Naksha Feature extending the default [GeoFeature].
 */
@JsExport
open class NakshaFeatureProxy() : GeoFeature() {

    @JsName("of")
    constructor(id: String): this(){
        this.id = id
    }

    companion object {
        private val REFERENCE_POINT = NullableProperty<Any, NakshaFeatureProxy, PointCoordsProxy>(
            PointCoordsProxy::class
        )
    }

    /**
     * Reference point of the feature. Used for grid calculation.
     */
    var referencePoint: PointCoordsProxy? by REFERENCE_POINT

    fun nakshaProperties(): NakshaPropertiesProxy = properties.proxy(NakshaPropertiesProxy::class)

    fun xyz() = nakshaProperties().xyz
}
