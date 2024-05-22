@file:Suppress("OPT_IN_USAGE")

package com.here.naksha.lib.base

import com.here.naksha.lib.base.Klass.Companion.stringKlass
import kotlin.js.JsExport
import kotlin.jvm.JvmStatic

/**
 *
 */
@JsExport
open class GeoFeature(vararg args: Any?) : BaseObject(*args) {

    companion object {
        @JvmStatic
        val klass = object : BaseObjectKlass<GeoFeature>() {
            override fun isInstance(o: Any?): Boolean = o is GeoFeature

            override fun newInstance(vararg args: Any?): GeoFeature = GeoFeature()
        }

        @JvmStatic
        val ID = Base.intern("id")

        @JvmStatic
        val PROPERTIES = Base.intern("properties")

        @JvmStatic
        val GEOMETRY = Base.intern("geometry")
    }

    override fun klass(): BaseKlass<*> = klass

    open fun getId(): String? = toElement(get(ID), stringKlass, null)
    open fun setId(id: String?) = set(ID, id)

    open fun useProperties(): BaseObject  = getOrCreate(PROPERTIES, BaseObject.klass)
    open fun getProperties(): BaseObject? = getOr(PROPERTIES, BaseObject.klass, null)
    open fun setProperties(p: BaseObject?) = set(PROPERTIES, p)

    open fun getGeometry(): Geometry? = getOrNull(GEOMETRY, Geometry.klass)
    open fun setGeometry(value: Geometry) = set(GEOMETRY, value)
}