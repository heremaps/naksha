package naksha.base

import naksha.base.Base.BaseCompanion.klassFor
import naksha.base.fn.Fn2

object JvmAnyObjectUtil {

    @JvmStatic
    fun <T : Any> getProperty(owner: PAnyMap, key: String, type: Class<T>): T? =
        owner.getAs(key, klassFor(type))

    @JvmStatic
    @JvmOverloads
    fun <T : Any> getOrCreateProperty(
        owner: PAnyMap,
        key: String,
        type: Class<T>,
        init: Fn2<out T?, in PAnyMap, in String>? = null
    ): T = owner.getOrCreate(key, klassFor(type), init)

    @JvmStatic
    fun <T : Any> getOrSetProperty(
        owner: PAnyMap,
        key: String,
        value: T,
    ): T = owner.getOrSet(key, value)

    @JvmStatic
    fun <T: Any> getPropertyOrReturnDefault(
        owner: PAnyMap,
        key: String,
        default: T,
    ): T = owner.getOr(key, default)
}