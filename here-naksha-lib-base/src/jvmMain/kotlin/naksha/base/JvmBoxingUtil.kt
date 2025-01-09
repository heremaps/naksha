package naksha.base

import kotlin.jvm.internal.Reflection

object JvmBoxingUtil {
    @JvmStatic
    fun <T> box(raw: Any?, _clazz: Class<T>): T? =
        Proxy.box(raw, Reflection.getOrCreateKotlinClass(_clazz)) as T?
}