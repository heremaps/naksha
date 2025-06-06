package naksha.geo

import naksha.base.Platform.PlatformCompanion.forKClass
import naksha.base.PlatformType
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.js.JsStatic
import kotlin.jvm.JvmField
import kotlin.jvm.JvmOverloads

/**
 * A GeoJSON [Point](https://datatracker.ietf.org/doc/html/rfc7946#section-3.1.1).
 */
@Suppress("OPT_IN_USAGE")
@JsExport
class SpPoint() : SpGeometry() {

    @JvmOverloads
    @JsName("fromLonLatZM")
    constructor(longitude: Double, latitude: Double, z: Double? = null, m: Double? = null) : this() {
        this.coordinates =
        this.longitude = longitude
        this.z = z
        this.m = m
    }

    @JsName("fromPointCoord")
    constructor(coordinates: PointCoord) : this() {
        this.coordinates = coordinates
    }

    companion object SpPointCompanion {
        /**
         * The [PlatformType] of [SpPoint].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE: PlatformType<SpPoint> = forKClass(SpPoint::class)
            .withPackageName(PACKAGE_NAME)
            .withJsonType("Point")
    }
}