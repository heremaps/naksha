package naksha.geo

import naksha.base.NullableProperty
import kotlin.js.JsExport
import kotlin.js.JsName

@Suppress("OPT_IN_USAGE")
@JsExport
class GeometryCollectionProxy() : GeometryProxy() {

    @JsName("of")
    constructor(geometries: GeometriesProxy) : this() {
        this.geometries = geometries
    }

    companion object GeometryCollectionCompanion {
        private val GEOMETRIES = NullableProperty<Any, GeometryCollectionProxy, GeometriesProxy>(GeometriesProxy::class)
    }

    var geometries by GEOMETRIES
}

