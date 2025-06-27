package naksha.base

import naksha.base.fn.Fn0

class JvmThreadLocal<T>(private val initializer: Fn0<T?>?) : ThreadLocal<T>(), PlatformThreadLocal<T> {
    override fun initialValue(): T? = initializer?.call()
}