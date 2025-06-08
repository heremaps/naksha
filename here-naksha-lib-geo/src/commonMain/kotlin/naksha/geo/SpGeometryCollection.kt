package naksha.geo

import naksha.base.NullableProperty
import naksha.base.Platform.PlatformCompanion.forKClass
import naksha.base.PlatformType
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

/**
 * The [GeoJSON geometry collection](https://datatracker.ietf.org/doc/html/rfc7946#section-3.1.8).
 * @since 3.0
 */
@Suppress("OPT_IN_USAGE")
@JsExport
class SpGeometryCollection() : SpGeometry() {

    @JsName("SpGeometryCollectionOf")
    constructor(geometries: SpGeometryList) : this() {
        this.geometries = geometries
    }

    companion object SpGeometryCollectionCompanion {
        /**
         * The [PlatformType] of [SpGeometryCollection].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE: PlatformType<SpGeometryCollection> = forKClass(SpGeometryCollection::class)
            .withPackageName(PACKAGE_NAME)
            .withJsonType("GeometryCollection")

        private val GEOMETRIES_MEMBER = NullableProperty<SpGeometryCollection, SpGeometryList>(SpGeometryList.TYPE)

        init {
            initialize()
        }
    }

    /**
     * The geometries of this collection.
     * @since 3.0
     */
    var geometries: SpGeometryList? by GEOMETRIES_MEMBER
}