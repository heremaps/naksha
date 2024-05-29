package com.here.naksha.lib.base

class JvmPObjectIterator(obj: JvmPObject) : N_Iterator<String, Any?> {
    private val it = obj.properties?.iterator()
    private var _loaded : Boolean? = null
    private var _entry:  MutableMap.MutableEntry<String, Any?>? = null

    override fun loadNext(): Boolean {
        if (it == null || _loaded == false) return false
        if (it.hasNext()) {
            _loaded = true
            _entry = it.next()
            return true
        }
        _loaded = false
        _entry = null
        return false
    }

    override fun isLoaded(): Boolean = _loaded == true

    override fun getKey(): String {
        require(_loaded == true)
        return _entry!!.key
    }

    override fun getValue(): Any? {
        require(_loaded == true)
        return _entry!!.value
    }
}