package naksha.base

/**
 * A non-null reference.
 * @since 3.0
 */
expect open class BaseRefNotNull<R : Any>(initialValue: R): AbstractBase {
    override fun withAtomic(enable: Boolean): BaseRefNotNull<R>
    override fun withReadOnly(enable: Boolean): BaseRefNotNull<R>
    override fun withImmutable(enable: Boolean): BaseRefNotNull<R>
    open fun get(): R
    open fun set(newValue: R)
    open fun getAndSet(newValue: R): R
    open fun compareAndSet(expectedValue: R, newValue: R): Boolean
}