package naksha.geo
import naksha.base.NullableProperty
import naksha.base.P_Object
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlin.js.JsName

@OptIn(ExperimentalJsExport::class)
@JsExport
abstract class GeometryProxy(): P_Object() {

    @JsName("of")
    constructor(coordinates: CoordinatesProxy<*>, type: String? = null): this() {
        this.coordinates = coordinates
        this.type = type
    }

    companion object {
        private val COORDINATES = NullableProperty<Any, GeometryProxy, CoordinatesProxy<*>>(CoordinatesProxy::class)
        private val TYPE = NullableProperty<Any, GeometryProxy, String>(String::class)
    }

    var coordinates by COORDINATES
    var type by TYPE

}