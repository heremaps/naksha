package naksha.base

import naksha.base.fn.Fn0

class JsThreadLocal<T>(private val initializer: Fn0<T?>?) : PlatformThreadLocal<T> {
    private var isInitialized = false
    private var value: T? = null

    override fun get(): T? {
        if (!isInitialized) {
            isInitialized = true
            value = initializer?.call()
        }
        return value
    }

    override fun set(value: T?) {
        this.value = value
    }
}