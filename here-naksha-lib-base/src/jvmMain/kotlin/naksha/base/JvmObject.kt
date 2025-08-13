package naksha.base

/**
 * The base class of all other platform objects.
 * @since 3.0
 */
internal open class JvmObject : PlatformObject {
    internal companion object {
        @JvmStatic
        internal val undefined = JvmObject()
    }

    /**
     * If only one [SymbolMember] is added to the [PlatformObject], the symbol of the member.
     * @since 3.0
     */
    private var symbol: Symbol? = null

    /**
     * If only one [SymbolMember] is added to the [PlatformObject] _([symbol] is not `null`)_, then the value of the member.
     * @since 3.0
     */
    private var value: Any? = null

    /**
     * The map for additional symbols; if any.
     */
    internal var symbols: HashMap<Symbol, Any?>? = null

    /**
     * Returns the number of assigned symbols.
     * @return the number of assigned symbols.
     */
    fun symbolsCount() : Int = symbols?.size ?: if (symbol != null) 1 else 0

    /**
     * Returns the symbols map.
     *
     * If no such map exists yet, creates one.
     * @return The symbols map.
     */
    fun symbols(): HashMap<Symbol, Any?> {
        var s = symbols
        if (s == null) {
            s = HashMap()
            val symbol = this.symbol
            if (symbol != null) {
                s[symbol] = value
                this.symbol = null
                this.value = null
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
        return this.symbol === sym
    }

    /**
     * Returns the value assigned to the given symbol.
     * @param sym The symbol to query.
     * @return The value assigned to the symbol or _null_
     */
    open fun getSymbol(sym: Symbol): Any? {
        val s = symbols
        if (s != null) return s[sym]
        return if (this.symbol === sym) value else null
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
        if (this.symbol === sym) {
            val old = this.value
            this.symbol = null
            this.value = null
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
        if (this.symbol === sym) {
            this.symbol = null
            this.value = null
            return true
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
        var symbols = this.symbols
        if (symbols == null) {
            val symbol = this.symbol
            if (symbol === sym || symbol == null) {
                val old = this.value
                this.symbol = sym
                this.value = value
                return old
            }
        }
        symbols = symbols()
        val old = symbols[sym]
        symbols[sym] = value
        return old
    }

    /**
     * Invokes [PlatformType.proxy] of ths given `type` against this object.
     * @param type The [PlatformType] of the proxy to create.
     * @return The proxy instance.
     */
    fun <T : Proxy> proxy(type: PlatformType<T>): T = type.proxy(this)
}