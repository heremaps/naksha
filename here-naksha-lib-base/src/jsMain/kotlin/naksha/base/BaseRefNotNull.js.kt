package naksha.base

/**
 * JS actual of [BaseRefNotNull].
 * @since 3.0
 */
@JsExport
actual open class BaseRefNotNull<R : Any> actual constructor(initialValue: R): AbstractBase() {
    actual override fun withAtomic(enable: Boolean): BaseRefNotNull<R> = super.withAtomic(enable) as BaseRefNotNull<R>
    actual override fun withReadOnly(enable: Boolean): BaseRefNotNull<R> = super.withReadOnly(enable) as BaseRefNotNull<R>
    actual override fun withImmutable(enable: Boolean): BaseRefNotNull<R> = super.withImmutable(enable) as BaseRefNotNull<R>

    private var ref: R = initialValue

    actual open fun get(): R = ref

    actual open fun set(newValue: R) {
        startMutate()
        ref = newValue
    }

    actual open fun getAndSet(newValue: R): R {
        startMutate()
        val old = ref
        ref = newValue
        return old
    }

    actual open fun compareAndSet(expectedValue: R, newValue: R): Boolean {
        startMutate()
        if (ref === expectedValue) {
            ref = newValue
            return true
        }
        return false
    }
}
