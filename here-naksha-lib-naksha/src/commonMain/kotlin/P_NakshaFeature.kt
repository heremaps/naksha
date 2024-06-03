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
        val REFERENCE_POINT = Platform.intern("referencePoint")
    }

    override fun getProperties(): P_NakshaProperties = getOrCreate(PROPERTIES, P_NakshaProperties::class)

    /**
     * Set reference point of the feature. Used for grid calculation.
     */
    fun setReferencePoint(referencePoint: P_Point?) = set(REFERENCE_POINT, referencePoint)

    /**
     * Reference point of the feature. Used for grid calculation.
     */
    fun getReferencePint(): P_Point? = getOrNull(REFERENCE_POINT, P_Point::class)
}