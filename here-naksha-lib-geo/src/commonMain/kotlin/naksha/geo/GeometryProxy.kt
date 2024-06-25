package naksha.geo

import naksha.base.NullableProperty
import naksha.base.P_AnyList
import naksha.base.P_Object
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@OptIn(ExperimentalJsExport::class)
@JsExport
class GeometryProxy : P_Object() {

    companion object {
        private val COORDINATES = NullableProperty<Any, GeometryProxy, P_AnyList>(P_AnyList::class)
        private val TYPE = NullableProperty<Any, GeometryProxy, String>(String::class)
    }

    var coordinates by COORDINATES
    var type by TYPE

}