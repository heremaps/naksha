package naksha.base

import java.lang.invoke.MethodHandles
import java.lang.invoke.VarHandle

/**
 * JVM actual of [BaseRef].
 * @since 3.0
 */
actual open class BaseRef<R : Any> actual constructor(initialValue: R?) : AbstractBase() {
    actual override fun withAtomic(enable: Boolean): BaseRef<R> = super.withAtomic(enable) as BaseRef<R>
    actual override fun withReadOnly(enable: Boolean): BaseRef<R> = super.withReadOnly(enable) as BaseRef<R>
    actual override fun withImmutable(enable: Boolean): BaseRef<R> = super.withImmutable(enable) as BaseRef<R>

    companion object BaseRef_C {
        private val refHandle: VarHandle = MethodHandles
            .privateLookupIn(BaseRef::class.java, MethodHandles.lookup())
            .findVarHandle(BaseRef::class.java, "ref", Any::class.java)
    }
    private var ref: R? = initialValue

    actual open fun get(): R? = ref

    actual open fun set(newValue: R?) {
        startMutate()
        if (atomic) refHandle.setVolatile(newValue) else ref = newValue
    }

    @Suppress("UNCHECKED_CAST")
    actual open fun getAndSet(newValue: R?): R? {
        startMutate()
        if (atomic) return refHandle.getAndSet(this, newValue) as R?
        val old = this.ref
        ref = newValue
        return old
    }

    actual open fun compareAndSet(expectedValue: R?, newValue: R?): Boolean {
        startMutate()
        if (atomic) return refHandle.compareAndSet(this, expectedValue, newValue)
        val current = this.ref
        if (current != expectedValue) return false
        ref = newValue
        return true
    }

}

