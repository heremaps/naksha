package naksha.base

import naksha.base.fn.Fn2
import kotlin.jvm.internal.Reflection

object JvmAnyObjectUtil {

    @JvmStatic
    fun <T> getProperty(owner: AnyObject, key: String, type: Class<T>): T? =
        owner.getAs(key, Reflection.getOrCreateKotlinClass(type)) as T?

    @JvmStatic
    @JvmOverloads
    fun <T> getOrCreateProperty(
        owner: AnyObject,
        key: String,
        type: Class<T>,
        init: Fn2<out T?, in AnyObject, in String>? = null
    ): T = owner.getOrCreate(key, Reflection.getOrCreateKotlinClass(type), init) as T
}