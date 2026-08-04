package naksha.base

import kotlin.reflect.KClass

@JsExport
actual abstract class BaseObject internal actual constructor(): AbstractBase(), IProxyable {

    companion object BaseObject_C {
        /** Constant to use empty array. */
        internal val EMPTY: Array<Any?> = emptyArray()
    }

    /** The first linked proxy, if any. */
    internal var firstProxy: AbstractProxy? = null

    /**
     * Creates a new proxy of the given type. The returned proxy will be linked to this object through the constructor of the proxy.
     * @param klass the class of the proxy.
     * @return the new proxy, linked to this object _(through the constructor of the proxy)_.
     */
    private fun <T : AbstractProxy> newProxyLinkedToThis(klass: KClass<T>): T {
        val replacement: Any? = Base.interfaceToImplementation[klass]
        @Suppress("UnusedVariable", "unused") // We use it in `js()`, just compiler does not reconignize!
        val constructor: JsClass<T> = if (replacement != null) {
            if (replacement is KClass<*>) {
                @Suppress("UNCHECKED_CAST")
                (replacement as KClass<T>).js
            } else if (js("typeof replacement === 'function'").unsafeCast<Boolean>()) {
                @Suppress("UNCHECKED_CAST_TO_EXTERNAL_INTERFACE", "UNCHECKED_CAST")
                replacement as JsClass<T>
            } else {
                klass.js
            }
        } else klass.js
        return js("new constructor(this)").unsafeCast<T>()
    }

    /**
     * Links the given proxy to this object and returns this object.
     * @param newProxy the proxy to link, called from [AbstractProxy].
     * @return this.
     * @since 3.0
     */
    internal fun linkNewProxy(newProxy: AbstractProxy): BaseObject {
        var last: AbstractProxy? = this.firstProxy
        if (last == null) {
            last = newProxy
            this.firstProxy = last
            return this
        }
        var next = last.next
        while (next != null) {
            last = next
            next = last.next
        }
        last.next = newProxy
        return this
    }

    /**
     * Create a proxy or return an existing cached proxy bound to this object.
     * @param constructor The proxy class.
     * @return The proxy instance.
     * @since 3.0
     */
    override fun <T : AbstractProxy> proxy(constructor: JsClass<T>): T = proxy(constructor.kotlin)

    @Suppress("NON_EXPORTABLE_TYPE")
    actual override fun <T : AbstractProxy> proxy(klass: KClass<T>): T {
        var last = firstProxy ?: return newProxyLinkedToThis(klass) // No existing proxy, create new, link, return.
        if (klass.isInstance(last)) return last.unsafeCast<T>()
        var next = last.next
        while (next != null) {
            last = next
            if (klass.isInstance(last)) return last.unsafeCast<T>()
            next = last.next
        }
        // No matching existing proxy, create new, link, return.
        return newProxyLinkedToThis(klass)
    }

    internal actual open fun box(value: Any?): Any? = when(value) {
        is AbstractProxy -> value.baseObject
        else -> value
    }

    internal actual open fun unbox(value: Any?): Any? = value

    /**
     * The actual data hosted in the
     */
    internal var data: Array<Any?> = EMPTY
}