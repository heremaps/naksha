package naksha.geo

import naksha.base.JsEnum
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmField
import kotlin.jvm.JvmStatic
import kotlin.reflect.KClass

/**
 * The [geometry type](https://datatracker.ietf.org/doc/html/rfc7946#section-3.1).
 */
@Suppress("OPT_IN_USAGE")
@JsExport
class SpType : JsEnum() {
    @Suppress("NON_EXPORTABLE_TYPE")
    override fun namespace(): KClass<out JsEnum> = SpType::class

    override fun initClass() {}

    companion object GeoTypeCompanion {
        /**
         * Returns the given value as [SpType].
         * @param value the value.
         * @return the [SpType] representing this value.
         */
        @JvmStatic
        @JsStatic
        fun of(value: String): SpType = get(value, SpType::class)

        @JvmField
        @JsStatic
        val Point = def(SpType::class, "Point")

        @JvmField
        @JsStatic
        val MultiPoint = def(SpType::class, "MultiPoint")

        @JvmField
        @JsStatic
        val LineString = def(SpType::class, "LineString")

        @JvmField
        @JsStatic
        val MultiLineString = def(SpType::class, "MultiLineString")

        @JvmField
        @JsStatic
        val Polygon = def(SpType::class, "Polygon")

        @JvmField
        @JsStatic
        val MultiPolygon = def(SpType::class, "MultiPolygon")

        @JvmField
        @JsStatic
        val GeometryCollection = def(SpType::class, "GeometryCollection")
    }
}