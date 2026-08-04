@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package naksha.base

import java.lang.invoke.MethodHandles
import java.lang.invoke.VarHandle
import kotlin.reflect.KClass

actual open class AbstractProxy internal actual constructor(baseObject: BaseObject): IProxyable {
    companion object AbstractProxy_C {
        /** Reference to [AbstractProxy.next] */
        @JvmField
        internal val nextHandle: VarHandle = MethodHandles
            .privateLookupIn(AbstractProxy::class.java, MethodHandles.lookup())
            .findVarHandle(AbstractProxy::class.java, "next", AbstractProxy::class.java)
    }

    /**
     * We link proxies together. This property is used form [BaseObject]!
     * @see BaseObject
     */
    internal var next: AbstractProxy? = null

    @JvmField
    actual val baseObject: BaseObject = baseObject.linkNewProxy(this)

    actual override fun <T : AbstractProxy> proxy(klass: KClass<T>): T = this.proxy(klass.java)
    override fun <T : AbstractProxy> proxy(javaClass: Class<T>): T = baseObject.proxy(javaClass)
}