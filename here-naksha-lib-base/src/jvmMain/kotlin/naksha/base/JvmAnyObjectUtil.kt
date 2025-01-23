package naksha.base

import kotlin.jvm.internal.Reflection

object JvmAnyObjectUtil {

    @JvmStatic
    fun <T> getProperty(owner: AnyObject, key: String, type: Class<T>): T? =
        owner.getAs(key, Reflection.getOrCreateKotlinClass(type)) as T?
}