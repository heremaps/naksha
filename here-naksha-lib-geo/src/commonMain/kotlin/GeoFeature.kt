@file:Suppress("OPT_IN_USAGE")

package com.here.naksha.lib.base

import com.here.naksha.lib.base.Platform.Companion.stringKlass
import kotlin.js.JsExport
import kotlin.jvm.JvmStatic

/**
 *
 */
@JsExport
open class GeoFeature : P_Object() {

    companion object {
        @JvmStatic
        val ID = Platform.intern("id")

        @JvmStatic
        val PROPERTIES = Platform.intern("properties")
    }

    open fun getId(): String? = getOrNull(ID, stringKlass)
    open fun setId(id: String?) = set(ID, id)

    open fun useProperties(): P_Object  = getOrCreate(PROPERTIES, P_Object::class)
    open fun getProperties(): P_Object? = getOrNull(PROPERTIES, P_Object::class)
    open fun setProperties(p: P_Object?) = set(PROPERTIES, p)
}