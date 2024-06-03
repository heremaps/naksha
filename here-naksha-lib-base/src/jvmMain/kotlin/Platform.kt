@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING", "unused", "UNCHECKED_CAST")

package com.here.naksha.lib.base

import sun.misc.Unsafe
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.reflect.KClass

/**
 * The JVM implementation of the static Naksha multi-platform singleton.
 */
actual class Platform {
    actual companion object {
        /**
         * The cache stores the 64-bit integers between -1024 and +1023 with 0 being at index 0, 1023 at index 1023, -1024 at index 1024
         * and -1 at index 2047. To query, do: `cached[(value.toInt() shl 21) ushr 21)]`
         */
        @JvmStatic
        internal val int64Cache = Array(2048) { JvmInt64(((it shl 21) shr 21).toLong()) }

        /**
         * The cache for all declared symbols.
         */
        @JvmStatic
        internal val symbolsCache = ConcurrentHashMap<String, Symbol>()

        /**
         * The constant for undefined.
         */
        @JvmStatic
        actual val undefined: Any = Object()

        /**
         * The symbol (_com.here.naksha.lib.nak_) to store the default Naksha multi-platform types in.
         */
        @JvmStatic
        actual val DEFAULT_SYMBOL: Symbol = symbol("com.here.naksha.lib.nak")

        /**
         * The maximum value of a 64-bit integer.
         * @return The maximum value of a 64-bit integer.
         */
        @JvmStatic
        actual val INT64_MAX_VALUE: Int64 = JvmInt64(Long.MAX_VALUE)

        /**
         * The minimum value of a 64-bit integer.
         * @return The minimum value of a 64-bit integer.
         */
        @JvmStatic
        actual val INT64_MIN_VALUE: Int64 = JvmInt64(Long.MIN_VALUE)

        /**
         * The minimum integer that can safely stored in a double.
         * @return The minimum integer that can safely stored in a double.
         */
        @JvmStatic
        actual val MAX_SAFE_INT: Double = 9007199254740991.0

        /**
         * The maximum integer that can safely stored in a double.
         * @return The maximum integer that can safely stored in a double.
         */
        @JvmStatic
        actual val MIN_SAFE_INT: Double = -9007199254740991.0

        /**
         * The reference to TheUnsafe class.
         */
        @JvmStatic
        val unsafe: Unsafe

        /**
         * The base-offset in a byte-array.
         */
        @JvmStatic
        val baseOffset: Int

        @JvmStatic
        private val initialized = AtomicBoolean(false)

        init {
            val unsafeConstructor = Unsafe::class.java.getDeclaredConstructor()
            unsafeConstructor.isAccessible = true
            unsafe = unsafeConstructor.newInstance()
            val someByteArray = ByteArray(8)
            baseOffset = unsafe.arrayBaseOffset(someByteArray.javaClass)
        }

        @JvmStatic
        actual fun initialize(vararg parameters: Any?): Boolean {
            if (initialized.compareAndSet(false, true)) {
                // TODO: Do we need to do anything?
                return true
            }
            return false
        }

        // TODO: Add cache and normalization!
        @JvmStatic
        actual fun intern(s: String, cd: Boolean): String = s

        @JvmStatic
        actual fun <T : Proxy> getAssignment(o: Any?, symbol: Symbol): T? = toJvmObject(o)?.get(symbol) as? T

        @JvmStatic
        actual fun <T : Proxy> proxy(o: Any, klass: OldBaseKlass<T>, vararg args: Any?): T {
            val data = toJvmObject(o)
            require(data != null)
            val sym = klass.symbol()
            var nakType: Any? = data[sym]
            if (klass.isInstance(nakType)) return nakType as T
            require(klass.isAssignable(data))
            nakType = klass.newInstance(*args)
            nakType.data = data
            data[sym] = nakType
            return nakType
        }

        @JvmStatic
        actual fun <T : Proxy> forceAssign(o: Any, klass: OldBaseKlass<T>, vararg args: Any?): T {
            val data = toJvmObject(o)
            require(data != null)
            val sym = klass.symbol()
            var nakType: Any? = data[sym]
            if (klass.isInstance(nakType)) return nakType as T
            require(klass.getPlatformKlass().isInstance(data))
            nakType = klass.newInstance(*args)
            data[sym] = nakType
            return nakType
        }

        @JvmStatic
        actual fun isAssignable(o: Any?, klass: OldBaseKlass<*>): Boolean {
            val data = toJvmObject(o)
            return data != null && klass.isAssignable(data)
        }

        @JvmStatic
        actual fun symbol(key: String?): Symbol {
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
        actual fun newObject(vararg entries: Any?): PlatformObject = JvmPObject(*entries)

        @JvmStatic
        actual fun newArray(vararg elementClass: Any?): PlatformList = JvmPList(*elementClass)

        @JvmStatic
        actual fun newByteArray(size: Int): ByteArray = ByteArray(size)

        @JvmStatic
        actual fun newDataView(byteArray: ByteArray, offset: Int, size: Int): PlatformDataViewApi = JvmNativeDataView(byteArray, offset, size)

        @JvmStatic
        actual fun unbox(value: Any?): Any? = if (value is Proxy) value.__data as? JvmObject else value

        /**
         * Returns the [JvmObject] of the given object. This method uses the same implementation as [unbox].
         * @param o Any object.
         * @return The [JvmObject] or _null_.
         */
        @JvmStatic
        fun toJvmObject(o: Any?): JvmObject? = if (o is Proxy) o.__data as? JvmObject else if (o is JvmObject) o else null

        @JvmStatic
        actual fun toInt(value: Any): Int = when (value) {
            is Number -> value.toInt()
            is JvmInt64 -> value.toInt()
            is String -> Integer.parseInt(value)
            else -> throw IllegalArgumentException("Failed to convert object to integer")
        }

        @JvmStatic
        actual fun toInt64(value: Any): Int64 = when (value) {
            is Number -> longToInt64(value.toLong())
            is JvmInt64 -> value
            is String -> longToInt64(java.lang.Long.parseLong(value))
            else -> throw IllegalArgumentException("Failed to convert object to 64-bit integer")
        }

        @JvmStatic
        actual fun toDouble(value: Any): Double = when (value) {
            is Number -> value.toDouble()
            is JvmInt64 -> value.toDouble()
            is String -> java.lang.Double.parseDouble(value)
            else -> throw IllegalArgumentException("Failed to convert object to double")
        }

        @JvmStatic
        actual fun toDoubleRawBits(i: Int64): Double = java.lang.Double.longBitsToDouble(i.toLong())

        @JvmStatic
        actual fun toInt64RawBits(d: Double): Int64 = longToInt64(java.lang.Double.doubleToRawLongBits(d))

        @JvmStatic
        actual fun longToInt64(value: Long): Int64 {
            if (value >= -1024 && value < 1024) return int64Cache[(value.toInt() shl 21) ushr 21]
            if (value == INT64_MAX_VALUE.toLong()) return INT64_MAX_VALUE
            if (value == INT64_MIN_VALUE.toLong()) return INT64_MIN_VALUE
            return JvmInt64(value)
        }

        @JvmStatic
        actual fun int64ToLong(value: Int64): Long = value.toLong()

        @JvmStatic
        actual fun isNative(o: Any?): Boolean = o is JvmObject || o is Number || o is JvmInt64 || o is String || o is Boolean

        @JvmStatic
        actual fun isString(o: Any?): Boolean = o is String

        @JvmStatic
        actual fun isNumber(o: Any?): Boolean = o is Number || o is JvmInt64

        @JvmStatic
        actual fun isInteger(o: Any?): Boolean = o is Byte || o is Short || o is Int || o is JvmInt64

        @JvmStatic
        actual fun isDouble(o: Any?): Boolean = o is Double

        @JvmStatic
        actual fun isInt(o: Any?): Boolean = o is Int

        @JvmStatic
        actual fun isInt64(o: Any?): Boolean = o is JvmInt64

        @JvmStatic
        actual fun isObject(o: Any?): Boolean = o is JvmPObject

        @JvmStatic
        actual fun isArray(o: Any?): Boolean = o is JvmPList

        @JvmStatic
        actual fun isSymbol(o: Any?): Boolean = o is JvmSymbol

        @JvmStatic
        actual fun isByteArray(o: Any?): Boolean = o is ByteArray

        @JvmStatic
        actual fun isDataView(o: Any?): Boolean = o is JvmNativeDataView

        @JvmStatic
        actual fun has(o: Any?, key: Any?): Boolean {
            val p = toJvmObject(o) ?: return false
            if (key is JvmSymbol) return p.contains(key)
            if (key is String) return p.contains(key)
            if (key is Int && p is JvmPList) return key >= 0 && key < p.size
            return false
        }

        @JvmStatic
        actual fun get(o: Any, key: Any): Any? {
            val p = toJvmObject(o) ?: return undefined
            if (key is JvmSymbol) return p[key]
            if (key is String) return if (p.contains(key)) p[key] else undefined
            if (key is Int && p is JvmPList) return if (key >= 0 && key < p.size) p[key] else undefined
            return undefined
        }

        @JvmStatic
        actual fun set(o: Any, key: Any, value: Any?): Any? {
            val p = toJvmObject(o)
            require(p != null) { "The given object is not backed by a native base object" }
            if (key is JvmSymbol) return p.set(key, value)
            if (key is String) return p.set(key, value)
            if (key is Int && p is JvmPList) return p.set(key, value)
            throw IllegalArgumentException("The given key is invalid, must be symbol, string or integer (only for array)")
        }

        @JvmStatic
        actual fun delete(o: Any, key: Any): Any? {
            val p = toJvmObject(o) ?: return undefined
            if (key is Symbol) return p.remove(key)
            if (key is String) return p.remove(key)
            if (key is Int && p is JvmPList) return p.remove(key)
            return undefined
        }

        @JvmStatic
        actual fun arrayIterator(o: PlatformList): PlatformIterator<Int, Any?> = JvmPArrayIterator(o as JvmPList)

        @JvmStatic
        actual fun objectIterator(o: PlatformObject): PlatformIterator<String, Any?> = JvmPObjectIterator(o as JvmPObject)

        @JvmStatic
        actual fun count(obj: Any?): Int = if (obj is JvmObject) obj.properties?.size ?: 0 else 0

        @JvmStatic
        actual fun length(a: PlatformList?): Int = if (a is JvmPList) a.size else 0

        private val EMPTY_KEYS = arrayOf<String>()
        private val EMPTY_VALUES = arrayOf<Any?>()
        private val EMPTY_SYMBOLS = arrayOf<Symbol>()
        private val NAK_ONLY_SYMBOLS = arrayOf(DEFAULT_SYMBOL)

        @JvmStatic
        actual fun keys(obj: Any): Array<String> =
            if (obj is JvmObject) obj.properties?.keys?.toTypedArray()
                ?: EMPTY_KEYS else throw IllegalArgumentException("Invalid object given")

        @JvmStatic
        actual fun keysOfMembers(obj: Any): Array<Symbol> {
            require(obj is JvmObject)
            val symbols = obj.symbols
            if (symbols != null) return symbols.keys.toTypedArray()
            if (obj.baseSym != undefined) return NAK_ONLY_SYMBOLS
            return EMPTY_SYMBOLS
        }

        @JvmStatic
        actual fun values(obj: Any): Array<Any?> =
            if (obj is JvmObject) obj.properties?.values?.toTypedArray()
                ?: EMPTY_VALUES else throw IllegalArgumentException("Invalid object given")


        @JvmStatic
        actual fun compare(a: Any?, b: Any?): Int = throw UnsupportedOperationException()

        @JvmStatic
        actual fun hashCodeOf(o: Any?): Int = throw UnsupportedOperationException()

        /**
         * Returns the Kotlin class for the given JAVA class.
         * @param javaClass The JAVA class.
         * @return The Kotlin class.
         */
        @JvmStatic
        fun <T : Any> klassOf(javaClass: Class<T>): KClass<T> = javaClass.kotlin

        /**
         * Returns the Kotlin class for the given JAVA class.
         * @param javaClass The JAVA class.
         * @return The Kotlin class.
         */
        @JvmStatic
        fun <T : Any> klassOf(javaClass: Class<T>): KClass<T> = javaClass.kotlin
    }
}
