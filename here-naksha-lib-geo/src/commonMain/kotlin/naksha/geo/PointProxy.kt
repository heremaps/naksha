package naksha.geo

import naksha.base.NullableProperty
import naksha.geo.cords.PointCoordsProxy
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlin.js.JsName

@OptIn(ExperimentalJsExport::class)
@JsExport
open class PointProxy() : GeometryProxy() {

    @JsName("of")
    constructor(vararg coords: Double?) : this() {
        this.addAll(coords)
    }

    companion object{
        private val COORDINATES = NullableProperty<Any, PointProxy, PointCoordsProxy>(PointCoordsProxy::class)
    }
    var coordinates by COORDINATES
    override var type: String? = "Point"

}