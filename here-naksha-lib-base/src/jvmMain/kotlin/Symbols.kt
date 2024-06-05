package com.here.naksha.lib.base

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference
import kotlin.reflect.KClass

/**
 * A singleton that grants access to symbols. Symbols are a way to bind proxies (and other hidden data) to platform objects.
 */
@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
actual class Symbols {
    actual companion object {
        @JvmStatic
        private val symbolsCache = ConcurrentHashMap<String, Symbol>()

        @JvmStatic
        actual fun newSymbol(description: String?): Symbol = JvmSymbol(description?:"")

        @JvmStatic
        actual fun forName(key: String?): Symbol {
            if (key == null) return JvmSymbol()
            var symbol = symbolsCache[key]
            if (symbol == null) {
                symbol = JvmSymbol(key)
                val existing = symbolsCache.putIfAbsent(key, symbol)
                if (existing != null) return existing
            }
            return symbol
        }

        @JvmStatic
        private val symbolResolver = AtomicReference<List<SymbolResolver>>()

        @JvmStatic
        actual fun <T : Any> symbolOf(klass: KClass<out T>): Symbol {
            val resolvers = symbolResolver.get()
            if (resolvers != null) {
                for (resolver in resolvers) {
                    val symbol = resolver.resolve(klass)
                    if (symbol != null) return symbol
                }
            }
            return Platform.DEFAULT_SYMBOL
        }

        @JvmStatic
        actual fun getSymbolResolvers(): List<SymbolResolver> = symbolResolver.get() ?: emptyList()

        @JvmStatic
        actual fun compareAndSetSymbolResolvers(expect: List<SymbolResolver>, value: List<SymbolResolver>): Boolean
            = symbolResolver.compareAndSet(expect.ifEmpty { null }, value.ifEmpty { null })

        /**
         * Returns the value of a symbol, stored with the platform object.
         * @param obj The object to access.
         * @param key The symbol.
         * @return The value or _undefined_ if no such symbol exist.
         */
        actual fun get(obj: PlatformObject, key: Symbol): Any? = if (obj is JvmObject) obj[key] else null

        /**
         * Sets the value of a symbol, stored with the platform object.
         * @param obj The object to access.
         * @param key The symbol.
         * @param value The value to store, if being _undefined_, then the symbol is removed.
         * @return The previously assigned value; _undefined_ if no such symbol existed.
         */
        actual fun set(obj: PlatformObject, key: Symbol, value: Any?): Any? {
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