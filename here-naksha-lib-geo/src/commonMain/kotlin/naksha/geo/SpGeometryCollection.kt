package naksha.geo

import naksha.base.NullableProperty
import kotlin.js.JsExport
import kotlin.js.JsName

@Suppress("OPT_IN_USAGE")
@JsExport
class SpGeometryCollection() : SpGeometry() {

    @JsName("of")
    constructor(geometries: SpGeometryList) : this() {
        this.geometries = geometries
    }

    companion object GeometryCollectionCompanion {
        private val GEOMETRIES = NullableProperty<SpGeometryCollection, SpGeometryList>(SpGeometryList::class)
    }

    var geometries by GEOMETRIES
}