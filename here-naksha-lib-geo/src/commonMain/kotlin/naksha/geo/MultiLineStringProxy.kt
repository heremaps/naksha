package naksha.geo

import naksha.base.NullableProperty
import naksha.geo.cords.LineStringCoordsProxy
import naksha.geo.cords.MultiLineStringCoordsProxy
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlin.js.JsName

@OptIn(ExperimentalJsExport::class)
@JsExport
open class MultiLineStringProxy() : GeometryProxy() {

    @JsName("ofLineStrings")
    constructor(vararg lineStrings: LineStringProxy) : this() {
        this.addAll(lineStrings.map { it.coordinates })
    }

    @JsName("ofLineStringsCoords")
    constructor(vararg coords: LineStringCoordsProxy) : this() {
        this.addAll(coords)
    }

    companion object {
        private val COORDINATES =
            NullableProperty<Any, MultiLineStringProxy, MultiLineStringCoordsProxy>(MultiLineStringCoordsProxy::class)
    }

    var coordinates by COORDINATES
    override var type: String? = "MultiLineString"

}