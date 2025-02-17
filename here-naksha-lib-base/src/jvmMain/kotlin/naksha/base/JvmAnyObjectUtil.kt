package naksha.base

import naksha.base.Platform.PlatformCompanion.klassFor
import naksha.base.fn.Fn2

object JvmAnyObjectUtil {

    @JvmStatic
    fun <T : Any> getProperty(owner: AnyObject, key: String, type: Class<T>): T? =
        owner.getAs(key, klassFor(type))

    @JvmStatic
    @JvmOverloads
    fun <T : Any> getOrCreateProperty(
        owner: AnyObject,
        key: String,
        type: Class<T>,
        init: Fn2<out T?, in AnyObject, in String>? = null
    ): T = owner.getOrCreate(key, klassFor(type), init)
}