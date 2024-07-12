package naksha.base

import naksha.base.Platform.DEFAULT_SYMBOL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference
import kotlin.reflect.KClass

/**
 * A singleton that grants access to symbols. Symbols are a way to bind proxies (and other hidden data) to platform objects.
 */
@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
actual object Symbols {
    @JvmStatic
    private val symbolsCache = ConcurrentHashMap<String, Symbol>()

    @JvmStatic
    actual fun newInstance(description: String?): Symbol = JvmSymbol(description ?: "")

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

    private val symbolResolverRef = AtomicReference<List<SymbolResolver>?>()

    actual fun <T : Any> of(klass: KClass<out T>): Symbol {
        val resolvers = symbolResolverRef.get()
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

    actual fun getSymbolResolvers(): List<SymbolResolver> = symbolResolverRef.get() ?: emptyList()

    actual fun compareAndSetSymbolResolvers(expect: List<SymbolResolver>, value: List<SymbolResolver>): Boolean =
        symbolResolverRef.compareAndSet(expect.ifEmpty { null }, value.ifEmpty { null })

    /**
     * Returns the value of a symbol, stored with the platform object.
     * @param obj The object to access.
     * @param symbol The symbol.
     * @return The value or _undefined_ if no such symbol exist.
     */
    actual fun get(obj: PlatformObject, symbol: Symbol): Any? = if (obj is JvmObject) obj.getSymbol(symbol) else null

    /**
     * Sets the value of a symbol, stored with the platform object.
     * @param obj The object to access.
     * @param symbol The symbol.
     * @param value The value to store, if being _undefined_, then the symbol is removed.
     * @return The previously assigned value; _undefined_ if no such symbol existed.
     */
    actual fun set(obj: PlatformObject, symbol: Symbol, value: Any?): Any? {
        require(obj is JvmObject) { "Unsupported platform object $obj" }
        return obj.setSymbol(symbol, value)
    }

    /**
     * Tests if the symbol exists, stored with the platform object.
     * @param obj The object to access.
     * @param symbol The symbol to test.
     * @return _true_ if the symbol exists; _false_ otherwise.
     */
    actual fun has(obj: PlatformObject, symbol: Symbol): Boolean {
        require(obj is JvmObject) { "Unsupported platform object $obj" }
        return obj.containsSymbol(symbol)
    }

    /**
     * Removes the symbol, stored with the platform object.
     * @param obj The object to access.
     * @param symbol The symbol.
     * @return The value being removed; _undefined_ if no such symbol existed.
     */
    actual fun remove(obj: PlatformObject, symbol: Symbol): Any? {
        require(obj is JvmObject) { "Unsupported platform object $obj" }
        return obj.removeSymbol(symbol)
    }

    /**
     * Returns an iterator above all symbols of a platform object.
     * @param obj The object to iterate.
     * @return The iterator above all symbols, where the value is an array with the element at index 0 being the key (the symbol)
     * and the element at index 1 being the value.
     */
    actual fun iterator(obj: PlatformObject): PlatformIterator<PlatformList> {
        require(obj is JvmObject) { "Unsupported platform object $obj" }
        return JvmMapEntryIterator(obj.symbols)
    }

    /**
     * Collect all the keys of the object properties (being [String]).
     * @param obj The object from which to get all property keys.
     * @return The keys of the object properties.
     */
    actual fun keys(obj: PlatformObject): Array<Symbol> {
        require(obj is JvmObject) { "Unsupported platform object $obj" }
        return obj.symbols?.keys?.toTypedArray() ?: emptyArray()
    }

    /**
     * Returns the amount of symbols assigned to the given platform object.
     * @param obj The platform object for which to count the symbols.
     * @return The amount of symbols.
     */
    actual fun count(obj: PlatformObject): Int {
        require(obj is JvmObject) { "Unsupported platform object $obj" }
        return obj.symbols().size
    }

    /**
     * A simple helper that adds the given symbol resolver to the end of the resolver list.
     * @param symbolResolver The symbol resolved to add.
     */
    actual fun pushSymbolResolver(symbolResolver: SymbolResolver) {
        while (true) {
            val current = symbolResolverRef.get()
            val _new = List((current?.size ?: 0) + 1) {
                if (current != null && it < current.size) current[it] else symbolResolver
            }
            if (symbolResolverRef.compareAndSet(current, _new)) break
        }
    }

    /**
     * A simple helper that adds the given symbol resolver to the start of the resolver list.
     * @param symbolResolver The symbol resolved to add.
     */
    actual fun unshiftSymbolResolver(symbolResolver: SymbolResolver) {
        while (true) {
            var current = symbolResolverRef.get()
            if (current == null) {
                current = listOf(symbolResolver)
                current = symbolResolverRef.compareAndExchange(null, current) ?: return
            }
            val _new = List(current.size + 1) {
                if (it == 0) symbolResolver else current[it - 1]
            }
            if (symbolResolverRef.compareAndSet(current, _new)) break
        }
    }
}