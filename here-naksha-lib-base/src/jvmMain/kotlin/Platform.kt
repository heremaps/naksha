package com.here.naksha.lib.base

import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import sun.misc.Unsafe
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.full.isSuperclassOf
import kotlin.reflect.full.primaryConstructor

/**
 * The JVM implementation of the static Naksha multi-platform singleton.
 */
@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
actual class Platform {
    actual companion object {
        internal val module = SimpleModule().apply {
            addAbstractTypeMapping(Map::class.java, JvmMap::class.java as Class<Map<*, *>>)
            addAbstractTypeMapping(List::class.java, JvmList::class.java)
        }

        internal val mapper = jacksonObjectMapper().apply {
            registerKotlinModule()
            registerModule(module)
        }

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
         * The symbol (_com.here.naksha.lib.nak_) to store the default Naksha multi-platform types in.
         */
        @JvmStatic
        actual val DEFAULT_SYMBOL: Symbol = Symbols.forName("com.here.naksha.lib.nak")

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
        actual fun isAssignableFrom(fromSource: KClass<*>, toTarget: KClass<*>): Boolean = fromSource.java.isAssignableFrom(toTarget.java)

        @JvmStatic
        actual fun isProxyKlass(klass: KClass<*>): Boolean = Proxy::class.isSuperclassOf(klass)

        @JvmStatic
        actual fun <T : Any> klassBy(constructor: KFunction<T>): KClass<out T> {
            TODO("Implement me!")
        }

        @JvmStatic
        actual fun <T : Any> klassOf(o: T): KClass<out T> {
            TODO("Implement me!")
        }

//        @JvmStatic
//        actual fun <T : Proxy> proxy(o: Any, klass: OldBaseKlass<T>, vararg args: Any?): T {
//            val data = toJvmObject(o)
//            require(data != null)
//            val sym = klass.symbol()
//            var nakType: Any? = data[sym]
//            if (klass.isInstance(nakType)) return nakType as T
//            require(klass.isAssignable(data))
//            nakType = klass.newInstance(*args)
//            nakType.data = data
//            data[sym] = nakType
//            return nakType
//        }
//
//        @JvmStatic
//        actual fun <T : Proxy> forceAssign(o: Any, klass: OldBaseKlass<T>, vararg args: Any?): T {
//            val data = toJvmObject(o)
//            require(data != null)
//            val sym = klass.symbol()
//            var nakType: Any? = data[sym]
//            if (klass.isInstance(nakType)) return nakType as T
//            require(klass.getPlatformKlass().isInstance(data))
//            nakType = klass.newInstance(*args)
//            data[sym] = nakType
//            return nakType
//        }
//
//        @JvmStatic
//        actual fun isAssignable(o: Any?, klass: OldBaseKlass<*>): Boolean {
//            val data = toJvmObject(o)
//            return data != null && klass.isAssignable(data)
//        }
//

        @JvmStatic
        actual fun newMap(vararg entries: Any?): PlatformMap = JvmMap(*entries)

        @JvmStatic
        actual fun newList(vararg entries: Any?): PlatformList = JvmList(*entries)

        @JvmStatic
        actual fun newByteArray(size: Int): ByteArray = ByteArray(size)

        @JvmStatic
        actual fun newDataView(byteArray: ByteArray, offset: Int, size: Int): PlatformDataView = JvmDataView(byteArray, offset, size)

        @JvmStatic
        actual fun unbox(value: Any?): Any? = if (value is Proxy) value.data() as? JvmObject else value

        /**
         * Returns the [JvmObject] of the given object. This method uses the same implementation as [unbox].
         * @param o Any object.
         * @return The [JvmObject] or _null_.
         */
        @JvmStatic
        fun toJvmObject(o: Any?): JvmObject? = if (o is Proxy) o.data() as? JvmObject else if (o is JvmObject) o else null

        @JvmStatic
        actual fun toInt(value: Any): Int = when (value) {
            is Number -> value.toInt()
            is String -> Integer.parseInt(value)
            else -> throw IllegalArgumentException("Failed to convert object to integer")
        }

        @JvmStatic
        actual fun toInt64(value: Any): Int64 = when (value) {
            is Number -> longToInt64(value.toLong())
            is String -> longToInt64(java.lang.Long.parseLong(value))
            else -> throw IllegalArgumentException("Failed to convert object to 64-bit integer")
        }

        @JvmStatic
        actual fun toDouble(value: Any): Double = when (value) {
            is Number -> value.toDouble()
            is String -> java.lang.Double.parseDouble(value)
            else -> throw IllegalArgumentException("Failed to convert object to double")
        }

        @JvmStatic
        actual fun toDoubleRawBits(i: Int64): Double = java.lang.Double.longBitsToDouble(i.toLong())

        @JvmStatic
        fun toLong(value: Any): Long = when (value) {
            is Number -> value.toLong()
            is String -> java.lang.Long.parseLong(value)
            else -> throw IllegalArgumentException("Failed to convert object to long")
        }

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
        actual fun isNumber(o: Any?): Boolean = o is Number

        @JvmStatic
        actual fun isInteger(o: Any?): Boolean = o is Byte || o is Short || o is Int || o is Long || o is JvmInt64

        @JvmStatic
        actual fun isDouble(o: Any?): Boolean = o is Double

//        @JvmStatic
//        actual fun has(o: Any?, key: Any?): Boolean {
//            val p = toJvmObject(o) ?: return false
//            if (key is JvmSymbol) return p.contains(key)
//            if (key is String) return p.contains(key)
//            if (key is Int && p is JvmList) return key >= 0 && key < p.size
//            return false
//        }
//
//        @JvmStatic
//        actual fun get(o: Any, key: Any): Any? {
//            val p = toJvmObject(o) ?: return undefined
//            if (key is JvmSymbol) return p[key]
//            if (key is String) return if (p.contains(key)) p[key] else undefined
//            if (key is Int && p is JvmList) return if (key >= 0 && key < p.size) p[key] else undefined
//            return undefined
//        }
//
//        @JvmStatic
//        actual fun set(o: Any, key: Any, value: Any?): Any? {
//            val p = toJvmObject(o)
//            require(p != null) { "The given object is not backed by a native base object" }
//            if (key is JvmSymbol) return p.set(key, value)
//            if (key is String) return p.set(key, value)
//            if (key is Int && p is JvmList) return p.set(key, value)
//            throw IllegalArgumentException("The given key is invalid, must be symbol, string or integer (only for array)")
//        }
//
//        @JvmStatic
//        actual fun delete(o: Any, key: Any): Any? {
//            val p = toJvmObject(o) ?: return undefined
//            if (key is Symbol) return p.remove(key)
//            if (key is String) return p.remove(key)
//            if (key is Int && p is JvmList) return p.remove(key)
//            return undefined
//        }
//
//        @JvmStatic
//        actual fun count(obj: Any?): Int = if (obj is JvmObject) obj.properties?.size ?: 0 else 0
//
//        @JvmStatic
//        actual fun length(a: PlatformList?): Int = if (a is JvmList) a.size else 0
//
//        private val EMPTY_KEYS = arrayOf<String>()
//        private val EMPTY_VALUES = arrayOf<Any?>()
//        private val EMPTY_SYMBOLS = arrayOf<Symbol>()
//        private val NAK_ONLY_SYMBOLS = arrayOf(DEFAULT_SYMBOL)
//
//        @JvmStatic
//        actual fun keys(obj: Any): Array<String> =
//            if (obj is JvmObject) obj.properties?.keys?.toTypedArray()
//                ?: EMPTY_KEYS else throw IllegalArgumentException("Invalid object given")
//
//        @JvmStatic
//        actual fun keysOfMembers(obj: Any): Array<Symbol> {
//            require(obj is JvmObject)
//            val symbols = obj.symbols
//            if (symbols != null) return symbols.keys.toTypedArray()
//            if (obj.baseSym != undefined) return NAK_ONLY_SYMBOLS
//            return EMPTY_SYMBOLS
//        }
//
//        @JvmStatic
//        actual fun values(obj: Any): Array<Any?> =
//            if (obj is JvmObject) obj.properties?.values?.toTypedArray()
//                ?: EMPTY_VALUES else throw IllegalArgumentException("Invalid object given")


        @JvmStatic
        actual fun compare(a: Any?, b: Any?): Int = throw UnsupportedOperationException()

        @JvmStatic
        actual fun hashCodeOf(o: Any?): Int = throw UnsupportedOperationException()

        @JvmStatic
        actual fun <T : Any> newInstanceOf(klass: KClass<T>): T = klass.primaryConstructor!!.call()

        @JvmStatic
        actual fun toJSON(obj: Any?): String {
            return mapper.writeValueAsString(obj)
            // JvmInt64 <-> Long
        }

        @JvmStatic
        actual fun fromJSON(json: String): Any? {
            return mapper.readValue(json,Any::class.java)
        }

        @JvmStatic
        actual fun fromPlatform(obj: Any?, importers: List<PlatformImporter>): Any? {
            TODO("Implement me!")
        }

        @JvmStatic
        actual fun toPlatform(obj: Any?, exporters: List<PlatformExporter>): Any? {
            TODO("Implement me!")
        }

//        /**
//         * Returns the Kotlin class for the given JAVA class.
//         * @param javaClass The JAVA class.
//         * @return The Kotlin class.
//         */
//        @JvmStatic
//        fun <T : Any> klassOf(javaClass: Class<T>): KClass<T> = javaClass.kotlin
//
//        /**
//         * Returns the Kotlin class for the given JAVA class.
//         * @param javaClass The JAVA class.
//         * @return The Kotlin class.
//         */
//        @JvmStatic
//        fun <T : Any> klassOf(javaClass: Class<T>): KClass<T> = javaClass.kotlin
        /**
         * The iterator member.
         */
        actual val ITERATOR: Symbol
            get() = TODO("Not yet implemented")

        /**
         * The KClass for [Any].
         */
        actual val anyKlass: KClass<Any>
            get() = TODO("Not yet implemented")

        /**
         * The KClass for [Boolean].
         */
        actual val booleanKlass: KClass<Boolean>
            get() = TODO("Not yet implemented")

        /**
         * The KClass for [Short].
         */
        actual val shortKlass: KClass<Short>
            get() = TODO("Not yet implemented")

        /**
         * The KClass for [Int].
         */
        actual val intKlass: KClass<Int>
            get() = Int::class

        /**
         * The KClass for [Int64].
         */
        actual val int64Klass: KClass<Int64>
            get() = TODO("Not yet implemented")

        /**
         * The KClass for [Double].
         */
        actual val doubleKlass: KClass<Double>
            get() = TODO("Not yet implemented")

        /**
         * The KClass for [String].
         */
        actual val stringKlass: KClass<String>
            get() = String::class

        /**
         * The KClass for [PlatformObject].
         */
        actual val objectKlass: KClass<PlatformObject>
            get() = TODO("Not yet implemented")

        /**
         * The KClass for [PlatformList].
         */
        actual val listKlass: KClass<PlatformList>
            get() = TODO("Not yet implemented")

        /**
         * The KClass for [PlatformMap].
         */
        actual val mapKlass: KClass<PlatformMap>
            get() = TODO("Not yet implemented")

        /**
         * The KClass for [PlatformDataViewApi].
         */
        actual val dataViewKlass: KClass<PlatformDataView>
            get() = TODO("Not yet implemented")

        /**
         * Tests if the given value is _null_ or _undefined_.
         * @param any The value to test.
         * @return _true_ if the value is _null_ or _undefined_; false otherwise.
         */
        actual fun isNil(any: Any?): Boolean = any == null

        /**
         * Creates an undefined value for the given type or returns the cached one.
         * @param klass The type for which to create an undefined value.
         * @return The undefined value.
         */
        actual fun <T : Any> undefinedOf(klass: KClass<T>): T {
            TODO("Not yet implemented")
        }
    }
}
