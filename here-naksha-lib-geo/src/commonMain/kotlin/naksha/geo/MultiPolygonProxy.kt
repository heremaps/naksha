package naksha.geo

import naksha.base.NullableProperty
import naksha.geo.cords.MultiPolygonCoordsProxy
import naksha.geo.cords.PolygonCoordsProxy
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlin.js.JsName

@OptIn(ExperimentalJsExport::class)
@JsExport
open class MultiPolygonProxy() : GeometryProxy() {

    @JsName("ofPolygons")
    constructor(vararg polygons: PolygonProxy) : this() {
        this.coordinates = MultiPolygonCoordsProxy(*polygons.map { it.coordinates!! }.toTypedArray())
    }

    @JsName("ofPolygonsCoords")
    constructor(vararg coords: PolygonCoordsProxy) : this() {
        this.coordinates = MultiPolygonCoordsProxy(*coords)
    }

    companion object {
        private val COORDINATES =
            NullableProperty<Any, MultiPolygonProxy, MultiPolygonCoordsProxy>(MultiPolygonCoordsProxy::class)
    }

    var coordinates by COORDINATES
    override var type: String? = "MultiPolygon"
}