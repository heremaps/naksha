package naksha.geo

import naksha.base.NullableProperty
import naksha.geo.cords.LineStringCoordsProxy
import naksha.geo.cords.PolygonCoordsProxy
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlin.js.JsName

@OptIn(ExperimentalJsExport::class)
@JsExport
open class PolygonProxy(): GeometryProxy() {

    @JsName("ofLineStrings")
    constructor(vararg lineStrings: LineStringProxy) : this() {
        this.coordinates = PolygonCoordsProxy(*lineStrings.map { it.coordinates!! }.toTypedArray())
    }

    @JsName("ofLineStringsCoords")
    constructor(vararg coords: LineStringCoordsProxy) : this() {
        this.coordinates = PolygonCoordsProxy(*coords)
    }

    companion object {
        private val COORDINATES =
            NullableProperty<Any, PolygonProxy, PolygonCoordsProxy>(PolygonCoordsProxy::class)
    }

    var coordinates by COORDINATES
    override var type: String? = "Polygon"
}