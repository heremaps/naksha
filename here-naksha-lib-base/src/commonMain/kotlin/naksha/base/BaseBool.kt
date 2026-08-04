package naksha.base

/**
 * A mutable boolean.
 * @since 3.0
 */
expect class BaseBool(value: Boolean): AbstractBase {
    override fun withAtomic(enable: Boolean): BaseBool
    override fun withReadOnly(enable: Boolean): BaseBool
    override fun withImmutable(enable: Boolean): BaseBool
    fun get(): Boolean
    fun set(value: Boolean)
    fun compareAndSet(expect: Boolean, update: Boolean): Boolean
}