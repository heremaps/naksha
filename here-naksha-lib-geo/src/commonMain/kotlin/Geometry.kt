@file:Suppress("OPT_IN_USAGE")

package com.here.naksha.lib.base

import kotlin.js.JsExport
import kotlin.jvm.JvmStatic

/**
 *
 */
@JsExport
class Geometry(vararg args: Any?)  : BaseObject(*args) {

    companion object {
        @JvmStatic
        val klass = object : BaseObjectKlass<Geometry>() {
            override fun isInstance(o: Any?): Boolean = o is Geometry

            override fun newInstance(vararg args: Any?): Geometry = Geometry()
        }

        @JvmStatic
        val TYPE = Base.intern("type")

        @JvmStatic
        val COORDINATES = Base.intern("coordinates")

        @JvmStatic
        val POINT_TYPE = "Point"

        @JvmStatic
        val LINE_STRING_TYPE = "LineString"

        @JvmStatic
        val MULTI_POINT_TYPE = "MultiPoint"

        @JvmStatic
        val MULTI_LINE_STRING_TYPE = "MultiLineString"

        @JvmStatic
        val MULTI_POLYGON_TYPE = "MultiPolygon"

        @JvmStatic
        val POLYGON_TYPE = "Polygon"
    }

    fun getType(): String = getOrNull(TYPE, Klass.stringKlass)!!
    fun setType(type: String) = set(TYPE, type)

    fun <T: BaseArray<*>> getCoordinates(): T? = getOrNull(COORDINATES, BaseArray.klass) as T?
    fun <T: BaseArray<*>> setCoordinates(value: T) = set(COORDINATES, value)
}