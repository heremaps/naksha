@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package naksha.base

import kotlin.reflect.KClass

/**
 * Base class for all [proxies][Proxy].
 *
 * The primary constructor is called via reflection and links a proxy to an existing [base object][BaseObject]. Therefore, all extending classes must have a primary constructor that accepts a single parameter being [BaseObject].
 *
 * The [AbstractProxy] only exists to split shared common code _(located
 * @since 3.0
 * @see Proxy
 */
expect open class AbstractProxy internal constructor(baseObject: BaseObject): IProxyable {
    /**
     * The underlying object as given to the primary constructor. This is the underlying object to which this proxy is bound.
     *
     * It mainly should be tested for [IMap], [IMutableMap], [IArray] or [IMutableArray] to stay compatible with all possible implementations. However, in rare cases a tests for a concrete implementation like [BaseMap] or [BaseArray] can be helpful.
     * @see IMap
     * @see IMutableMap
     * @see IArray
     * @see IMutableArray
     * @see BaseMap
     * @see BaseArray
     */
    val baseObject: BaseObject

    override fun <T : AbstractProxy> proxy(klass: KClass<T>): T
}