package naksha.base

import naksha.base.Base.BaseCompanion.DEFAULT_SYMBOL
import kotlin.reflect.KClass

/**
 * The base class of all other platform objects.
 */
open class JvmObject : PlatformObject {
    internal companion object {
        @JvmStatic
        internal val undefined = Json.UNDEFINED; // JvmObject()
    }

    /** The first linked proxy, if any. */
    internal var firstProxy: JvmProxyLink? = null

    /**
     * Links the given proxy to this object and returns this object.
     * @param proxy the proxy to link, called from [AbstractProxy].
     * @return this.
     * @since 3.0
     */
    internal fun linkProxy(proxy: AbstractProxy): JvmObject {
        var link: JvmProxyLink? = this.firstProxy
        if (link == null) {
            link = JvmProxyLink(this, proxy)
            this.firstProxy = link
            return this
        }
        var nextLink = link.next
        while (nextLink != null) {
            link = nextLink
            nextLink = link.next
        }
        link.next = JvmProxyLink(this, proxy)
        return this
    }

    /**
     * Creates a new proxy of the given type. The returned proxy will be linked to this object through the constructor of the proxy.
     * @param klass the type of the proxy.
     * @return the new proxy, linked to this object _(through the constructor of the proxy)_.
     */
    private fun <T : Proxy> newProxyLinkedToThis(klass: Class<T>): T {
        val constructor = klass.getDeclaredConstructor(PlatformObject::class.java)
        val proxy = constructor.newInstance(this) as T
        constructor.isAccessible = true
        return proxy
    }

    /**
     * Create a proxy or return the existing proxy.
     * @param klass The proxy class.
     * @return The proxy instance.
     * @see BaseCompanion.proxy
     */
    fun <T : Proxy> proxy(klass: Class<T>): T {
        var link: JvmProxyLink = firstProxy ?: return newProxyLinkedToThis(klass) // No existing proxy, create new, link, return.
        if (klass.isInstance(link.proxy)) return klass.cast(link.proxy)
        var nextLink = link.next
        while (nextLink != null) {
            link = nextLink
            if (klass.isInstance(link.proxy)) return klass.cast(link.proxy)
            nextLink = link.next
        }
        // No matching existing proxy, create new, link, return.
        return newProxyLinkedToThis(klass)
    }

    /**
     * Create a proxy or return the existing proxy.
     * @param klass The proxy class.
     * @return The proxy instance.
     */
    fun <T : Proxy> proxy(klass: KClass<T>): T = proxy(klass.java)

    /**
     * The Naksha default symbol, only used as long as no other symbols are defined.
     */
    private var baseSym: Any? = undefined

    /**
     * The map for additional symbols; if any.
     */
    internal var symbols: HashMap<Symbol, Any?>? = null

    /**
     * Returns the number of assigned symbols.
     * @return the number of assigned symbols.
     */
    fun symbolsCount() : Int = symbols?.size ?: if (baseSym != null) 1 else 0

    /**
     * Returns the symbols map.
     * @return The symbols map.
     */
    fun symbols(): HashMap<Symbol, Any?> {
        var s = symbols
        if (s == null) {
            s = HashMap()
            if (baseSym != undefined) {
                s[DEFAULT_SYMBOL] = baseSym
                baseSym = undefined
            }
            symbols = s
        }
        return s
    }

    /**
     * Tests if this object has an assignment for the given symbol.
     * @param sym The symbol to test for.
     * @return _true_ if the object has such a symbol assignment; _false_ otherwise.
     */
    open fun containsSymbol(sym: Symbol): Boolean {
        val s = symbols
        if (s != null) return s.containsKey(sym)
        return sym === DEFAULT_SYMBOL && baseSym != undefined
    }

    /**
     * Returns the value assigned to the given symbol.
     * @param sym The symbol to query.
     * @return The value assigned to the symbol or _null_
     */
    open fun getSymbol(sym: Symbol): Any? {
        val s = symbols
        if (s != null) return s[sym]
        return if (sym === DEFAULT_SYMBOL) baseSym else null
    }

    /**
     * Removes the assigned to the given symbol.
     * @param sym The symbol to remove.
     * @return The value that was assigned to the symbol or _null_.
     */
    open fun removeSymbol(sym: Symbol): Any? {
        val s = symbols
        if (s != null) {
            return if (s.containsKey(sym)) s.remove(sym) else null
        }
        if (sym === DEFAULT_SYMBOL) {
            val old = if (baseSym === undefined) null else baseSym
            baseSym = undefined
            return old
        }
        return null
    }

    /**
     * Removes the assigned to the given symbol.
     * @param sym The symbol to remove.
     * @return _true_ if the symbol was removed; _false_ if no such symbol existed.
     */
    open fun deleteSymbol(sym: Symbol): Boolean {
        val s = symbols
        if (s != null) {
            if (s.containsKey(sym)) {
                s.remove(sym)
                return true
            }
            return false
        }
        if (sym === DEFAULT_SYMBOL) {
            val removed = baseSym != undefined
            baseSym = undefined
            return removed
        }
        return false
    }

    /**
     * Assigns the given symbol to the given value.
     * @param sym The symbol to assign.
     * @param value The value to assign.
     * @return The previously assigned value or _null_.
     */
    open fun setSymbol(sym: Symbol, value: Any?): Any? {
        if (value === undefined) return removeSymbol(sym)
        var s = symbols
        if (s == null && sym === DEFAULT_SYMBOL) {
            val old = if (baseSym === undefined) null else baseSym
            baseSym = value
            return old
        }
        if (s == null) s = symbols()
        val old = s[sym]
        s[sym] = value
        return old
    }
}