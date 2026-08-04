package naksha.base

import java.lang.invoke.MethodHandles
import java.lang.invoke.VarHandle
import java.util.concurrent.atomic.AtomicBoolean

/**
 * JVM actual of [BaseBool]. Backed by [AtomicBoolean].
 * @since 3.0
 */
actual class BaseBool actual constructor(private var value: Boolean): AbstractBase() {
    actual override fun withAtomic(enable: Boolean): BaseBool = super.withAtomic(enable) as BaseBool
    actual override fun withReadOnly(enable: Boolean): BaseBool = super.withReadOnly(enable) as BaseBool
    actual override fun withImmutable(enable: Boolean): BaseBool = super.withImmutable(enable) as BaseBool

    companion object BaseBool_C {
        private val valueHandle: VarHandle = MethodHandles
            .privateLookupIn(BaseBool::class.java, MethodHandles.lookup())
            .findVarHandle(BaseBool::class.java, "value", Int::class.java)
    }

    actual fun get(): Boolean = value
    actual fun set(value: Boolean) {
        startMutate()
        if (atomic) valueHandle.setVolatile(value) else this.value = value
    }

    actual fun compareAndSet(expect: Boolean, update: Boolean): Boolean {
        startMutate()
        if (atomic) return valueHandle.compareAndSet(this, expect, update)
        if (this.value != expect) return false
        this.value = value
        return true
    }
}

