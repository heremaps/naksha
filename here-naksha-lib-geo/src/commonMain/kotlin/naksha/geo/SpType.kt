package naksha.geo

import naksha.base.AnyObject
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

        /**
         * Returns the given value as [SpType].
         * @param value the value.
         * @return the [SpType] representing this value; `null` if the value does not represent any [SpGeometry].
         */
        @JvmStatic
        @JsStatic
        fun ofDefined(value: String?): SpType? = if (value != null) getDefined(value, SpType::class) else null

        @JvmField
        @JsStatic
        val Point = def(SpType::class, "Point") { self -> self._klass = SpPoint::class }

        @JvmField
        @JsStatic
        val MultiPoint = def(SpType::class, "MultiPoint") { self -> self._klass = SpMultiPoint::class }

        @JvmField
        @JsStatic
        val LineString = def(SpType::class, "LineString") { self -> self._klass = SpLineString::class }

        @JvmField
        @JsStatic
        val MultiLineString = def(SpType::class, "MultiLineString") { self -> self._klass = SpMultiLineString::class }

        @JvmField
        @JsStatic
        val Polygon = def(SpType::class, "Polygon") { self -> self._klass = SpPolygon::class }

        @JvmField
        @JsStatic
        val MultiPolygon = def(SpType::class, "MultiPolygon") { self -> self._klass = SpMultiPolygon::class }

        @JvmField
        @JsStatic
        val GeometryCollection = def(SpType::class, "GeometryCollection") { self -> self._klass = SpGeometryCollection::class }
    }

    /**
     * Tests if the given object is a geometry of this type, to be done before casting via [asPoint][SpGeometry.asPoint], `...`.
     * @param any the object to test.
     * @return `true` if the given object is a geometry; `false` otherwise.
     */
    fun isType(any: Any?): Boolean {
        if (!isDefined) return false
        val typeName = this.toString()
        if (any !is AnyObject) return false
        return any.getRaw("type") == typeName
    }

    private var _klass: KClass<out SpGeometry>? = null

    /**
     * The [KClass] referring to the [SpGeometry] type; access throws an exception, when this not [isDefined].
     */
    val klass: KClass<out SpGeometry>
        get() = _klass!!
}