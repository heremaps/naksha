package naksha.base

import naksha.base.Platform.Platform_C.DEFAULT_SYMBOL

/**
 * A singleton that grants access to [symbols][Symbol]. [Symbols][Symbol] are a way to [bind][Proxy.bind] [proxies][Proxy] _(and other hidden data)_ to [platform objects][PlatformObject].
 * @since 3.0
 * @see Symbol
 * @see SymbolMember
 */
@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
expect class Symbols private constructor() {
    companion object Symbols_C {
        // TODO: Add some simple binding methods for symbols, like:
        //       bind(klass: KClass<out T>, symbol: Symbol)
        //       unbind(klass: KClass<out T>)
        //       We just need an atomic concurrent hash-map, its easy to use, easy to implement, and fast!
        //       This will become necessary, when customers or handlers start using own data models!

       /**
         * Creates a new symbol with the given description, does not add the symbol into the global registry.
         * @param description The optional description.
         * @return A new symbol with the given description.
         * @since 3.0
         */
        fun newInstance(description: String? = null): Symbol

        /**
         * Returns the symbol for the given string from the global registry. It is recommended to use a package name, for example _com.here.naksha_ is used for [DEFAULT_SYMBOL], the default Naksha multi-platform library.
         * @param key The symbol key; if _null_, a random symbol not part of the registry is created.
         * @return The existing symbol, if no such symbol exist yet, creates a new one.
         * @since 3.0
         */
        fun forName(key: String?): Symbol

        /**
         * Returns the default [symbol][Symbol] to bind the given [PlatformType] against. If no [symbol][Symbol] is found by any of the registered [symbol resolvers][SymbolResolver], this method returns [DEFAULT_SYMBOL].
         * @param type The [PlatformType] for which to return the default [symbol][Symbol].
         * @return The default [symbol][Symbol] to bind the given [PlatformType] against.
         * @since 3.0
         */
        fun of(type: PlatformType<*>): Symbol

        /**
         * Returns a read-only list of all currently registered symbol resolvers.
         * @return The list of all currently registered symbol resolvers.
         * @since 3.0
         */
        fun getSymbolResolvers(): List<SymbolResolver>

        /**
         * Compares and sets the symbol resolvers in an atomic way.
         * @param expect The list that was read.
         * @param value The new list that should be set, a read-only copy will be done.
         * @return _true_ if the set was successful; _false_ if it failed (another thread modified the list concurrently).
         * @since 3.0
         */
        fun compareAndSetSymbolResolvers(expect: List<SymbolResolver>, value: List<SymbolResolver>): Boolean

        /**
         * A simple helper that adds the given symbol resolver to the end of the resolver list.
         * @param symbolResolver The symbol resolved to add.
         * @since 3.0
         */
        fun pushSymbolResolver(symbolResolver: SymbolResolver)

        /**
         * A simple helper that adds the given symbol resolver to the start of the resolver list.
         * @param symbolResolver The symbol resolved to add.
         * @since 3.0
         */
        fun unshiftSymbolResolver(symbolResolver: SymbolResolver)

        /**
         * Returns the value of a symbol, stored with the platform object.
         * @param obj The object to access.
         * @param symbol The symbol.
         * @return The value or _undefined_ if no such symbol exist.
         * @since 3.0
         */
        fun get(obj: PlatformObject, symbol: Symbol = DEFAULT_SYMBOL): Any?

        /**
         * Sets the value of a symbol, stored with the platform object.
         * @param obj The object to access.
         * @param symbol The symbol.
         * @param value The value to store, if being _undefined_, then the symbol is removed.
         * @return The previously assigned value; _undefined_ if no such symbol existed.
         * @since 3.0
         */
        fun set(obj: PlatformObject, symbol: Symbol = DEFAULT_SYMBOL, value: Any?): Any?

        /**
         * Tests if the symbol exists, stored with the platform object.
         * @param obj The object to access.
         * @param symbol The symbol to test.
         * @return _true_ if the symbol exists; _false_ otherwise.
         * @since 3.0
         */
        fun has(obj: PlatformObject, symbol: Symbol = DEFAULT_SYMBOL): Boolean

        /**
         * Removes the symbol, stored with the platform object.
         * @param obj The object to access.
         * @param symbol The symbol.
         * @return The value being removed; _undefined_ if no such symbol existed.
         * @since 3.0
         */
        fun remove(obj: PlatformObject, symbol: Symbol = DEFAULT_SYMBOL): Any?

        /**
         * Returns an iterator above all symbols of a platform object.
         * @param obj The object to iterate.
         * @return The iterator above all symbols, where the value is an array with the element at index 0 being the key (the symbol)
         * and the element at index 1 being the value.
         * @since 3.0
         */
        fun iterator(obj: PlatformObject): PlatformIterator<PlatformList>

        /**
         * Collect all the keys of the object properties (being [String]).
         * @param obj The object from which to get all property keys.
         * @return The keys of the object properties.
         * @since 3.0
         */
        fun keys(obj: PlatformObject): Array<Symbol> // Object.getOwnPropertySymbols(x)

        /**
         * Returns the amount of symbols assigned to the given platform object.
         * @param obj The platform object for which to count the symbols.
         * @return The amount of symbols.
         * @since 3.0
         */
        fun count(obj: PlatformObject): Int
    }
}