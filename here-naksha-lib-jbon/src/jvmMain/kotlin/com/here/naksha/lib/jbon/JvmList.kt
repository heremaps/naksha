package com.here.naksha.lib.jbon

@Suppress("UNCHECKED_CAST")
class JvmList : IList {
    override fun isList(any: Any?): Boolean {
        return any is List<*>
    }

    override fun newList(): Any {
        return ArrayList<Any?>()
    }

    override fun size(list: Any): Int {
        require(list is List<*>)
        return list.size
    }

    override fun setSize(list: Any, size: Int) {
        require(list is MutableList<*>)
        require(size >= 0)
        if (size == 0) {
            clear(list)
            return
        }
        val casted = list as MutableList<Any?>
        while (casted.size > size) {
            casted.removeLastOrNull()
        }
        while (casted.size < size) {
            casted.add(null)
        }
    }

    override fun get(list: Any, index: Int): Any? {
        require(list is List<*>)
        require(index in 0..<list.size)
        return list[index]
    }

    override fun set(list: Any, index: Int, value: Any?): Any? {
        require(list is MutableList<*>)
        val casted = list as MutableList<Any?>
        require(index in 0..<list.size)
        val old = list[index]
        list[index] = value
        return old
    }

    override fun add(list: Any, vararg values: Any?) {
        require(list is MutableList<*>)
        val casted = list as MutableList<Any?>
        casted.addAll(values)
    }

    override fun splice(list: Any, start: Int, delete: Int, vararg add: Any?): Any? {
        TODO("Not yet implemented")
    }

    override fun clear(list: Any) {
        require(list is MutableList<*>)
        list.clear()
    }
}