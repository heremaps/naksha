package com.here.naksha.lib.base

/**
 * Simple implementation of the [Iterable] interface for all Naksha types.
 */
class KtIterable<K, V>(private val it: PIterator<K, V>) : Iterable<RawPair<K, V>> {
    override fun iterator(): Iterator<RawPair<K, V>> = KtIterator(it)
}