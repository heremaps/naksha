@file:Suppress("OPT_IN_USAGE")

package com.here.naksha.lib.base

import com.here.naksha.lib.base.Klass.Companion.stringKlass
import kotlin.js.JsExport
import kotlin.jvm.JvmStatic

/**
 * Naksha service authorization.
 */
@JsExport
open class ArmNaksha(vararg args: Any?) : BaseObject(*args) {

    companion object {
        @JvmStatic
        val klass = object : BaseObjectKlass<ArmNaksha>() {
            override fun isInstance(o: Any?): Boolean = o is ArmNaksha

            override fun newInstance(vararg args: Any?): ArmNaksha = ArmNaksha()
        }
    }

    override fun klass(): BaseKlass<*> = klass
    // TODO: Add getWriteFeatures, useWriteFeatures, ...
}