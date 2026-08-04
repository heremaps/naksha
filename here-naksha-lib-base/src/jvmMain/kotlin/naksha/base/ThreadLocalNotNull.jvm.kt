package naksha.base

actual open class ThreadLocalNotNull<T> actual constructor(private val initializer: (()->T)) : ThreadLocal<T>() {
    override fun initialValue(): T = initializer.invoke()
    actual override fun get(): T = super.get()!!
    actual override fun set(value: T) = super.set(value)
}