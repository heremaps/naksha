package naksha.base

object JvmProxyUtil {
    @JvmStatic
    fun <T : Proxy> box(raw: Any?, _clazz: Class<T>): T? =
        Proxy.box(raw, _clazz.kotlin)

}