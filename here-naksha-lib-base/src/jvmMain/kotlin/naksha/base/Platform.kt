package naksha.base

import com.fasterxml.jackson.annotation.JsonAutoDetect
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.PropertyAccessor
import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.JsonFactoryBuilder
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.*
import com.fasterxml.jackson.databind.deser.BeanDeserializerModifier
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.databind.ser.BeanSerializerModifier
import com.fasterxml.jackson.module.kotlin.kotlinModule
import net.jpountz.lz4.LZ4Factory
import org.slf4j.LoggerFactory
import sun.misc.Unsafe
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.round
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
        @JvmStatic
        private val module = SimpleModule().apply {
            addAbstractTypeMapping(Map::class.java, JvmMap::class.java)
            addAbstractTypeMapping(MutableMap::class.java, JvmMap::class.java)
            addAbstractTypeMapping(List::class.java, JvmList::class.java)
            addAbstractTypeMapping(MutableList::class.java, JvmList::class.java)
        }
            .setDeserializerModifier(object : BeanDeserializerModifier() {
            override fun modifyDeserializer(
                config: DeserializationConfig,
                beanDesc: BeanDescription,
                deserializer: JsonDeserializer<*>
            ): JsonDeserializer<*> {
                return if (beanDesc.beanClass == Number::class.java || beanDesc.beanClass == String::class.java) {
                    CustomDeserializer()
                } else {
                    // If no special deserialization is required, delegate to the default deserializer
                    deserializer
                }
            }
        })
        //TODO implement the logic that will switch serializer, and the CustomSerializer itself
