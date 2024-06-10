package naksha.base

import kotlin.reflect.KClass
import kotlin.reflect.full.primaryConstructor

/**
 * The JVM implementation of a [PlatformList].
 */
open class JvmList(vararg entries: Any?) : JvmObject(), MutableList<Any?>, PlatformList {
    /**
     * The payload of the array.
     */
    internal var list: ArrayList<Any?>?

    init {
        val list: ArrayList<Any?>?
        if (entries.isNotEmpty()) {
            list = ArrayList(entries.size + 4)
            list.addAll(entries)
        } else {
            list = null
        }
        this.list = list
    }

    open fun list(): ArrayList<Any?> {
        var list = this.list
        if (list == null) {
            list = ArrayList()
            this.list = list
        }
        return list
    }

    override val size: Int
        get() = list?.size ?: 0

    override fun clear() {
        list?.clear()
    }

    override fun addAll(elements: Collection<Any?>): Boolean = list().addAll(elements)

    override fun addAll(index: Int, elements: Collection<Any?>): Boolean = list().addAll(index, elements)

    override fun add(index: Int, element: Any?) {
        list().add(index, element)
    }

    override fun add(element: Any?): Boolean = list().add(element)

    override fun containsAll(elements: Collection<Any?>): Boolean = list?.containsAll(elements) ?: false

    override operator fun contains(element: Any?): Boolean = element != null && list?.contains(element) == true

    override operator fun get(index: Int): Any? {
        val d = list
        if (d == null || index < 0 || index >= d.size) return null
        return d[index]
    }

    override fun isEmpty(): Boolean = list?.isEmpty() ?: true

    override fun iterator(): MutableIterator<Any?> = list().iterator()

    override fun listIterator(): MutableListIterator<Any?> = list().listIterator()

    override fun listIterator(index: Int): MutableListIterator<Any?> = list().listIterator(index)

    override fun removeAt(index: Int): Any? {
        val d = list
        if (d == null || index < 0 || index >= d.size) return null
        return d.removeAt(index)
    }

    override fun subList(fromIndex: Int, toIndex: Int): MutableList<Any?> = list().subList(fromIndex, toIndex)

    @Suppress("ConvertArgumentToSet")
    override fun retainAll(elements: Collection<Any?>): Boolean = list?.retainAll(elements) ?: false

    @Suppress("ConvertArgumentToSet")
    override fun removeAll(elements: Collection<Any?>): Boolean = list?.removeAll(elements) ?: false

    override fun remove(element: Any?): Boolean {
        if (element is Int) return list?.remove(element) ?: false
        val list = this.list
        if (list != null) {
            for (i in 0..<list.size) {
                val e = list[i]
                if (e == element) {
                    list.removeAt(i)
                    return true
                }
            }
        }
        return false
    }

    override fun lastIndexOf(element: Any?): Int = list?.lastIndexOf(element) ?: -1

    override fun indexOf(element: Any?): Int = list?.indexOf(element) ?: -1

    override operator fun set(index: Int, element: Any?): Any? {
        require(index >= 0) { "Illegal index $index, must be positive number" }
        val d = list()
        val old: Any? = if (index < d.size) d[index] else null
        while (index >= d.size) d.add(null)
        d[index] = element
        return old
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T : P_List<*>> proxy(klass: KClass<T>, doNotOverride: Boolean): T {
        val symbol = Symbols.of(klass)
        var proxy = getSymbol(symbol)
        if (proxy != null) {
            if (klass.isInstance(proxy)) return proxy as T
            if (doNotOverride) throw IllegalStateException("The symbol $symbol is already bound to incompatible type")
        }
        proxy = klass.primaryConstructor!!.call()
        proxy.bind(this, symbol)
        return proxy
    }
}