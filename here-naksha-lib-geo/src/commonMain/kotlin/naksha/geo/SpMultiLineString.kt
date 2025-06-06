package naksha.geo

import naksha.base.Platform.PlatformCompanion.forKClass
import naksha.base.PlatformType
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

@Suppress("OPT_IN_USAGE")
@JsExport
class SpMultiLineString() : SpGeometry() {

    @JsName("SpMultiLineStringOf")
    constructor(coordinates: MultiLineStringCoord) : this() {
        this.coordinates = coordinates
    }

    companion object SpMultiLineStringCompanion {
        /**
         * The [PlatformType] of [SpLineString].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE: PlatformType<SpMultiLineString> = forKClass(SpMultiLineString::class)
            .withPackageName(PACKAGE_NAME)
            .withJsonType("MultiLineString")
    }

    override var coordinates: MultiLineStringCoord
        get() = get_coordinates() as MultiLineStringCoord
        set(value) { set_coordinates(value) }

    fun withCoordinates(value: MultiLineStringCoord): SpMultiLineString {
        set_coordinates(value)
        return this
    }
}