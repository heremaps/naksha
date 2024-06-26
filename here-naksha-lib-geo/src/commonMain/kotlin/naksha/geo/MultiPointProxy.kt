package naksha.geo

import naksha.base.NullableProperty
import naksha.geo.cords.MultiPointCoordsProxy
import naksha.geo.cords.PointCoordsProxy
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlin.js.JsName

@OptIn(ExperimentalJsExport::class)
@JsExport
open class MultiPointProxy() : GeometryProxy() {

    @JsName("ofPoints")
    constructor(vararg points: PointProxy) : this() {
        this.coordinates = MultiPointCoordsProxy(*points.map { it.coordinates!! }.toTypedArray())
    }

    @JsName("ofCoords")
    constructor(vararg coords: PointCoordsProxy) : this() {
        this.coordinates = MultiPointCoordsProxy(*coords)
    }

    companion object {
        private val COORDINATES =
            NullableProperty<Any, MultiPointProxy, MultiPointCoordsProxy>(MultiPointCoordsProxy::class)
    }

    var coordinates by COORDINATES
    override var type: String? = "MultiPoint"

}