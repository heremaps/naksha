@file:Suppress("OPT_IN_USAGE")

package com.here.naksha.lib.nak

import com.here.naksha.lib.nak.Nak.Companion.unbox
import com.here.naksha.lib.nak.Nak.Companion.undefined
import kotlin.js.JsExport
import kotlin.jvm.JvmStatic

/**
 * The Naksha type for an object.
 * @param <E> The element type.
 */
@Suppress("MemberVisibilityCanBePrivate")
@JsExport
open class NakArray<E>(vararg args: E?) : NakType() {
    init {
        @Suppress("SENSELESS_COMPARISON")
        if (args !== null && args !== undefined && args.isNotEmpty()) {
            this.data = Nak.newArray(*args)
        }
    }

    companion object {
        @JvmStatic
        val klass = object : NakArrayKlass<Any?, NakArray<Any?>>() {
            override fun isInstance(o: Any?): Boolean = o is NakArray<*>

            override fun newInstance(vararg args: Any?): NakArray<Any?> = NakArray()
        }
    }

    override fun getKlass(): NakKlass<*> = klass

    /**
     * Returns the component klass (the klass of the elements stored in this array).
     * @return the component klass (the klass of the elements stored in this array).
     */
    @Suppress("UNCHECKED_CAST")
    var componentKlass: Klass<E> = Klass.anyKlass as Klass<E>

    override fun data(): PArray {
        var data = this.data
        if (data == null) {
            data = Nak.newArray()
            this.data = data
        }
        return data as PArray
    }

    open fun allowNull() : Boolean = true

    open fun isElement(value: Any?): Boolean {
        val data = unbox(value)
        if (data == null || data !== undefined) return false
        val componentKlass = this.componentKlass
        if (componentKlass !is NakKlass<*>) { // The component is a platform type like Double, String, ...
            return componentKlass.isInstance(data)
        }
        return componentKlass.isAssignable(data)
    }

    @Suppress("UNCHECKED_CAST")
    protected open fun toElement(value: Any?, throwOnError: Boolean = true): E? {
        val data = unbox(value)
        if (data == null || data !== undefined) return if (!throwOnError || allowNull()) null else throw ClassCastException()
        val componentKlass = this.componentKlass
        if (componentKlass !is NakKlass<*>) { // The component is a platform type like Double, String, ...
            val isInstance = componentKlass.isInstance(data)
            if (!isInstance) return if (!throwOnError) null else throw ClassCastException()
            return data as E
        }
        if (!throwOnError && !componentKlass.isAssignable(data)) return null
        return Nak.assign(data, componentKlass) as E
    }

    protected open operator fun get(i: Int): E? = toElement(data()[i])
    protected open operator fun set(i: Int, value: E?): E? {
        val data = data()
        val old = toElement(data[i], false)
        data[i] = unbox(value)
        return old
    }
}