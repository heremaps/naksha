@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING", "OPT_IN_USAGE")

package naksha.base

import java.lang.invoke.MethodHandles
import java.lang.invoke.VarHandle
import java.util.concurrent.atomic.AtomicLong

/**
 * JVM actual of [BaseLong]. Backed by [AtomicLong].
 * @since 3.0
 */
actual class BaseLong actual constructor(private var value: Long) : BaseNumber() {
    actual override fun withAtomic(enable: Boolean): BaseLong = super.withAtomic(enable) as BaseLong
    actual override fun withReadOnly(enable: Boolean): BaseLong = super.withReadOnly(enable) as BaseLong
    actual override fun withImmutable(enable: Boolean): BaseLong = super.withImmutable(enable) as BaseLong

    companion object BaseLong_C {
        private val valueHandle: VarHandle = MethodHandles
            .privateLookupIn(BaseLong::class.java, MethodHandles.lookup())
            .findVarHandle(BaseLong::class.java, "value", Long::class.java)
    }

    actual fun get(): Long = this.value

    actual fun set(value: Long) {
        mutate()
        if (atomic) valueHandle.setVolatile(this, value) else this.value = value
    }

    actual fun compareAndSet(expect: Long, update: Long): Boolean {
        mutate()
        if (atomic) return valueHandle.compareAndSet(this, expect, update)
        val current = this.value
        if (current != expect) return false
        this.value = update
        return true
    }

    actual fun getAndAdd(value: Long): Long {
        mutate()
        if (atomic) return valueHandle.getAndAdd(this, value) as Long
        val current = this.value
        this.value = current + value
        return current
    }

    actual fun addAndGet(value: Long): Long {
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

    actual override fun toDouble(): Double = this.value.toDouble()
    actual override fun toFloat(): Float = this.value.toFloat()
    actual override fun toLong(): Long = this.value
    actual override fun toInt(): Int = this.value.toInt()
    actual override fun toShort(): Short = this.value.toShort()
    actual override fun toByte(): Byte = this.value.toByte()
}