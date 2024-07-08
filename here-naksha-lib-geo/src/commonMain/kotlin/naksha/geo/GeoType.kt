package naksha.geo

import naksha.base.JsEnum
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmField
import kotlin.reflect.KClass

/**
 * The [geometry type](https://datatracker.ietf.org/doc/html/rfc7946#section-3.1).
 */
@Suppress("OPT_IN_USAGE")
@JsExport
class GeoType : JsEnum() {
    @Suppress("NON_EXPORTABLE_TYPE")
    override fun namespace(): KClass<out JsEnum> = GeoType::class

    override fun initClass() {}

    companion object {
        /**
         * Returns the given value as [GeoType].
         * @param value the value.
         * @return the [GeoType] representing this value.
         */
        fun of(value: String): GeoType = JsEnum.get(value, GeoType::class)

        @JvmField
        @JsStatic
        val Point = def(GeoType::class, "Point")

        @JvmField
        @JsStatic
        val MultiPoint = def(GeoType::class, "MultiPoint")

        @JvmField
        @JsStatic
        val LineString = def(GeoType::class, "LineString")

        @JvmField
        @JsStatic
        val MultiLineString = def(GeoType::class, "MultiLineString")

        @JvmField
        @JsStatic
        val Polygon = def(GeoType::class, "Polygon")

        @JvmField
        @JsStatic
        val MultiPolygon = def(GeoType::class, "MultiPolygon")
    }
}