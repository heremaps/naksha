@file:Suppress("OPT_IN_USAGE")

package naksha.base

import kotlin.js.JsExport
import kotlin.math.max
import kotlin.math.min

@JsExport
internal class ListProxyMutableIterator<E> internal constructor(private val list: ListProxy<E>, private var current: Int): MutableListIterator<E?> {
    private val last: Int
        get() = list.size - 1

    override fun add(element: E?) {
        list.add(element)
    }

    override fun hasNext(): Boolean = current < last

    override fun hasPrevious(): Boolean = current >= 1

    override fun next(): E? {
        val i = current + 1
        if (i < list.size) {
            current = i
            return list[i]
        }
        throw NoSuchElementException()
    }

    override fun nextIndex(): Int = min(current + 1, list.size)

    override fun previous(): E? {
        val i = current - 1
        if (i >= 0) {
            current = i
            return list[i]
        }
        throw NoSuchElementException()
    }

    override fun previousIndex(): Int = max(current - 1, -1)

    override fun remove() {
        if (current >= 0 && current < list.size) {
            list.removeAt(current)
        }
    }

    override fun set(element: E?) {
        if (current >= 0 && current < list.size) {
            list[current] = element
        }
    }
}