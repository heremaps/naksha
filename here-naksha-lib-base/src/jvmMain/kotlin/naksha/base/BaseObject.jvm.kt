package naksha.base

import java.lang.invoke.MethodHandles
import java.lang.invoke.VarHandle
import kotlin.reflect.KClass

/**
 * JVM actual of [BaseObject].
 * @since 3.0
 */
actual abstract class BaseObject internal actual constructor(): AbstractBase(), IProxyable {

    companion object BaseObject_C {
        /** Reference to [BaseObject.firstProxyHandle]. */
        private val firstProxyHandle: VarHandle = MethodHandles
            .privateLookupIn(BaseObject::class.java, MethodHandles.lookup())
            .findVarHandle(BaseObject::class.java, "firstProxy", AbstractProxy::class.java)

        /** Reference to [BaseObject.firstProxyHandle]. */
        internal val dataHandle: VarHandle = MethodHandles
            .privateLookupIn(BaseObject::class.java, MethodHandles.lookup())
            .findVarHandle(BaseObject::class.java, "data", AbstractProxy::class.java)

        /** Constant to use empty array. */
        internal val EMPTY: Array<Any?> = emptyArray()
    }

    /** The first linked proxy, if any. */
    private var firstProxy: AbstractProxy? = null

    private fun <T : AbstractProxy> implementationOf(klass: Class<T>): Class<T> {
        var replacement: Any? = Base.interfaceToImplementation[klass]
        return if (replacement != null) {
            if (replacement is KClass<*>) replacement = replacement.java
            if (replacement is Class<*> && klass.isAssignableFrom(replacement)) {
                @Suppress("UNCHECKED_CAST")
                replacement as Class<T>
            } else {
                klass
            }
        } else klass
    }

    /**
     * Creates a new proxy of the given type. The returned proxy will be linked to this object through the constructor of the proxy.
     * @param klass the type of the proxy.
     * @return the new proxy, linked to this object _(through the constructor of the proxy)_.
     */
    private fun <T : AbstractProxy> newProxyLinkedToThis(klass: Class<T>): T {
        val constructor = implementationOf(klass).getDeclaredConstructor(BaseObject::class.java)
        constructor.isAccessible = true
        return constructor.newInstance(this) as T
    }

    /**
     * Links the given proxy to this object and returns this object.
     * @param newProxy the proxy to link, called from [AbstractProxy].
     * @return this.
     * @since 3.0
     */
    internal fun linkNewProxy(newProxy: AbstractProxy): BaseObject {
        while (true) {
            val init: AbstractProxy? = this.firstProxy // plain read
            var last: AbstractProxy = if (init != null) init else { // firstProxy == null
                if (firstProxyHandle.compareAndSet(this, null, newProxy)) return this
                // `firstProxy` must not be null, even while we thought so (stale cache, other thread updated).
                // The CAS operation above added a full memory fence. This means:
                // We know that `firstProxy` is not null, and that the current value is in our L1 cache.
                // We know that `firstProxy` is a write-ones field, therefore, we can now read plain from L1 cache.
                this.firstProxy as AbstractProxy
            }
            if (last::class === newProxy::class) throw BaseProxyLinkError(last)
            var next: AbstractProxy? = newProxy.next // plain read
            while (true) {
                while (next != null) {
                    if (next::class === newProxy::class) throw BaseProxyLinkError(next)
                    last = next
                    next = last.next // plain read
                }
                // next == null
                if (AbstractProxy.nextHandle.compareAndSet(last, null, newProxy)) return this
                // proxy.next was modified by another thread, or we read an outdated version from cache!
                // Fix this by reading the latest value, beware that it must not be null, because
                // we now that values are only set, never modified later.
                next = last.next as AbstractProxy
                // next != null
                if (next::class === newProxy::class) throw BaseProxyLinkError(next)
            }
        }
    }

    override fun <T : AbstractProxy> proxy(javaClass: Class<T>): T {
        try {
            // For performance reason, we do only plain reads!
            // Therefore, single thread performance is the same as multi-thread performance!
            var proxy: AbstractProxy = firstProxy ?: return newProxyLinkedToThis(javaClass) // No existing proxy, create new, link, return.
            if (javaClass.isInstance(proxy)) return javaClass.cast(proxy)
            var nextProxy = proxy.next // plain read
            while (nextProxy != null) {
                proxy = nextProxy
                if (javaClass.isInstance(proxy)) return javaClass.cast(proxy)
                nextProxy = nextProxy.next
            }
            // No matching existing proxy, create new, link, return.
            return newProxyLinkedToThis(javaClass)
        } catch (e: BaseProxyLinkError) {
            @Suppress("UNCHECKED_CAST") // Another thread added the needed proxy.
            val newProxy = e.existing as T
            // Beware, this can happen for a couple of reasons, one can be stale CPU caching.
            // All our reads are plain reads, so not-volatile, that makes them fast, but error-prone.
            // Even while we think that the proxy is not added, it could have been added already.
            // Therefore, in that case we will get an exception throw by the constructor when it tries to
            // link itself into the base-object using a few volatile instructions.
            //
            // We are willing to pay with one not necessary object construction, in case of concurrent
            // proxy modifications, in exchange for drastically improved read performance !!!
            return newProxy
        }
    }

    actual override fun <T : AbstractProxy> proxy(klass: KClass<T>): T = proxy(klass.java)
    internal actual open fun box(value: Any?): Any? = when (value) {
        is AbstractProxy -> value.baseObject
        else -> value
    }
    internal actual open fun unbox(value: Any?): Any? = value

    /**
     * The actual data hosted in the
     */
    internal var data: Array<Any?> = EMPTY
}
