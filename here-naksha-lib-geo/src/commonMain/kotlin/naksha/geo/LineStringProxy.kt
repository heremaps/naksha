package naksha.geo

import naksha.base.NullableProperty
import naksha.geo.cords.LineStringCoordsProxy
import naksha.geo.cords.PointCoordsProxy
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlin.js.JsName

@OptIn(ExperimentalJsExport::class)
@JsExport
open class LineStringProxy() : GeometryProxy() {

    @JsName("ofPoints")
    constructor(vararg points: PointProxy) : this() {
        this.addAll(points.map { it.coordinates })
    }

    @JsName("ofCoords")
    constructor(vararg coords: PointCoordsProxy) : this() {
        this.addAll(coords)
    }

    companion object{
        private val COORDINATES = NullableProperty<Any, LineStringProxy, LineStringCoordsProxy>(LineStringCoordsProxy::class)
    }
    var coordinates by COORDINATES
    override var type: String? = "LineString"

}