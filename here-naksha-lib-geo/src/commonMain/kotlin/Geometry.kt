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
    }

    fun getType(): String = getOrNull(TYPE, Klass.stringKlass)!!
    fun setType(type: String) = set(TYPE, type)

    fun <T: BaseArray<*>> getCoordinates(): T? = getOrNull(COORDINATES, BaseArray.klass) as T?
    fun <T: BaseArray<*>> setCoordinates(value: T) = set(COORDINATES, value)
}