package naksha.base

class JvmThreadLocal<T>(private val initializer: (()->T)?) : ThreadLocal<T>(), PlatformThreadLocal<T> {
    override fun initialValue(): T? = initializer?.invoke()
}