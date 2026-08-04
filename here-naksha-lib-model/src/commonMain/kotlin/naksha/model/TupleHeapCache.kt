@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package naksha.model

import naksha.base.Int64
import naksha.base.TupleNumber

/**
 * A cache in the Java heap for [Tuple]'s, used as default cache by the [Naksha.cache]. This cache is a mandatory first level cache, the application can install second or third level caches.
 * @since 3.0
 */
expect class TupleHeapCache(): ITupleCache {
    override val latencyInMicros: Int64
    override operator fun get(tupleNumber: TupleNumber): Tuple?
    override fun load(tuples: Array<Tuple?>, tupleNumbers: Array<TupleNumber>, from:Int, to:Int, maxMicros: Int64?): Int
    override fun put(tuple: Tuple)
    override fun store(vararg tuples: Tuple?)
    override fun clear()
    override fun gc()
}