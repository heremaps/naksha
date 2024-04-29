@file:Suppress("OPT_IN_USAGE")

package com.here.naksha.lib.nak

import com.here.naksha.lib.nak.Nak.Companion.unbox
import com.here.naksha.lib.nak.Nak.Companion.undefined
import kotlin.js.JsExport
import kotlin.jvm.JvmStatic

@Suppress("MemberVisibilityCanBePrivate")
@JsExport
open class NakMap<E>(vararg args: Any?) : NakElementType<E>() {
    init {
        @Suppress("SENSELESS_COMPARISON")
        if (args !== null && args !== undefined && args.isNotEmpty()) {
            this.data = Nak.newObject(*args)
        }
    }

    companion object {
        @JvmStatic
        val klass = object : NakMapKlass<Any?, NakMap<Any?>>() {
            override fun isInstance(o: Any?): Boolean = o is NakMap<*>

            override fun newInstance(vararg args: Any?): NakMap<Any?> = NakMap()
        }
    }

    override fun getKlass(): NakKlass<*> = klass

    override fun data(): PObject {
        var data = this.data
        if (data == null) {
            data = Nak.newObject()
            this.data = data
        }
        return data as PObject
    }

    protected open operator fun get(key: String): E? = toElement(data()[key])
    protected open operator fun set(key: String, value: E?): E? {
        val data = data()
        val old = toElement(data[key], false)
        data[key] = unbox(value)
        return old
    }
}
