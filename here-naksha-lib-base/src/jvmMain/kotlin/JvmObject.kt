package com.here.naksha.lib.base

import com.here.naksha.lib.base.Platform.Companion.DEFAULT_SYMBOL
import com.here.naksha.lib.base.Platform.Companion.undefined

/**
 * The base class of all other platform objects.
 */
open class JvmObject {
    // TODO: Improve the HashMap implementation. We want all keys to be NFC (or NFCK) encoded, and to be interned, and normalized.
    //       The reason is, that our hash-map should be as fast as accessing native properties, but this requires that we can
    //       compare the keys with a simple === instead of == (equals), because actually, two strings only equal after comparing
    //       all bytes of them, because even when they have the same hash, they still may not be the same strings. But if we
    //       ensure that the same key is always the same instance, the check is, especially when the strings are being equal,
    //       much faster. The compare will be as well very fast for misses, unless they accidentally have the same hash.
    //       For very small sized hash-maps, keeping a simple array is enough, iterating 8 to 16 key-value pairs should be as
    //       fast as a complicated hash-map.

    /**
     * The properties of the object; if any.
     */
    var properties: HashMap<String, Any?>? = null

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
     * Returns the value of the property with the given name or [Platform.undefined].
     * @param name The name of the property.
     * @return The value of the property or [Platform.undefined].
     */
    open operator fun get(name: String): Any? = if (properties?.containsKey(name) == true) properties?.get(name) else undefined

    /**
     * Removes the property with the given name.
     * @param name The name of the property.
     * @return The value that was removed or [Platform.undefined].
     */
    open fun remove(name: String): Any? = if (properties?.containsKey(name) == true) properties?.remove(name) else undefined

    /**
     * Set the value of the property.
     * @param name The name of the property.
     * @param value The value to set.
     * @return The previous value or [Platform.undefined].
     */
    open operator fun set(name: String, value: Any?): Any? {
        // Note: This is incompatible with JavaScript default behavior, but makes Kotlin code better!
        //       We do not want properties with the value undefined!
        if (value === undefined) return remove(name)
        val old = get(name)
        properties()[name] = value
        return old
    }

    /**
     * The Naksha default symbol, only used as long as no other symbols are defined.
     */
    var baseSym: Any? = undefined

    /**
     * The map for additional symbols; if any.
     */
    var symbols: HashMap<Symbol, Any?>? = null

    /**
     * Returns the number of assigned symbols.
     * @return the number of assigned symbols.
     */
    fun symbolsCount() : Int {
        val s = symbols
        return s?.size ?: if (baseSym != undefined) 1 else 0
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
     * @return The value assigned to the symbol or [Platform.undefined].
     */
    open operator fun get(sym: Symbol): Any? {
        val s = symbols
        if (s != null) return s[sym] ?: undefined
        if (sym === DEFAULT_SYMBOL) return baseSym
        return undefined
    }

    /**
     * Removes the assigned to the given symbol.
     * @param sym The symbol to remove.
     * @return The value that was assigned to the symbol or [Platform.undefined].
     */
    open fun remove(sym: Symbol): Any? {
        val s = symbols
        if (s != null) {
            return if (s.containsKey(sym)) s.remove(sym) else undefined
        }
        if (sym === DEFAULT_SYMBOL) {
            val old = baseSym
            baseSym = undefined
            return old
        }
        return undefined
    }

    /**
     * Assigns the given symbol to the given value.
     * @param sym The symbol to assign.
     * @param value The value to assign.
     * @return The previously assigned value or [Platform.undefined].
     */
    open operator fun set(sym: Symbol, value: Any?): Any? {
        if (value === undefined) return remove(sym)
        var s = symbols
        if (s == null && sym === DEFAULT_SYMBOL) {
            val old = baseSym
            baseSym = value
            return old
        }
        if (s == null) s = symbols()
        val old = if (s.containsKey(sym)) s[sym] else undefined
        s[sym] = value
        return old
    }
}