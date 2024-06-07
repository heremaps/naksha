package naksha.base

class JvmThreadLocal<T>(private val initializer: (()->T)?) : ThreadLocal<T>(), BaseThreadLocal<T> {
    override fun initialValue(): T? = initializer?.invoke()
}