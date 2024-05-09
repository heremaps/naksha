@file:Suppress("OPT_IN_USAGE")

package com.here.naksha.lib.base

import com.here.naksha.lib.base.Klass.Companion.stringKlass
import kotlin.js.JsExport
import kotlin.jvm.JvmStatic

/**
 *
 */
@JsExport
open class AuthMatrix(vararg args: Any?) : BaseObject(*args) {

    companion object {
        @JvmStatic
        val klass = object : BaseObjectKlass<AuthMatrix>() {
            override fun isInstance(o: Any?): Boolean = o is AuthMatrix

            override fun newInstance(vararg args: Any?): AuthMatrix = AuthMatrix()
        }

        @JvmStatic
        val NAKSHA = "naksha"
    }

    override fun klass(): BaseKlass<*> = klass

    open fun getNaksha(): ArmNaksha? = getOr(NAKSHA, ArmNaksha.klass, null)
    open fun useNaksha(): ArmNaksha = super.getOrCreate(NAKSHA, ArmNaksha.klass)
    open fun setNaksha(naksha: ArmNaksha?) = set(NAKSHA, naksha)
}