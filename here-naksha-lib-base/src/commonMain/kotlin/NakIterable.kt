package com.here.naksha.lib.nak

/**
 * Simple implementation of the [Iterable] interface for all Naksha types.
 */
class NakIterable<K, V>(private val it: PIterator<K, V>) : Iterable<NakPair<K, V>> {
    override fun iterator(): Iterator<NakPair<K, V>> = NakIterator(it)
}