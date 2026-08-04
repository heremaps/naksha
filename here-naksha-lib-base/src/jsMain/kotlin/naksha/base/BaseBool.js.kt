package naksha.base

/**
 * JS actual of [BaseBool].
 * @since 3.0
 */
@JsExport
actual class BaseBool actual constructor(private var value: Boolean): AbstractBase() {
    actual override fun withAtomic(enable: Boolean): BaseBool = super.withAtomic(enable) as BaseBool
    actual override fun withReadOnly(enable: Boolean): BaseBool = super.withReadOnly(enable) as BaseBool
    actual override fun withImmutable(enable: Boolean): BaseBool = super.withImmutable(enable) as BaseBool

    actual fun get(): Boolean = value

    actual fun set(value: Boolean) {
        startMutate()
        this.value = value
    }

    actual fun compareAndSet(expect: Boolean, update: Boolean): Boolean {
        startMutate()
        if (value == expect) {
            value = update
            return true
        }
        return false
    }
}
