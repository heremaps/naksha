package naksha.base

import kotlin.jvm.internal.Reflection

object JvmBoxingUtil {
    @Deprecated(message = "Use Platform.javaProxy instead", replaceWith = ReplaceWith("Platform.javaProxy"))
    @JvmStatic
    fun <T> box(raw: Any?, _clazz: Class<T>): T? =
        Proxy.box(raw, Reflection.getOrCreateKotlinClass(_clazz)) as T?
}