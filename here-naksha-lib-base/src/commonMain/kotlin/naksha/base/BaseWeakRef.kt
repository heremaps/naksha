package naksha.base

/**
 * An atomic nullable reference.
 * @since 3.0
 */
expect open class BaseWeakRef<R : Any>(referent: R?): AbstractBase {
    override fun withAtomic(enable: Boolean): BaseWeakRef<R>
    override fun withReadOnly(enable: Boolean): BaseWeakRef<R>
    override fun withImmutable(enable: Boolean): BaseWeakRef<R>
    open fun get(): R?
    open fun set(newValue: R?)
    open fun getAndSet(newValue: R?): R?
    open fun compareAndSet(expectedValue: R?, newValue: R?): Boolean
}