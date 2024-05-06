@file:Suppress("OPT_IN_USAGE")

package com.here.naksha.lib.base

import kotlin.js.JsExport
import kotlin.jvm.JvmStatic

/**
 * The Naksha Feature extending the default [GeoFeature].
 */
@JsExport
open class NakFeature(vararg args: Any?) : GeoFeature(*args) {

    companion object {
        @JvmStatic
        val klass = object : BaseObjectKlass<NakFeature>() {
            override fun isInstance(o: Any?): Boolean = o is NakFeature

            override fun newInstance(vararg args: Any?): NakFeature = NakFeature()
        }
    }

    override fun klass(): BaseKlass<*> = klass

    override fun getProperties(): NakProperties = getOrCreate(PROPERTIES, NakProperties.klass)

}