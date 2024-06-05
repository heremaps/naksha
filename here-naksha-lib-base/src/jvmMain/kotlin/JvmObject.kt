package com.here.naksha.lib.base

import com.here.naksha.lib.base.Platform.Companion.DEFAULT_SYMBOL

/**
 * The base class of all other platform objects.
 */
open class JvmObject {
    internal companion object {
        @JvmStatic
        internal val undefined = JvmObject()
    }

    /**
     * The properties of the object; if any.
     */
    internal var properties: HashMap<String, Any?>? = null

    /**
     * Returns the properties map, if none exists yet, creates a new one.
     * @return The properties map of the object.
     */
    fun properties(): HashMap<String, Any?> {
        var p = properties
        if (p == null) {
            p = HashMap()
            properties = p
        }
        return p
    }

    /**
     * Returns the number of assigned properties.
     * @return the number of assigned properties.
     */
    open fun propertiesCount() : Int {
        val p = properties
        return p?.size ?: 0
    }

    /**
     * Tests if this object has a property with the given name.
     * @param name The name of the property.
     * @return _true_ if the object has such a property; _false_ otherwise.
     */
    open operator fun contains(name: String): Boolean = properties?.containsKey(name) == true

    /**
     * Returns the value of the property with the given name or _null_.
     * @param name The name of the property.
     * @return The value of the property or _null_.
     */
    open operator fun get(name: String): Any? = if (properties?.containsKey(name) == true) properties?.get(name) else null

    /**
     * Removes the property with the given name.
     * @param name The name of the property.
     * @return The value that was removed or _null_.
     */
    open fun delete(name: String): Any? = if (properties?.containsKey(name) == true) properties?.remove(name) else null

    /**
     * Removes the property with the given name.
     * @param name The name of the property.
     * @return _true_ if the property was removed; _false_ if no such property existed.
     */
    open fun remove(name: String): Boolean {
        if (properties?.containsKey(name) == true) {
            properties?.remove(name)
            return true
        }
        return false
    }

    /**
     * Set the value of the property.
     * @param name The name of the property.
     * @param value The value to set.
     * @return The previous value or _null_.
     */
    open operator fun set(name: String, value: Any?): Any? {
        // Note: This is incompatible with JavaScript default behavior, but makes Kotlin code better!
        //       We do not want properties with the value undefined!
        if (value === undefined) return delete(name)
        val old = get(name)
        properties()[name] = value
        return old
    }

    /**
     * The Naksha default symbol, only used as long as no other symbols are defined.
     */
    internal var baseSym: Any? = undefined

    /**
     * The map for additional symbols; if any.
     */
    internal var symbols: HashMap<Symbol, Any?>? = null

    /**
     * Returns the number of assigned symbols.
     * @return the number of assigned symbols.
     */
    fun symbolsCount() : Int {
        val s = symbols
        return s?.size ?: if (baseSym != null) 1 else 0
    }

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
    open operator fun contains(sym: Symbol): Boolean {
        val s = symbols
        if (s != null) return s.containsKey(sym)
        return sym === DEFAULT_SYMBOL && baseSym != undefined
    }

    /**
     * Returns the value assigned to the given symbol.
     * @param sym The symbol to query.
     * @return The value assigned to the symbol or _null_
     */
    open operator fun get(sym: Symbol): Any? {
        val s = symbols
        if (s != null) return s[sym]
        return if (sym === DEFAULT_SYMBOL) baseSym else null
    }

    /**
     * Removes the assigned to the given symbol.
     * @param sym The symbol to remove.
     * @return The value that was assigned to the symbol or _null_.
     */
    open fun delete(sym: Symbol): Any? {
        val s = symbols
        if (s != null) {
            return if (s.containsKey(sym)) s.remove(sym) else null
        }
        if (sym === DEFAULT_SYMBOL) {
            val old = if (baseSym == undefined) null else baseSym
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
    open fun remove(sym: Symbol): Boolean {
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
    open operator fun set(sym: Symbol, value: Any?): Any? {
        if (value === undefined) return delete(sym)
        var s = symbols
        if (s == null && sym === DEFAULT_SYMBOL) {
            val old = if (baseSym == undefined) null else baseSym
            baseSym = value
            return old
        }
        if (s == null) s = symbols()
        val old = s[sym]
        s[sym] = value
        return old
    }
}