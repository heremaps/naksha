package naksha.geo

import naksha.base.NullableProperty
import naksha.base.P_Object
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@OptIn(ExperimentalJsExport::class)
@JsExport
open class GeometryProxy : P_Object() {

    companion object {
        private val TYPE = NullableProperty<Any, GeometryProxy, String>(String::class)
    }

    open var type by TYPE


    fun asPointProxy() = this.proxy(PointProxy::class)
    fun asMultiPointProxy() = this.proxy(MultiPointProxy::class)
    fun asLineStringProxy() = this.proxy(LineStringProxy::class)
    fun asMultiLineStringProxy() = this.proxy(MultiLineStringProxy::class)
    fun asMultiPolygonProxy() = this.proxy(MultiPolygonProxy::class)
    fun asPolygonProxy() = this.proxy(PolygonProxy::class)

}