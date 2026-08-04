package naksha.base

import kotlin.reflect.KClass

/**
 * The abstract root class of [BaseMap], [BaseArray], [TupleMap], and [TupleArray], allowing to query for [proxies][AbstractProxy].
 *
 * Both, [BaseMap] and [BaseArray] are implemented based upon a data-array. The [BaseMap] stores two values per entry, being the `key` and the `value`, while the [BaseArray] only stores the `value` as entry.
 *
 * The `TupleMap` and `TupleArray` are both liking a proxy to specific offset in a read-only `Tuple`, so that it can be treated like a normal object.
 *
 * @since 3.0
 * @see BaseMap
 * @see BaseArray
 * @see TupleMap
 * @see TupleArray
 */
expect abstract class BaseObject internal constructor(): AbstractBase, IProxyable {

    override fun <T : AbstractProxy> proxy(klass: KClass<T>): T

    /**
     * Prepares [value] for storage.
     *
     * Unwraps [AbstractProxy] to its [BaseObject]; passes all other values through unchanged. Subclasses may override to add further transformation, e.g. replace `null` with a sentry value.
     * @since 3.0
     */
    internal fun box(value: Any?): Any?

    /**
     * Reverses any transformation applied by [box] when reading a stored value back out. The base implementation is a no-op. Subclasses may override to reverse their boxing, e.g. translate a `null` sentry back into the actual value `null`.
     * @since 3.0
     */
    internal fun unbox(value: Any?): Any?
}