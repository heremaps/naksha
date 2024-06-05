package com.here.naksha.lib.base

import com.here.naksha.lib.base.Platform.Companion.isAssignableFrom
import kotlin.reflect.KClass
import kotlin.reflect.jvm.jvmErasure

/**
 * The JVM implementation of a [PlatformList].
 */
open class JvmList(vararg entries: Any?) : JvmObject(), MutableList<Any?>, PlatformList {
    /**
     * The payload of the array.
     */
    var data: ArrayList<Any?>?

    init {
        val a: ArrayList<Any?>?
        if (entries.isNotEmpty()) {
            a = ArrayList(entries.size + 4)
            a.addAll(entries)
        } else {
            a = null
        }
        data = a
    }

    open fun data(): ArrayList<Any?> {
        var d = data
        if (d == null) {
            d = ArrayList()
            data = d
        }
        return d
    }

    override val size: Int = data?.size ?: 0

    override fun clear() {
        data = null
    }

    override fun addAll(elements: Collection<Any?>): Boolean = data().addAll(elements)

    override fun addAll(index: Int, elements: Collection<Any?>): Boolean = data().addAll(index, elements)

    override fun add(index: Int, element: Any?) {
        data().add(index, element)
    }

    override fun add(element: Any?): Boolean = data().add(element)

    override fun containsAll(elements: Collection<Any?>): Boolean = data?.containsAll(elements) ?: false

    override operator fun contains(element: Any?): Boolean {
        if (data?.contains(element) == true) return true
        if (element is String) return super.contains(element)
        if (element is Symbol) return super.contains(element)
        return false
    }

    override operator fun get(index: Int): Any? {
        val d = data
        if (d == null || index < 0 || index >= d.size) return null
        return d[index]
    }

    override fun isEmpty(): Boolean = data?.isEmpty() ?: true

    override fun iterator(): MutableIterator<Any?> = data().iterator()

    override fun listIterator(): MutableListIterator<Any?> = data().listIterator()

    override fun listIterator(index: Int): MutableListIterator<Any?> = data().listIterator(index)

    override fun removeAt(index: Int): Any? {
        val d = data
        if (d == null || index < 0 || index >= d.size) return null
        return d.remove(index)
    }

    override fun subList(fromIndex: Int, toIndex: Int): MutableList<Any?> = data().subList(fromIndex, toIndex)

    @Suppress("ConvertArgumentToSet")
    override fun retainAll(elements: Collection<Any?>): Boolean = data?.retainAll(elements) ?: false

    @Suppress("ConvertArgumentToSet")
    override fun removeAll(elements: Collection<Any?>): Boolean = data?.removeAll(elements) ?: false

    override fun remove(element: Any?): Boolean {
        if (element is Int) return data?.remove(element) ?: false
        if (element is String) return super.remove(element)
        if (element is Symbol) return super.remove(element)
        return false
    }

    override fun lastIndexOf(element: Any?): Int = data?.lastIndexOf(element) ?: -1

    override fun indexOf(element: Any?): Int = data?.indexOf(element) ?: -1

    override operator fun set(index: Int, element: Any?): Any? {
        require(index >= 0) { "Illegal index $index, must be positive number" }
        val d = data()
        val old: Any? = if (index < d.size) d[index] else null
        while (index >= d.size) d.add(null)
        d[index] = element
        return old
    }

    override fun <E : Any, T : P_List<E>, C : P_List<*>> proxy(klass: KClass<C>, elementKlass: KClass<out E>?, doNotOverride: Boolean): T {
        val symbol = Symbols.symbolOf(klass)
        var proxy = get(symbol)
        if (proxy != null) {
            if (isAssignableFrom(klass, proxy::class)) return proxy as T
            if (doNotOverride) throw IllegalStateException("The symbol $symbol is already bound to incompatible type")
        }
        val constructors = klass.constructors
        for (constructor in constructors) {
            if (constructor.parameters.isEmpty()) {
                proxy = constructor.call()
                break
            }
            if (constructor.parameters.size == 1) {
                val pKlass = constructor.parameters[0].type.jvmErasure
                if (isAssignableFrom(elementKlass as KClass<*>, pKlass)) {
                    proxy = constructor.call(elementKlass)
                    break
                }
            }
        }
        if (proxy == null) throw IllegalArgumentException("Failed to create instance of $klass($elementKlass)")
        return proxy as T
    }
}