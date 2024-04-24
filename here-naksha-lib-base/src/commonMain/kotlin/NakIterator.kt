package com.here.naksha.lib.nak

/**
 * A default implementation of an iterator above a Naksha object or array.
 * @param <K> The key-type.
 * @param <V> The value-type.
 */
class NakIterator<K, V>(private val it: PIterator<K, V>) : Iterator<NakPair<K, V>> {
    private var _loaded: Boolean? = null
    private lateinit var _element: NakPair<K, V>

    override fun hasNext(): Boolean {
        var loaded = _loaded
        if (loaded == null) {
            loaded = it.loadNext()
            if (loaded) {
                if (!this::_element.isInitialized) {
                    _element = NakPair(it.getKey(), it.getValue())
                } else {
                    _element.key = it.getKey()
                    _element.value = it.getValue()
                }
            }
            _loaded = loaded
        }
        return loaded
    }

    override fun next(): NakPair<K, V> {
        require(hasNext())
        _loaded = null
        return _element
    }

}