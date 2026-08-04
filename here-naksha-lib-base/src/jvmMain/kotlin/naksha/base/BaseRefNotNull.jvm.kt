package naksha.base

import java.lang.invoke.MethodHandles
import java.lang.invoke.VarHandle
import java.util.concurrent.atomic.AtomicReference

/**
 * JVM actual of [BaseRefNotNull]. Backed by [AtomicReference].
 * @since 3.0
 */
actual open class BaseRefNotNull<R : Any> actual constructor(initialValue: R): AbstractBase() {
    actual override fun withAtomic(enable: Boolean): BaseRefNotNull<R> = super.withAtomic(enable) as BaseRefNotNull<R>
    actual override fun withReadOnly(enable: Boolean): BaseRefNotNull<R> = super.withReadOnly(enable) as BaseRefNotNull<R>
    actual override fun withImmutable(enable: Boolean): BaseRefNotNull<R> = super.withImmutable(enable) as BaseRefNotNull<R>

    companion object BaseRefNotNull_C {
        private val refHandle: VarHandle = MethodHandles
            .privateLookupIn(BaseRefNotNull::class.java, MethodHandles.lookup())
            .findVarHandle(BaseRefNotNull::class.java, "ref", Any::class.java)
    }
    private var ref: R = initialValue

    actual open fun get(): R = ref

    actual open fun set(newValue: R) {
        startMutate()
        if (atomic) refHandle.setVolatile(newValue) else ref = newValue
    }

    @Suppress("UNCHECKED_CAST")
    actual open fun getAndSet(newValue: R): R {
        startMutate()
        if (atomic) return refHandle.getAndSet(this, newValue) as R
        val old = this.ref
        ref = newValue
        return old
    }

    actual open fun compareAndSet(expectedValue: R, newValue: R): Boolean {
        startMutate()
        if (atomic) return refHandle.compareAndSet(this, expectedValue, newValue)
        val current = this.ref
        if (current != expectedValue) return false
        ref = newValue
        return true
    }
}
