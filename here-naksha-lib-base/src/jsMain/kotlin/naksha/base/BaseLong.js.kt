package naksha.base

/**
 * JS actual of [BaseLong].
 * @since 3.0
 */
@JsExport
actual class BaseLong actual constructor(private var value: Long): BaseNumber() {
    actual override fun withAtomic(enable: Boolean): BaseLong = super.withAtomic(enable) as BaseLong
    actual override fun withReadOnly(enable: Boolean): BaseLong = super.withReadOnly(enable) as BaseLong
    actual override fun withImmutable(enable: Boolean): BaseLong = super.withImmutable(enable) as BaseLong

    actual fun get(): Long = value

    actual fun set(value: Long) {
        mutate()
        this.value = value
    }

    actual fun compareAndSet(expect: Long, update: Long): Boolean {
        mutate()
        if (value == expect) {
            value = update
            return true
        }
        return false
    }

    actual fun getAndAdd(value: Long): Long {
        mutate()
        val old = this.value
        this.value += value
        return old
    }

    actual fun addAndGet(value: Long): Long {
        mutate()
        this.value += value
        return this.value
    }

    actual override fun toDouble(): Double = value.toDouble()
    actual override fun toFloat(): Float = value.toFloat()
    actual override fun toLong(): Long = value
    actual override fun toInt(): Int = value.toInt()
    actual override fun toShort(): Short = value.toShort()
    actual override fun toByte(): Byte = value.toByte()
}
