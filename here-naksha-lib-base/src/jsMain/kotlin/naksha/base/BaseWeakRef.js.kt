package naksha.base

/**
 * JS actual of [BaseRef].
 * @since 3.0
 */
@JsExport
actual open class BaseWeakRef<R : Any> actual constructor(referent: R?): AbstractBase() {
    actual override fun withAtomic(enable: Boolean): BaseWeakRef<R> = super.withAtomic(enable) as BaseWeakRef<R>
    actual override fun withReadOnly(enable: Boolean): BaseWeakRef<R> = super.withReadOnly(enable) as BaseWeakRef<R>
    actual override fun withImmutable(enable: Boolean): BaseWeakRef<R> = super.withImmutable(enable) as BaseWeakRef<R>

    companion object BaseWeakRef_C {
        internal fun <R: Any> newWeakRef(referent: R?): WeakRef<R> =
            js("try { return new WeakRef(referent); } catch(e) { return new WeakRef(Object(referent)); }").unsafeCast<WeakRef<R>>()
    }

    private var ref: WeakRef<R> = newWeakRef(referent)

    actual open fun get(): R? = ref.deref()

    actual open fun set(newValue: R?) {
        startMutate()
        val current = ref.deref()
        if (current === newValue) return
        ref = newWeakRef(newValue)
    }

    actual open fun getAndSet(newValue: R?): R? {
        startMutate()
        val old = ref.deref()
        if (old === newValue) return old
        ref = newWeakRef(newValue)
        return old
    }

    actual open fun compareAndSet(expectedValue: R?, newValue: R?): Boolean {
        startMutate()
        val current = ref.deref()
        if (current !== expectedValue) return false
        if (current !== newValue) {
            ref = newWeakRef(newValue)
        }
        return true
    }
}
