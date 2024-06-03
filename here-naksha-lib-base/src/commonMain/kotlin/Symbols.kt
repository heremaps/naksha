@file:Suppress("OPT_IN_USAGE")

package com.here.naksha.lib.base

import com.here.naksha.lib.base.Platform.Companion.DEFAULT_SYMBOL
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmStatic

/**
 * A singleton that grants access to symbols. Symbols are a way to bind proxies (and other hidden data) to platform objects.
 */
expect class Symbols {
    companion object {
        /**
         * Creates a new symbol with the given description.
         * @param description The optional description.
         * @return A new symbol with the given description.
         */
        @JvmStatic
        @JsStatic
        fun newSymbol(description: String? = null): Symbol

        /**
         * Returns the symbol for the given string from the global registry. It is recommended to use a package name, for example
         * _com.here.naksha_ is used for [DEFAULT_SYMBOL], the default Naksha multi-platform library.
         * @param key The symbol key; if _null_, a random symbol not part of the registry is created.
         * @return The existing symbol, if no such symbol exist yet, creates a new one.
         */
        @JvmStatic
        @JsStatic
        fun forName(key: String?): Symbol

        /**
         * Returns a read-only list of all currently registered symbol resolvers.
         * @return The list of all currently registered symbol resolvers.
         */
        @JvmStatic
        @JsStatic
        fun getSymbolResolvers(): List<SymbolResolver>

        /**
         * Compares and sets the symbol resolvers in an atomic way.
         * @param expect The list that was read.
         * @param value The new list that should be set, a read-only copy will be done.
         * @return _true_ if the set was successful; _false_ if it failed (another thread modified the list concurrently).
         */
        @JvmStatic
        @JsStatic
        fun compareAndSetSymbolResolvers(expect: List<SymbolResolver>, value: List<SymbolResolver>): Boolean

        /**
         * Returns the value of a symbol, stored with the platform object.
         * @param obj The object to access.
         * @param key The symbol.
         * @return The value or _undefined_ if no such symbol exist.
         */
        @JvmStatic
        @JsStatic
        fun get(obj: PlatformObject, key: Symbol): Any?

        /**
         * Sets the value of a symbol, stored with the platform object.
         * @param obj The object to access.
         * @param key The symbol.
         * @param value The value to store, if being _undefined_, then the symbol is removed.
         * @return The previously assigned value; _undefined_ if no such symbol existed.
         */
        @JvmStatic
        @JsStatic
        fun set(obj: PlatformObject, key: Symbol, value: Any?): Any?

        /**
         * Tests if the symbol exists, stored with the platform object.
         * @param obj The object to access.
         * @param key The symbol to test.
         * @return _true_ if the symbol exists; _false_ otherwise.
         */
        @JvmStatic
        @JsStatic
        fun has(obj: PlatformObject, key: Symbol): Boolean

        /**
         * Removes the symbol, stored with the platform object.
         * @param obj The object to access.
         * @param key The symbol.
         * @return The value being removed; _undefined_ if no such symbol existed.
         */
        @JvmStatic
        @JsStatic
        fun remove(obj: PlatformObject, key: String): Any?

        /**
         * Returns an iterator above all symbols of a platform object.
         * @param obj The object to iterate.
         * @return The iterator above all symbols, where the value is an array with the element at index 0 being the key (the symbol)
         * and the element at index 1 being the value.
         */
        @JvmStatic
        @JsStatic
        fun iterator(obj: PlatformObject): PlatformIterator<PlatformList>

        /**
         * Collect all the keys of the object properties (being [String]).
         * @param obj The object from which to get all property keys.
         * @return The keys of the object properties.
         */
        @JvmStatic
        @JsStatic
        fun keys(obj: PlatformObject): Array<Symbol> // Object.getOwnPropertySymbols(x)

        /**
         * Returns the amount of symbols assigned to the given platform object.
         * @param obj The platform object for which to count the symbols.
         * @return The amount of symbols.
         */
        @JvmStatic
        @JsStatic
        fun count(obj: PlatformObject): Int
    }
}
