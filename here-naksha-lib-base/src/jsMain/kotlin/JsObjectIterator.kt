package com.here.naksha.lib.nak

class JsObjectIterator(private val o: dynamic) : PIterator<String, Any?> {
    private val it = js("Object.keys(o)[Symbol.iterator]()")
    private var n: dynamic = null

    override fun loadNext(): Boolean {
        n = it.next();
        return n.done.unsafeCast<Boolean>()
    }

    override fun isLoaded(): Boolean = n != null

    override fun getKey(): String {
        require(n != null)
        return n.value.unsafeCast<String>()
    }

    override fun getValue(): Any? {
        require(n != null)
        return o[n.value.unsafeCast<String>()]
    }
}