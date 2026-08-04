package naksha.base

import java.lang.invoke.MethodHandles
import java.lang.invoke.VarHandle

actual class BaseDouble actual constructor(private var value: Double) : BaseNumber() {
    actual override fun withAtomic(enable: Boolean): BaseDouble = super.withAtomic(enable) as BaseDouble
    actual override fun withReadOnly(enable: Boolean): BaseDouble = super.withReadOnly(enable) as BaseDouble
    actual override fun withImmutable(enable: Boolean): BaseDouble = super.withImmutable(enable) as BaseDouble

    companion object BaseDouble_C {
        private val valueHandle: VarHandle = MethodHandles
            .privateLookupIn(BaseDouble::class.java, MethodHandles.lookup())
            .findVarHandle(BaseDouble::class.java, "value", Double::class.java)
    }

    actual fun get(): Double = this.value

    actual fun set(value: Double) {
        mutate()
        if (atomic) valueHandle.setVolatile(this, value) else this.value = value
    }

    actual fun compareAndSet(expect: Double, update: Double): Boolean {
        mutate()
        if (atomic) return valueHandle.compareAndSet(this, expect, update)
        val current = this.value
        if (current != expect) return false
        this.value = update
        return true
    }

    actual fun getAndAdd(value: Double): Double {
        mutate()
        if (atomic) return valueHandle.getAndAdd(this, value) as Double
        val current = this.value
        this.value = current + value
        return current
    }

    actual fun addAndGet(value: Double): Double {
        mutate()
        if (atomic) {
            while (true) {
                val current = this.value
                val new_value = current + value
                if (valueHandle.compareAndSet(this, current, new_value)) return new_value
            }
        }
        val new_value = this.value + value
        this.value = new_value
        return new_value
    }

    actual override fun toDouble(): Double = this.value
    actual override fun toFloat(): Float = this.value.toFloat()
    actual override fun toLong(): Long = this.value.toLong()
    actual override fun toInt(): Int = this.value.toInt()
    actual override fun toShort(): Short = this.value.toInt().toShort()
    actual override fun toByte(): Byte = this.value.toInt().toByte()
}