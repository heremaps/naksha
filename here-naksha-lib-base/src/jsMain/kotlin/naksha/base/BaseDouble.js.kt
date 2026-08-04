package naksha.base

@JsExport
actual class BaseDouble actual constructor(private var value: Double) : BaseNumber() {
    actual override fun withAtomic(enable: Boolean): BaseDouble = super.withAtomic(enable) as BaseDouble
    actual override fun withReadOnly(enable: Boolean): BaseDouble = super.withReadOnly(enable) as BaseDouble
    actual override fun withImmutable(enable: Boolean): BaseDouble = super.withImmutable(enable) as BaseDouble

    actual fun get(): Double = value

    actual fun set(value: Double) {
        mutate()
        this.value = value
    }

    actual fun compareAndSet(expect: Double, update: Double): Boolean {
        mutate()
        if (value == expect) {
            value = update
            return true
        }
        return false
    }

    actual fun getAndAdd(value: Double): Double {
        mutate()
        val old = this.value
        this.value += value
        return old
    }

    actual fun addAndGet(value: Double): Double {
        mutate()
        this.value += value
        return this.value
    }

    actual override fun toDouble(): Double = value
    actual override fun toFloat(): Float = value.toFloat()
    actual override fun toLong(): Long = value.toLong()
    actual override fun toInt(): Int = value.toInt()
    actual override fun toShort(): Short = value.toInt().toShort()
    actual override fun toByte(): Byte = value.toInt().toByte()
}