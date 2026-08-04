package naksha.base

/**
 * JS actual of [BaseRef].
 * @since 3.0
 */
@JsExport
actual open class BaseRef<R : Any> actual constructor(initialValue: R?): AbstractBase() {
    actual override fun withAtomic(enable: Boolean): BaseRef<R> = super.withAtomic(enable) as BaseRef<R>
    actual override fun withReadOnly(enable: Boolean): BaseRef<R> = super.withReadOnly(enable) as BaseRef<R>
    actual override fun withImmutable(enable: Boolean): BaseRef<R> = super.withImmutable(enable) as BaseRef<R>

    private var ref: R? = initialValue

    actual open fun get(): R? = ref

    actual open fun set(newValue: R?) {
        startMutate()
        ref = newValue
    }

    actual open fun getAndSet(newValue: R?): R? {
        startMutate()
        val old = ref
        ref = newValue
        return old
    }

    actual open fun compareAndSet(expectedValue: R?, newValue: R?): Boolean {
        startMutate()
        if (ref === expectedValue) {
            ref = newValue
            return true
        }
        return false
    }
}
