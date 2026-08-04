package naksha.base

actual open class ThreadLocalNullable<T> actual constructor(initializer: (()->T?)?) {
    private var value: T? = initializer?.invoke()

    actual fun get(): T? = value
    actual fun set(value: T?) {
        this.value = value
    }
}