package com.here.naksha.lib.nak

import kotlin.jvm.JvmStatic

class XyzNs(raw: PObject) : NakObject(raw) {
    companion object {
        @JvmStatic
        val klass = object : NakClass<PObject, XyzNs>() {
            override fun symbol(): PSymbol = Nak.NAK_SYM

            override fun canCast(o: Any?): Boolean = Nak.isObject(o)

            override fun isInstance(o: Any?): Boolean = o is XyzNs

            override fun create(o: PObject): XyzNs = XyzNs(o)

        }
    }

    override fun nakClass(): NakClass<PObject, *> = klass

    fun getAction(): String = data["action"] as? String ?: "CREATE"
    fun setAction(action: String?): String {
        val old = getAction()
        data["action"] = action
        return old
    }

}