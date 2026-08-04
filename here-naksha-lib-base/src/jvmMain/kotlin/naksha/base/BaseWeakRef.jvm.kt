package naksha.base

import java.lang.invoke.MethodHandles
import java.lang.invoke.VarHandle
import java.lang.ref.WeakReference

/**
 * JVM actual of [BaseRef].
 * @since 3.0
 */
actual open class BaseWeakRef<R : Any> actual constructor(referent: R?) : AbstractBase() {
    actual override fun withAtomic(enable: Boolean): BaseWeakRef<R> = super.withAtomic(enable) as BaseWeakRef<R>
    actual override fun withReadOnly(enable: Boolean): BaseWeakRef<R> = super.withReadOnly(enable) as BaseWeakRef<R>
    actual override fun withImmutable(enable: Boolean): BaseWeakRef<R> = super.withImmutable(enable) as BaseWeakRef<R>

    companion object BaseWeakRef_C {
        private val refHandle: VarHandle = MethodHandles
            .privateLookupIn(BaseWeakRef::class.java, MethodHandles.lookup())
            .findVarHandle(BaseWeakRef::class.java, "ref", WeakReference::class.java)
    }
    private var ref = WeakReference(referent)

    actual open fun get(): R? = ref.get()

    actual open fun set(newValue: R?) {
        startMutate()
        val ref = this.ref
        val current = ref.get()
        if (current === newValue) return
        val new_ref = WeakReference(newValue)
        if (atomic) refHandle.setVolatile(this, new_ref) else this.ref = new_ref
    }

    @Suppress("UNCHECKED_CAST")
    actual open fun getAndSet(newValue: R?): R? {
        startMutate()
        if (atomic) {
            while (true) {
                val ref = ref
                val current = ref.get()
                if (current === newValue) return current
                val new_ref = WeakReference(newValue)
                if (refHandle.compareAndSet(this, ref, new_ref)) return current
            }
        }
        val ref = ref
        val current = ref.get()
        if (current === newValue) return current
        val new_ref = WeakReference(newValue)
        this.ref = new_ref
        return current
    }

    actual open fun compareAndSet(expectedValue: R?, newValue: R?): Boolean {
        startMutate()
        val ref = ref
        val current = ref.get()
        if (current !== expectedValue) return false
        if (current === newValue) return true
        val new_ref = WeakReference(newValue)
        if (atomic) return refHandle.compareAndSet(this, ref, new_ref)
        this.ref = new_ref
        return true
    }

}
