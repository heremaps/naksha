package com.here.naksha.lib.base

/**
 * Simple implementation of the [Iterable] interface for all Naksha types.
 */
class KtIterable<K, V>(private val it: N_Iterator<K, V>) : Iterable<P_Entry<K, V>> {
    override fun iterator(): Iterator<P_Entry<K, V>> = KtIterator(it)
}