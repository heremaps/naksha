package com.here.naksha.lib.nak

import kotlin.jvm.JvmStatic

class XyzNs : NakObject() {
    companion object {
        @JvmStatic
        val klass = object : NakObjectKlass<XyzNs>() {
            override fun isInstance(o: Any?): Boolean = o is XyzNs

            override fun newInstance(vararg args: Any?): XyzNs = XyzNs()

        }
    }

    override fun getKlass(): NakKlass<*> = klass

    fun getAction(): String = data()["action"] as? String ?: "CREATE"
    fun setAction(action: String?): String {
        val old = getAction()
        data()["action"] = action
        return old
    }

}