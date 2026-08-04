package naksha.base

/**
 * JS actual of [BaseInt].
 * @since 3.0
 */
@JsExport
actual class BaseInt actual constructor(private var value: Int): BaseNumber() {
    actual override fun withAtomic(enable: Boolean): BaseInt = super.withAtomic(enable) as BaseInt
    actual override fun withReadOnly(enable: Boolean): BaseInt = super.withReadOnly(enable) as BaseInt
    actual override fun withImmutable(enable: Boolean): BaseInt = super.withImmutable(enable) as BaseInt

    actual fun get(): Int = value

    actual fun set(value: Int) {
        startMutate()
        this.value = value
        endMutate()
    }

    actual fun compareAndSet(expect: Int, update: Int): Boolean {
        startMutate()
        try {
            if (value == expect) {
                value = update
                return true
            }
            return false
        } finally {
            endMutate()
        }
    }

    actual fun getAndAdd(value: Int): Int {
        startMutate()
        try {
            val old = this.value
            this.value += value
            return old
        } finally {
            endMutate()
        }
    }

    actual fun addAndGet(value: Int): Int {
        startMutate()
        try {
            this.value += value
            return this.value
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
