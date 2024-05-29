package com.here.naksha.lib.base

/**
 * A default implementation of an iterator above a Naksha object or array.
 * @param <K> The key-type.
 * @param <V> The value-type.
 */
class KtIterator<K, V>(private val it: N_Iterator<K, V>) : Iterator<P_Entry<K, V>> {
    private var _loaded: Boolean? = null
    private lateinit var _element: P_Entry<K, V>

    override fun hasNext(): Boolean {
        var loaded = _loaded
        if (loaded == null) {
            loaded = it.loadNext()
            if (loaded) {
                if (!this::_element.isInitialized) {
                    _element = P_Entry(it.getKey(), it.getValue())
                } else {
                    _element.key = it.getKey()
                    _element.value = it.getValue()
                }
            }
            _loaded = loaded
        }
        return loaded
    }

    override fun next(): P_Entry<K, V> {
        require(hasNext())
        _loaded = null
        return _element
    }

}