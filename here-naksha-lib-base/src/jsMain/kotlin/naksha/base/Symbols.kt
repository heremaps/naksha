@file:Suppress("OPT_IN_USAGE", "EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package naksha.base

import naksha.base.Platform.PlatformCompanion.DEFAULT_SYMBOL
import kotlin.reflect.KClass

/**
 * A singleton that grants access to symbols. Symbols are a way to bind proxies (and other hidden data) to platform objects.
 */
@JsExport
actual class Symbols {
    actual companion object SymbolsCompanion {

        /**
         * Creates a new symbol with the given description.
         * @param description The optional description.
         * @return A new symbol with the given description.
         */
        @JsStatic
        actual fun newInstance(description: String?): Symbol = js("Symbol(description)").unsafeCast<Symbol>()

        /**
         * Returns the symbol for the given string from the global registry. It is recommended to use a package name, for example
         * _com.here.naksha_ is used for [DEFAULT_SYMBOL], the default Naksha multi-platform library.
         * @param key The symbol key; if _null_, a random symbol not part of the registry is created.
         * @return The existing symbol, if no such symbol exist yet, creates a new one.
         */
        @JsStatic
        actual fun forName(key: String?): Symbol = js("Symbol.for(description)").unsafeCast<Symbol>()

        private var symbolResolvers: List<SymbolResolver>? = null

        /**
         * Returns the default symbol to bind the given [KClass] against. If no symbol is returned by the registered symbol resolvers,
         * it returns [DEFAULT_SYMBOL].
         * @param klass The [KClass] for which to return the default symbol.
         * @return The default symbol to bind the given [KClass] against.
         */
        @Suppress("NON_EXPORTABLE_TYPE")
        @JsStatic
        actual fun <T : Any> of(klass: KClass<out T>): Symbol {
            val resolvers = symbolResolvers
            if (resolvers != null) {
                for (resolver in resolvers) {
                    try {
                        val symbol = resolver.call(klass)
                        if (symbol != null) return symbol
                    } catch (e: Exception) {
                        Platform.logger.error("The symbol resolver raised an exception: {}", e.stackTraceToString())
                    }
                }
            }
            return DEFAULT_SYMBOL
        }

        /**
         * Returns a read-only list of all currently registered symbol resolvers.
         * @return The list of all currently registered symbol resolvers.
         */
        @JsStatic
        actual fun getSymbolResolvers(): List<SymbolResolver> = symbolResolvers ?: emptyList()

        /**
         * Compares and sets the symbol resolvers in an atomic way.
         * @param expect The list that was read.
         * @param value The new list that should be set, a read-only copy will be done.
         * @return _true_ if the set was successful; _false_ if it failed (another thread modified the list concurrently).
         */
        @JsStatic
        actual fun compareAndSetSymbolResolvers(
            expect: List<SymbolResolver>,
            value: List<SymbolResolver>
        ): Boolean {
            if (symbolResolvers === expect) {
                symbolResolvers = value
                return true
            }
            return false
        }

        /**
         * Returns the value of a symbol, stored with the platform object.
         * @param obj The object to access.
         * @param symbol The symbol.
         * @return The value or _undefined_ if no such symbol exist.
         */
        @JsStatic
        actual fun get(obj: PlatformObject, symbol: Symbol): Any? = obj.asDynamic()[symbol]

        /**
         * Sets the value of a symbol, stored with the platform object.
         * @param obj The object to access.
         * @param symbol The symbol.
         * @param value The value to store, if being _undefined_, then the symbol is removed.
         * @return The previously assigned value; _undefined_ if no such symbol existed.
         */
        @JsStatic
        actual fun set(
            obj: PlatformObject,
            symbol: Symbol,
            value: Any?
        ): Any? {
            val o = obj.asDynamic()
            val old = o[symbol]
            o[symbol] = value
            return old
        }

        /**
         * Tests if the symbol exists, stored with the platform object.
         * @param obj The object to access.
         * @param symbol The symbol to test.
         * @return _true_ if the symbol exists; _false_ otherwise.
         */
        @JsStatic
        actual fun has(obj: PlatformObject, symbol: Symbol): Boolean = js("Object.hasOwn(obj, symbol)").unsafeCast<Boolean>()

        /**
         * Removes the symbol, stored with the platform object.
         * @param obj The object to access.
         * @param symbol The symbol.
         * @return The value being removed; _undefined_ if no such symbol existed.
         */
        @JsStatic
        actual fun remove(obj: PlatformObject, symbol: Symbol): Any? {
            val o = obj.asDynamic()
            val old = o[symbol]
            js("delete o[symbol]")
            return old
        }

        /**
         * Returns an iterator above all symbols of a platform object.
         * @param obj The object to iterate.
         * @return The iterator above all symbols, where the value is an array with the element at index 0 being the key (the symbol)
         * and the element at index 1 being the value.
         */
        @JsStatic
        actual fun iterator(obj: PlatformObject): PlatformIterator<PlatformList> =
            js("Object.getOwnPropertySymbols(obj)[Symbol.iterator]()").unsafeCast<PlatformIterator<PlatformList>>()

        /**
         * Collect all the keys of the object properties (being [String]).
         * @param obj The object from which to get all property keys.
         * @return The keys of the object properties.
         */
        @JsStatic
        actual fun keys(obj: PlatformObject): Array<Symbol> = js("Object.getOwnPropertySymbols(obj)").unsafeCast<Array<Symbol>>()

        /**
         * Returns the amount of symbols assigned to the given platform object.
         * @param obj The platform object for which to count the symbols.
         * @return The amount of symbols.
         */
        @JsStatic
        actual fun count(obj: PlatformObject): Int = js("Object.getOwnPropertySymbols(obj)").length.unsafeCast<Int>()

        /**
         * A simple helper that adds the given symbol resolver to the end of the resolver list.
         * @param symbolResolver The symbol resolved to add.
         */
        @JsStatic
        actual fun pushSymbolResolver(symbolResolver: SymbolResolver) {
            val resolvers = symbolResolvers
            symbolResolvers = if (resolvers == null) {
                listOf(symbolResolver)
            } else {
                listOf(*resolvers.toTypedArray(), symbolResolver)
            }
        }

        /**
         * A simple helper that adds the given symbol resolver to the start of the resolver list.
         * @param symbolResolver The symbol resolved to add.
         */
        @JsStatic
        actual fun unshiftSymbolResolver(symbolResolver: SymbolResolver) {
            val resolvers = symbolResolvers
            symbolResolvers = if (resolvers == null) {
                listOf(symbolResolver)
            } else {
                listOf(symbolResolver, *resolvers.toTypedArray())
            }
        }
    }
}