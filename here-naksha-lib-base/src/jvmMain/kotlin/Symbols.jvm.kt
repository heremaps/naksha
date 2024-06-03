package com.here.naksha.lib.base

/**
 * A singleton that grants access to symbols. Symbols are a way to bind proxies (and other hidden data) to platform objects.
 */
actual class Symbols {
    actual companion object {
        /**
         * Creates a new symbol with the given description.
         * @param description The optional description.
         * @return A new symbol with the given description.
         */
        actual fun newSymbol(description: String?): Symbol {
            TODO("Not yet implemented")
        }

        /**
         * Returns the symbol for the given string from the global registry. It is recommended to use a package name, for example
         * _com.here.naksha_ is used for [DEFAULT_SYMBOL], the default Naksha multi-platform library.
         * @param key The symbol key; if _null_, a random symbol not part of the registry is created.
         * @return The existing symbol, if no such symbol exist yet, creates a new one.
         */
        actual fun forName(key: String?): Symbol {
            TODO("Not yet implemented")
        }

        /**
         * Returns a read-only list of all currently registered symbol resolvers.
         * @return The list of all currently registered symbol resolvers.
         */
        actual fun getSymbolResolvers(): List<SymbolResolver> {
            TODO("Not yet implemented")
        }

        /**
         * Compares and sets the symbol resolvers in an atomic way.
         * @param expect The list that was read.
         * @param value The new list that should be set, a read-only copy will be done.
         * @return _true_ if the set was successful; _false_ if it failed (another thread modified the list concurrently).
         */
        actual fun compareAndSetSymbolResolvers(
            expect: List<SymbolResolver>,
            value: List<SymbolResolver>
        ): Boolean {
            TODO("Not yet implemented")
        }

        /**
         * Returns the value of a symbol, stored with the platform object.
         * @param obj The object to access.
         * @param key The symbol.
         * @return The value or _undefined_ if no such symbol exist.
         */
        actual fun get(obj: PlatformObject, key: Symbol): Any? {
            TODO("Not yet implemented")
        }

        /**
         * Sets the value of a symbol, stored with the platform object.
         * @param obj The object to access.
         * @param key The symbol.
         * @param value The value to store, if being _undefined_, then the symbol is removed.
         * @return The previously assigned value; _undefined_ if no such symbol existed.
         */
        actual fun set(
            obj: PlatformObject,
            key: Symbol,
            value: Any?
        ): Any? {
            TODO("Not yet implemented")
        }

        /**
         * Tests if the symbol exists, stored with the platform object.
         * @param obj The object to access.
         * @param key The symbol to test.
         * @return _true_ if the symbol exists; _false_ otherwise.
         */
        actual fun has(obj: PlatformObject, key: Symbol): Boolean {
            TODO("Not yet implemented")
        }

        /**
         * Removes the symbol, stored with the platform object.
         * @param obj The object to access.
         * @param key The symbol.
         * @return The value being removed; _undefined_ if no such symbol existed.
         */
        actual fun remove(obj: PlatformObject, key: String): Any? {
            TODO("Not yet implemented")
        }

        /**
         * Returns an iterator above all symbols of a platform object.
         * @param obj The object to iterate.
         * @return The iterator above all symbols, where the value is an array with the element at index 0 being the key (the symbol)
         * and the element at index 1 being the value.
         */
        actual fun iterator(obj: PlatformObject): PlatformIterator<PlatformList> {
            TODO("Not yet implemented")
        }

        /**
         * Collect all the keys of the object properties (being [String]).
         * @param obj The object from which to get all property keys.
         * @return The keys of the object properties.
         */
        actual fun keys(obj: PlatformObject): Array<Symbol> {
            TODO("Not yet implemented")
        }

        /**
         * Returns the amount of symbols assigned to the given platform object.
         * @param obj The platform object for which to count the symbols.
         * @return The amount of symbols.
         */
        actual fun count(obj: PlatformObject): Int {
            TODO("Not yet implemented")
        }

    }
}