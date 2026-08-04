package naksha.base

actual open class ThreadLocalNullable<T> actual constructor(private val initializer: (()->T?)?) : java.lang.ThreadLocal<T>() {
    override fun initialValue(): T? = initializer?.invoke()
}