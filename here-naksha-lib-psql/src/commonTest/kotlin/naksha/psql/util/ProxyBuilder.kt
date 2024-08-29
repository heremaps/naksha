package naksha.psql.util

import naksha.base.AnyObject
import naksha.base.Proxy

object ProxyBuilder {

    inline fun <reified T : Proxy> make(vararg pairs: Pair<Any, Any>): T {
        return AnyObject().apply {
            pairs.forEach { (key, value) ->
                setRaw(key, value)
            }
        }.proxy(T::class)
    }
}