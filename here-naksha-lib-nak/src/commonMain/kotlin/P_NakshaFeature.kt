@file:Suppress("OPT_IN_USAGE")

package com.here.naksha.lib.base

import kotlin.js.JsExport
import kotlin.jvm.JvmStatic

/**
 * The Naksha Feature extending the default [GeoFeature].
 */
@JsExport
open class P_NakshaFeature() : GeoFeature() {

    companion object {
        @JvmStatic
        val klass = object : BaseObjectKlass<P_NakshaFeature>() {
            override fun isInstance(o: Any?): Boolean = o is P_NakshaFeature

            override fun newInstance(vararg args: Any?): P_NakshaFeature = P_NakshaFeature()
        }

        @JvmStatic
        val REFERENCE_POINT = Base.intern("referencePoint")
    }

    override fun klass(): BaseKlass<*> = klass

    override fun getProperties(): P_NakshaProperties = getOrCreate(PROPERTIES, P_NakshaProperties.klass)

    /**
     * Set reference point of the feature. Used for grid calculation.
     */
    fun setReferencePoint(referencePoint: P_Point?) = set(REFERENCE_POINT, referencePoint)

    /**
     * Reference point of the feature. Used for grid calculation.
     */
    fun getReferencePint(): P_Point? = getOrNull(REFERENCE_POINT, P_Point.klass)
}