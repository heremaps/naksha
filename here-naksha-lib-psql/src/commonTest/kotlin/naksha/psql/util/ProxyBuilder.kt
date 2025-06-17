package naksha.psql.util

import naksha.base.AnyObject
import naksha.base.PlatformType
import naksha.base.Proxy

object ProxyBuilder {

    @Suppress("NOTHING_TO_INLINE")
    inline fun <T : Proxy> make(type: PlatformType<T>, vararg pairs: Pair<Any, Any>): T {
        return type.proxy(AnyObject().apply {
            pairs.forEach { (key, value) ->
                setRaw(key, value)
            }
        })
    }
}