//            .setSerializerModifier(object : BeanSerializerModifier() {
//                override fun modifySerializer(
//                    config: SerializationConfig?,
//                    beanDesc: BeanDescription?,
//                    serializer: JsonSerializer<*>?
//                ): JsonSerializer<*>? {
//                    return if (beanDesc?.beanClass == Number::class.java) {
//                        CustomSerializer()
//                    } else {
//                        // If no special deserialization is required, delegate to the default serializer
//                        serializer
//                    }
//                }
//            })

        @JvmStatic
        private val objectMapper: ThreadLocal<ObjectMapper> = ThreadLocal.withInitial {
            val jsonFactory = JsonFactoryBuilder()
                .configure(JsonFactory.Feature.INTERN_FIELD_NAMES, false)
                .configure(JsonFactory.Feature.CANONICALIZE_FIELD_NAMES, false)
                .configure(JsonFactory.Feature.USE_THREAD_LOCAL_FOR_BUFFER_RECYCLING, true)
                .build()
            jsonFactory.configure(JsonGenerator.Feature.AUTO_CLOSE_TARGET, false)
            jsonFactory.configure(JsonParser.Feature.AUTO_CLOSE_SOURCE, false)
            JsonMapper.builder(jsonFactory)
                .enable(MapperFeature.DEFAULT_VIEW_INCLUSION)
                .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
                .enable(MapperFeature.SORT_CREATOR_PROPERTIES_FIRST)
                .serializationInclusion(JsonInclude.Include.NON_NULL)
                .visibility(PropertyAccessor.SETTER, JsonAutoDetect.Visibility.ANY)
                .visibility(PropertyAccessor.GETTER, JsonAutoDetect.Visibility.PUBLIC_ONLY)
                .visibility(PropertyAccessor.IS_GETTER, JsonAutoDetect.Visibility.NONE)
                .visibility(PropertyAccessor.CREATOR, JsonAutoDetect.Visibility.ANY)
                .configure(SerializationFeature.CLOSE_CLOSEABLE, false)
                .addModule(kotlinModule())
                .addModule(module)
                .build()
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
        actual fun <T : Any> klassFor(constructor: KFunction<T>): KClass<out T> {
            TODO("Implement me!")
        }

        @JvmStatic
        actual fun <T : Any> klassOf(o: T): KClass<out T> = o::class

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

        internal val toJsonOptions = ThreadLocal<ToJsonOptions>()

        @JvmStatic
        actual fun toJSON(obj: Any?, options: ToJsonOptions): String {
            toJsonOptions.set(options)
            return objectMapper.get().writeValueAsString(obj)
        }

        internal val fromJsonOptions = ThreadLocal<FromJsonOptions>()

        @JvmStatic
        actual fun fromJSON(json: String, options: FromJsonOptions): Any? {
            fromJsonOptions.set(options)
            return objectMapper.get().readValue(json, Any::class.java)
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
        @JvmStatic
        actual val ITERATOR: Symbol
            get() = TODO("Not yet implemented")

        /**
         * The KClass for [Any].
         */
        @JvmStatic
        actual val anyKlass: KClass<Any>
            get() = TODO("Not yet implemented")

        /**
         * The KClass for [Boolean].
         */
        @JvmStatic
        actual val booleanKlass: KClass<Boolean>
            get() = TODO("Not yet implemented")

        /**
         * The KClass for [Short].
         */
        @JvmStatic
        actual val shortKlass: KClass<Short>
            get() = TODO("Not yet implemented")

        /**
         * The KClass for [Int].
         */
        @JvmStatic
        actual val intKlass: KClass<Int>
            get() = Int::class

        /**
         * The KClass for [Int64].
         */
        @JvmStatic
        actual val int64Klass: KClass<Int64>
            get() = TODO("Not yet implemented")

        /**
         * The KClass for [Double].
         */
        @JvmStatic
        actual val doubleKlass: KClass<Double>
            get() = TODO("Not yet implemented")

        /**
         * The KClass for [String].
         */
        @JvmStatic
        actual val stringKlass: KClass<String>
            get() = String::class

        /**
         * The KClass for [PlatformObject].
         */
        @JvmStatic
        actual val objectKlass: KClass<PlatformObject>
            get() = TODO("Not yet implemented")

        /**
         * The KClass for [PlatformList].
         */
        @JvmStatic
        actual val listKlass: KClass<PlatformList>
            get() = TODO("Not yet implemented")

        /**
         * The KClass for [PlatformMap].
         */
        @JvmStatic
        actual val mapKlass: KClass<PlatformMap>
            get() = TODO("Not yet implemented")

        /**
         * The KClass for [PlatformDataViewApi].
         */
        @JvmStatic
        actual val dataViewKlass: KClass<PlatformDataView>
            get() = TODO("Not yet implemented")

        /**
         * Tests if the given value is _null_ or _undefined_.
         * @param any The value to test.
         * @return _true_ if the value is _null_ or _undefined_; false otherwise.
         */
        @JvmStatic
        actual fun isNil(any: Any?): Boolean = any == null

        /**
         * Creates an undefined value for the given type or returns the cached one.
         * @param klass The type for which to create an undefined value.
         * @return The undefined value.
         */
        @JvmStatic
        actual fun <T : Any> undefinedOf(klass: KClass<T>): T {
            TODO("Not yet implemented")
        }

        /**
         * Create a proxy or return the existing proxy. If a proxy of a not compatible type exists already and [doNotOverride]
         * is _true_, the method will throw an _IllegalStateException_; otherwise the current type is simply overridden.
         * @param pobject The object at which to query for the proxy.
         * @param klass The proxy class.
         * @param doNotOverride If _true_, do not override existing symbols bound to incompatible types, but throw an [IllegalStateException]
         * @return The proxy instance.
         * @throws IllegalStateException If [doNotOverride] is _true_ and the symbol is already bound to an incompatible type.
         */
        @JvmStatic
        actual fun <T : Proxy> proxy(pobject: PlatformObject, klass: KClass<T>, doNotOverride: Boolean): T {
            require(pobject is JvmObject)
            return pobject.proxy(klass, doNotOverride)
        }

        @JvmStatic
        private val defaultBaseLogger = object : BaseLogger {
            private val logger = LoggerFactory.getLogger("com.here.naksha.lib.base")
            override fun debug(msg: String, vararg args: Any?) {
                if (logger.isDebugEnabled) logger.debug(msg, *args)
            }

            override fun atDebug(msgFn: () -> String?) {
                if (logger.isDebugEnabled) {
                    val msg = msgFn.invoke()
                    if (msg != null) logger.debug(msg)
                }
            }

            override fun info(msg: String, vararg args: Any?) {
                if (logger.isInfoEnabled) logger.info(msg, *args)
            }

            override fun atInfo(msgFn: () -> String?) {
                if (logger.isInfoEnabled) {
                    val msg = msgFn.invoke()
                    if (msg != null) logger.info(msg)
                }
            }

            override fun warn(msg: String, vararg args: Any?) {
                if (logger.isWarnEnabled) logger.warn(msg, *args)
            }

            override fun atWarn(msgFn: () -> String?) {
                if (logger.isWarnEnabled) {
                    val msg = msgFn.invoke()
                    if (msg != null) logger.warn(msg)
                }
            }

            override fun error(msg: String, vararg args: Any?) {
                if (logger.isErrorEnabled) logger.error(msg, *args)
            }

            override fun atError(msgFn: () -> String?) {
                if (logger.isErrorEnabled) {
                    val msg = msgFn.invoke()
                    if (msg != null) logger.error(msg)
                }
            }
        }

        /**
         * The [BaseLogger].
         */
        @JvmStatic
        actual val logger: BaseLogger by newThreadLocal { defaultBaseLogger }

        /**
         * Creates a new thread-local. Should be stored only in a static immutable variable (`val`).
         * @param initializer An optional lambda to be invoked, when the thread-local is read for the first time.
         * @return The thread local.
         */
        @JvmStatic
        actual fun <T> newThreadLocal(initializer: (() -> T)?): BaseThreadLocal<T> = JvmThreadLocal(initializer)

        /**
         * The nano-time when the class is initialized.
         */
        @JvmStatic
        private val startNanos = System.nanoTime()

        /**
         * The epoch microseconds when the class is initialized.
         */
        @JvmStatic
        private val epochMicros = (System.currentTimeMillis() * 1000) + ((startNanos / 1000) % 1000)

        /**
         * The epoch nanoseconds when the class is initialized.
         */
        @JvmStatic
        private val epochNanos = (System.currentTimeMillis() * 1_000_000) + (startNanos % 1_000_000)

        /**
         * Returns the current epoch milliseconds.
         * @return The current epoch milliseconds.
         */
        @JvmStatic
        actual fun currentMillis(): Int64 = JvmInt64(System.currentTimeMillis())

        /**
         * Returns the current epoch microseconds.
         * @return current epoch microseconds.
         */
        @JvmStatic
        actual fun currentMicros(): Int64 = JvmInt64(epochMicros + ((System.nanoTime() - startNanos) / 1000))

        /**
         * Returns the current epoch nanoseconds.
         * @return current epoch nanoseconds.
         */
        actual fun currentNanos(): Int64 = JvmInt64(epochNanos + (System.nanoTime() - startNanos))

        /**
         * Generates a new random number between 0 and 1 (therefore with 53-bit random bits).
         * @return The new random number between 0 and 1.
         */
        @JvmStatic
        actual fun random(): Double = ThreadLocalRandom.current().nextDouble()

        /**
         * Tests if the given 64-bit floating point number can be converted into a 32-bit floating point number without losing information.
         * @param value The 64-bit floating point number.
         * @return _true_ if the given 64-bit float can be converted into a 32-bit one without losing information; _false_ otherwise.
         */
        @JvmStatic
        actual fun canBeFloat32(value: Double): Boolean {
            // IEEE-754, 32-bit = One sign-bit, 8-bit exponent biased by 127, then 23-bit mantissa
            // IEEE-754, 64-bit = One sign-bit, 11-bit exponent biased by 1023, then 52-bit mantissa
            // E = 0 means denormalized number (M>0) or null (M=0)
            // E = 255|2047 means either endless (M=0) or not a number (M>0)
            val binary = value.toRawBits()
            var exponent = (binary ushr 52).toInt() and 0x7ff
            if (exponent == 0 || exponent == 2047) return false
            // Remove bias: -1023 (0) .. 1024 (2047)
            exponent -= 1023
            // 32-bit exponent is 8-bit with bias 127: -127 (0) .. 128 (255)
            // We want to avoid extremes as they encode special states.
            if (exponent < -126 || exponent > 127) return false
            // We do not want to lose precision in mantissa either.
            // Either the lower 29-bit of mantissa are zero (only 23-bit used) or all bits are set.
            val mantissa = binary and 0x000f_ffff_ffff_ffff
            return (mantissa and 0x0000_0000_1fff_ffff) == 0L || mantissa == 0x000f_ffff_ffff_ffff
        }

        private const val MIN_INT_VALUE_AS_DOUBLE = Int.MIN_VALUE.toDouble()
        private const val MAX_INT_VALUE_AS_DOUBLE = Int.MAX_VALUE.toDouble()

        /**
         * Tests if the given 64-bit floating point number can be converted into a 32-bit integer without losing information.
         * @param value The 64-bit floating point number.
         * @return _true_ if the given 64-bit float can be converted into a 32-bit integer without losing information; _false_ otherwise.
         */
        @JvmStatic
        actual fun canBeInt32(value: Double): Boolean {
            val rounded = round(value)
            return rounded == value && (rounded in MIN_INT_VALUE_AS_DOUBLE..MAX_INT_VALUE_AS_DOUBLE)
        }

        /**
         * Clip the end, so that the end is already greater/equal [offset].
         * @param bytes The byte-array to clip to.
         * @param offset The first byte that should be used.
         * @param size The size.
         * @return The end-offset (the first byte not to use), greater or equal to offset and not larger than [ByteArray.size].
         */
        @JvmStatic
        private fun endOf(bytes: ByteArray, offset: Int, size: Int): Int {
            if (offset < 0) throw IllegalArgumentException("offset must not be less than zero")
            if (offset >= bytes.size) throw IllegalArgumentException("offset must be within the given byte-array")
            if (size < 0) throw IllegalArgumentException("size must not be less than zero")
            val end = offset + size
            return if (end > bytes.size) bytes.size else end
        }

        @JvmStatic
        private val lz4Factory: LZ4Factory = LZ4Factory.fastestInstance()

        /**
         * Compress bytes.
         * @param raw The bytes to compress.
         * @param offset The offset of the first byte to compress.
         * @param size The amount of bytes to compress.
         * @return The deflated (compressed) bytes.
         */
        @JvmStatic
        actual fun lz4Deflate(raw: ByteArray, offset: Int, size: Int): ByteArray {
            val end = endOf(raw, offset, size)
            val compressor = lz4Factory.fastCompressor()
            val maxCompressedLength = compressor.maxCompressedLength(end - offset)
            val compressed = ByteArray(maxCompressedLength)
            val compressedLength = compressor.compress(raw, offset, end - offset, compressed, 0, maxCompressedLength)
            return compressed.copyOf(compressedLength)
        }

        /**
         * Decompress bytes.
         * @param compressed The bytes to decompress.
         * @param bufferSize The amount of bytes that are decompressed, if unknown, set 0.
         * @param offset The offset of the first byte to decompress.
         * @param size The amount of bytes to decompress.
         * @return The inflated (decompress) bytes.
         */
        @JvmStatic
        actual fun lz4Inflate(
            compressed: ByteArray,
            bufferSize: Int,
            offset: Int,
            size: Int
        ): ByteArray {
            val end = endOf(compressed, offset, size)
            val decompressor = lz4Factory.fastDecompressor()
            val restored = if (bufferSize <= 0) ByteArray((end - offset) * 10) else ByteArray(bufferSize)
            val decompressedLength = decompressor.decompress(compressed, offset, restored, 0, restored.size)
            if (decompressedLength < restored.size) {
                return restored.copyOf(decompressedLength)
            }
            return restored
        }

        /**
         * Compress bytes.
         * @param raw The bytes to compress.
         * @param offset The offset of the first byte to compress.
         * @param size The amount of bytes to compress.
         * @return The deflated (compressed) bytes.
         */
        @JvmStatic
        actual fun gzipDeflate(raw: ByteArray, offset: Int, size: Int): ByteArray {
            TODO("Not yet implemented")
        }

        /**
         * Decompress bytes.
         * @param compressed The bytes to decompress.
         * @param bufferSize The amount of bytes that are decompressed, if unknown, set 0.
         * @param offset The offset of the first byte to decompress.
         * @param size The amount of bytes to decompress.
         * @return The inflated (decompress) bytes.
         */
        @JvmStatic
        actual fun gzipInflate(
            compressed: ByteArray,
            bufferSize: Int,
            offset: Int,
            size: Int
        ): ByteArray {
            TODO("Not yet implemented")
        }
    }
}