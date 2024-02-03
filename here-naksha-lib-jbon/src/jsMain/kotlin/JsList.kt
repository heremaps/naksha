package com.here.naksha.lib.jbon

@Suppress("UnsafeCastFromDynamic")
class JsList : IList {
    override fun isList(any: Any?): Boolean {
        return js("any !== undefined && any !== null && Array.isArray(any)");
    }

    override fun newList(): Any {
        return js("[]")
    }

    override fun size(list: Any): Int {
        require(isList(list))
        return js("list.length")
    }

    override fun setSize(list: Any, size: Int) {
        require(isList(list))
        js("list.length = size")
    }

    override fun get(list: Any, index: Int): Any? {
        require(isList(list))
        return js("list[index] || null")
    }

    override fun set(list: Any, index: Int, value: Any?): Any? {
        require(isList(list))
        val old = get(list, index)
        js("list[index]=value")
        return old
    }

    override fun add(list: Any, vararg values: Any?) {
        require(isList(list))
        js("list.concat(values)")
    }

    override fun splice(list: Any, start: Int, deleteAmount: Int, vararg add: Any?): Any? {
        require(isList(list))
        return js("list.splice(list, start, deleteAmount, add)")
    }

    override fun clear(list: Any) {
        require(isList(list))
        js("list.length=0")
    }
}