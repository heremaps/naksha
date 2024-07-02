@file:Suppress("OPT_IN_USAGE")

package naksha.model

import naksha.base.NullableProperty
import naksha.geo.GeoFeatureProxy
import naksha.geo.PointGeometry
import kotlin.js.JsExport
import kotlin.js.JsName

/**
 * The Naksha Feature extending the default [GeoFeatureProxy].
 */
@JsExport
open class NakshaFeatureProxy() : GeoFeatureProxy() {

    @JsName("of")
    constructor(id: String): this(){
        this.id = id
    }

    companion object {
        private val REFERENCE_POINT = NullableProperty<Any, NakshaFeatureProxy, PointGeometry>(
            PointGeometry::class
        )
    }

    /**
     * Reference point of the feature. Used for grid calculation.
     */
    var referencePoint: PointGeometry? by REFERENCE_POINT

    fun nakshaProperties(): NakshaPropertiesProxy = properties.proxy(NakshaPropertiesProxy::class)

    fun xyz() = nakshaProperties().xyz
}
