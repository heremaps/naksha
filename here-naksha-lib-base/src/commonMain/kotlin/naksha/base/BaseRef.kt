package naksha.base

/**
 * An atomic nullable reference.
 * @since 3.0
 */
expect open class BaseRef<R : Any>(initialValue: R?): AbstractBase {
    override fun withAtomic(enable: Boolean): BaseRef<R>
    override fun withReadOnly(enable: Boolean): BaseRef<R>
    override fun withImmutable(enable: Boolean): BaseRef<R>
    open fun get(): R?
    open fun set(newValue: R?)
    open fun getAndSet(newValue: R?): R?
    open fun compareAndSet(expectedValue: R?, newValue: R?): Boolean
}