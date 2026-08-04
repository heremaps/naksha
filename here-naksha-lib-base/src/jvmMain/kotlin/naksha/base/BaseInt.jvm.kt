@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING", "OPT_IN_USAGE")

package naksha.base

import java.lang.invoke.MethodHandles
import java.lang.invoke.VarHandle
import java.util.concurrent.atomic.AtomicInteger

/**
 * JVM actual of [BaseInt]. Backed by [AtomicInteger].
 * @since 3.0
 */
actual class BaseInt actual constructor(private var value: Int) : BaseNumber() {
    actual override fun withAtomic(enable: Boolean): BaseInt = super.withAtomic(enable) as BaseInt
    actual override fun withReadOnly(enable: Boolean): BaseInt = super.withReadOnly(enable) as BaseInt
    actual override fun withImmutable(enable: Boolean): BaseInt = super.withImmutable(enable) as BaseInt

    companion object BaseInt_C {
        private val valueHandle: VarHandle = MethodHandles
            .privateLookupIn(BaseInt::class.java, MethodHandles.lookup())
            .findVarHandle(BaseInt::class.java, "value", Int::class.java)
    }

    actual fun get(): Int = value

    actual fun set(value: Int) {
        startMutate()
        if (atomic) valueHandle.setVolatile(this, value) else this.value = value
        endMutate()
    }

    actual fun compareAndSet(expect: Int, update: Int): Boolean {
        startMutate()
        try {
            if (atomic) return valueHandle.compareAndSet(this, expect, update)
            val current = this.value
            if (current != expect) return false
            this.value = update
            return true
        } finally {
            endMutate()
        }
    }

    actual fun getAndAdd(value: Int): Int {
        startMutate()
        try {
            if (atomic) return valueHandle.getAndAdd(this, value) as Int
            val current = this.value
            this.value = current + value
            return current
        } finally {
            endMutate()
        }
    }

    actual fun addAndGet(value: Int): Int {
        startMutate()
        try {
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
        } finally {
            endMutate()
        }
    }

    actual override fun toDouble(): Double = value.toDouble()
    actual override fun toFloat(): Float = value.toFloat()
    actual override fun toLong(): Long = value.toLong()
    actual override fun toInt(): Int = value
    actual override fun toShort(): Short = value.toShort()
    actual override fun toByte(): Byte = value.toByte()
}
