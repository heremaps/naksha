package naksha.base

class JsThreadLocal<T>(initializer: (()->T)?) : PlatformThreadLocal<T> {
    private var value: T? = initializer?.invoke()

    override fun get(): T = value!!

    override fun set(value: T) {
        this.value = value
    }
}
