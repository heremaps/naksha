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
import com.fasterxml.jackson.module.kotlin.kotlinModule
import net.jpountz.lz4.LZ4Factory
import sun.misc.Unsafe
import java.security.MessageDigest
import java.text.Normalizer
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
    actual companion object PlatformCompanion {

        @JvmField
        internal val module = SimpleModule().apply {
            addAbstractTypeMapping(Map::class.java, JvmMap::class.java)
            addAbstractTypeMapping(MutableMap::class.java, JvmMap::class.java)
            addAbstractTypeMapping(List::class.java, JvmList::class.java)
            addAbstractTypeMapping(MutableList::class.java, JvmList::class.java)
            // TODO: Fix me!
            //addSerializer(CustomSerializer)
            setDeserializerModifier(object : BeanDeserializerModifier() {
                override fun modifyDeserializer(
                    config: DeserializationConfig,
                    beanDesc: BeanDescription,
                    deserializer: JsonDeserializer<*>
                ): JsonDeserializer<*> {
                    return if (beanDesc.beanClass == Number::class.java || beanDesc.beanClass == String::class.java) {
                        CustomDeserializer
                    } else {
                        // If no special deserialization is required, delegate to the default deserializer
                        deserializer
                    }
                }
            })
        }
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

        @JvmField
        internal val objectMapper: ThreadLocal<ObjectMapper> = ThreadLocal.withInitial {
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
        @JvmField
        internal val int64Cache = Array(2048) { JvmInt64(((it shl 21) shr 21).toLong()) }

        /**
         * A simple hash cache, must have a size of 2^n.
         */
        @JvmStatic
        private val int64ValueCache = arrayOfNulls<JvmInt64>(1 shl 8) // 256 * 8 = 1kb

        /**
         * The cache for all declared symbols.
         */
        @JvmField
        internal val symbolsCache = ConcurrentHashMap<String, Symbol>()

        /**
         * The platform specific value of undefined.
         */
        @JvmField
        actual val UNDEFINED: Any = Object()

        /**
         * The symbol (_com.here.naksha.lib.nak_) to store the default Naksha multi-platform types in.
         */
        @JvmField
        actual val DEFAULT_SYMBOL: Symbol = Symbols.forName("com.here.naksha.lib.nak")

        /**
         * The maximum value of a 64-bit integer.
         * @return The maximum value of a 64-bit integer.
         */
        @JvmField
        actual val INT64_MAX_VALUE: Int64 = JvmInt64(Long.MAX_VALUE)

        /**
         * The minimum value of a 64-bit integer.
         * @return The minimum value of a 64-bit integer.
         */
        @JvmField
        actual val INT64_MIN_VALUE: Int64 = JvmInt64(Long.MIN_VALUE)

        /**
         * The minimum integer that can safely stored in a double.
         * @return The minimum integer that can safely stored in a double.
         */
        @JvmField
        actual val MAX_SAFE_INT: Double = 9007199254740991.0

        /**
         * The maximum integer that can safely stored in a double.
         * @return The maximum integer that can safely stored in a double.
         */
        @JvmField
        actual val MIN_SAFE_INT: Double = -9007199254740991.0

        /**
         * The difference between 1 and the smallest floating point number greater than 1.
         */
        @JvmField
        actual val EPSILON: Double = Math.ulp(1.0)

        /**
         * The reference to TheUnsafe class.
         */
        @JvmField
        val unsafe: Unsafe

        /**
         * The base-offset in a byte-array.
         */
        @JvmField
        val baseOffset: Int

        @JvmField
        internal val initialized = AtomicBoolean(false)

        private val nonArgsConstuctorsCache: AtomicMap<KClass<out Proxy>, KFunction<out Proxy>>

        init {
            val unsafeConstructor = Unsafe::class.java.getDeclaredConstructor()
            unsafeConstructor.isAccessible = true
            unsafe = unsafeConstructor.newInstance()
            val someByteArray = ByteArray(8)
            baseOffset = unsafe.arrayBaseOffset(someByteArray.javaClass)
            nonArgsConstuctorsCache = AtomicMap()
        }

        @JvmStatic
        actual fun initialize(): Boolean {
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
        actual fun isAssignable(source: KClass<*>, target: KClass<*>): Boolean = source.java.isAssignableFrom(target.java)

        @JvmStatic
        actual fun isProxyKlass(klass: KClass<*>): Boolean = Proxy::class.isSuperclassOf(klass)

        @JvmStatic
        actual fun <T : Any> klassFor(constructor: KFunction<T>): KClass<out T> {
            TODO("Implement me!")
        }

        @Suppress("UNCHECKED_CAST")
        @JvmStatic
        actual fun <T : Any> klassOf(o: T): KClass<T> = o::class as KClass<T>

        /**
         * Returns the Kotlin class of the given Java class.
         * @param javaClass The Java class.
         * @return The Kotlin class.
         */
        @JvmStatic
        fun <T : Any> klassOf(javaClass: Class<T>): KClass<T> = javaClass.kotlin

        /**
         * Returns the Java class of the given Kotlin class.
         * @param kotlinKlass The Kotlin class.
         * @return The Java class.
         */
        @JvmStatic
        fun <T : Any> classOf(kotlinKlass: KClass<out T>): Class<out T> = kotlinKlass.java

        @JvmStatic
        actual fun newMap(vararg entries: Any?): PlatformMap = JvmMap(*entries)

        @JvmStatic
        actual fun <K : Any, V : Any> newAtomicMap(): AtomicMap<K, V> = JvmAtomicMap()

        @JvmStatic
        actual fun <R: Any> newAtomicRef(startValue: R?): AtomicRef<R> = JvmAtomicRef(startValue)

        @JvmStatic
        actual fun newAtomicInt(startValue: Int): AtomicInt = JvmAtomicInt(startValue)

        @JvmStatic
        actual fun newList(vararg entries: Any?): PlatformList = JvmList(*entries)

        @JvmStatic
        actual fun newByteArray(size: Int): ByteArray = ByteArray(size)

        @JvmStatic
        actual fun newDataView(byteArray: ByteArray, offset: Int, size: Int): PlatformDataView = JvmDataView(byteArray, offset, size)

        @JvmStatic
        actual fun <T : Any> newWeakRef(referent: T): WeakRef<T> = JvmWeakRef(referent)

        @JvmStatic
        actual fun newLock(): PlatformLock = JvmLock()

        @JvmStatic
        actual fun unbox(value: Any?): Any? {
            if (value is Proxy) return value.platformObject() as? JvmObject
            if (value is Array<*>) return JvmList(*value)
            return value
        }

        /**
         * Returns the [JvmObject] of the given object.
         * @param o Any object.
         * @return The [JvmObject] or _null_.
         */
        @JvmStatic
        fun toJvmObject(o: Any?): JvmObject? = if (o is Proxy) o.platformObject() as? JvmObject else if (o is JvmObject) o else null

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
            // This is a very simple hash-based cache, it is helpful for example for currentMillis.
            val cache = this.int64ValueCache
            val cacheMask = cache.size - 1
            val i = (value.toInt() xor (value ushr 32).toInt()) and cacheMask
            var instance = cache[i]
            if (instance != null && instance.value == value) return instance
            instance = JvmInt64(value)
            cache[i] = instance
            return instance
        }

        @JvmStatic
        actual fun int64ToLong(value: Int64): Long = value.toLong()

        @JvmStatic
        actual fun isNumber(o: Any?): Boolean = o is Number

        @JvmStatic
        actual fun isScalar(o: Any?): Boolean = o == null || o is Number || o is String || o is Boolean

        @JvmStatic
        actual fun isInteger(o: Any?): Boolean = o is Byte || o is Short || o is Int || o is Long || o is JvmInt64

        @JvmStatic
        actual fun isDouble(o: Any?): Boolean = o is Double

        @JvmStatic
        actual fun compare(a: Any?, b: Any?): Int = throw UnsupportedOperationException()

        @JvmStatic
        actual fun hashCodeOf(o: Any?): Int = throw UnsupportedOperationException()

        @JvmStatic
        actual fun <T : Any> newInstanceOf(klass: KClass<out T>): T = klass.primaryConstructor?.call() ?: throw IllegalArgumentException()

        @JvmStatic
        @Suppress("UNCHECKED_CAST")
        actual fun <T : Any> allocateInstance(klass: KClass<out T>): T = unsafe.allocateInstance(klass.java) as T

        @JvmStatic
        actual fun initializeKlass(klass: KClass<*>) {
            unsafe.ensureClassInitialized(klass.java)
        }

        @Suppress("UNCHECKED_CAST")
        @JvmStatic
        actual fun <T> copy(obj: T?, recursive: Boolean): T? {
            if (obj == null) return null
            return when (obj) {
                is Short -> obj
                is Int -> obj
                is Long -> obj
                is Int64 -> obj
                is Float -> obj
                is Double -> obj
                is String -> obj
                is JvmDataView -> newDataView(obj.getByteArray().copyOf()) as T
                is JvmMap -> {
                    val copy = JvmMap()
                    for (entry in obj) {
                        if (recursive) copy.put(entry.key, copy(entry.value, true))
                        else copy.put(entry.key, entry.value)
                    }
                    copy as T
                }
                is JvmList -> {
                    val copy = JvmList()
                    copy.setCapacity(obj.size)
                    for (value in obj) {
                        if (recursive) copy.add(copy(value, true))
                        else copy.add(value)
                    }
                    copy as T
                }
                else -> obj
            }
        }

        @JvmField
        internal val toJsonOptions = ThreadLocal<ToJsonOptions>()

        @JvmStatic
        actual fun toJSON(obj: Any?, options: ToJsonOptions): String {
            toJsonOptions.set(options)
            return objectMapper.get().writeValueAsString(obj)
        }

        @JvmField
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

        /**
         * The iterator member.
         */
        @JvmField
        actual val ITERATOR: Symbol = Symbols.forName("iterator")

        /**
         * The KClass for [Any].
         */
        @JvmField
        actual val anyKlass: KClass<Any> = Any::class

        /**
         * The KClass for [Boolean].
         */
        @JvmField
        actual val booleanKlass: KClass<Boolean> = Boolean::class

        /**
         * The KClass for [Short].
         */
        @JvmField
        actual val shortKlass: KClass<Short> = Short::class

        /**
         * The KClass for [Int].
         */
        @JvmField
        actual val intKlass: KClass<Int> = Int::class

        /**
         * The KClass for [Int64].
         */
        @JvmField
        actual val int64Klass: KClass<Int64> = Int64::class

        /**
         * The KClass for [Double].
         */
        @JvmField
        actual val doubleKlass: KClass<Double> = Double::class

        /**
         * The KClass for [String].
         */
        @JvmField
        actual val stringKlass: KClass<String> = String::class

        /**
         * The KClass for [PlatformObject].
         */
        @JvmField
        actual val objectKlass: KClass<PlatformObject> = PlatformObject::class

        /**
         * The KClass for [PlatformList].
         */
        @JvmField
        actual val listKlass: KClass<PlatformList> = PlatformList::class

        /**
         * The KClass for [PlatformMap].
         */
        @JvmField
        actual val mapKlass: KClass<PlatformMap> = PlatformMap::class

        /**
         * The KClass for [PlatformDataViewApi].
         */
        @JvmField
        actual val dataViewKlass: KClass<PlatformDataView> = PlatformDataView::class

        /**
         * Tests if the given value is _null_ or _undefined_.
         * @param any The value to test.
         * @return _true_ if the value is _null_ or _undefined_; false otherwise.
         */
        @JvmStatic
        actual fun isNil(any: Any?): Boolean = any == null

        /**
         * Create a proxy or return the existing proxy. If a proxy of a not compatible type exists already and [doNotOverride]
         * is _true_, the method will throw an _IllegalStateException_; otherwise the current type is simply overridden.
         * @param pobject The object at which to query for the proxy.
         * @param klass The proxy class.
         * @param doNotOverride If _true_, do not override existing symbols bound to incompatible types, but throw an [IllegalStateException]
         * @return The proxy instance.
         * @throws IllegalStateException If [doNotOverride] is _true_ and the symbol is already bound to an incompatible type.
         */
        @Suppress("UNCHECKED_CAST")
        @JvmStatic
        actual fun <T : Proxy> proxy(pobject: PlatformObject, klass: KClass<T>, doNotOverride: Boolean): T {
            require(pobject is JvmObject)
            val symbol = Symbols.of(klass)
            var proxy = pobject.getSymbol(symbol)
            if (proxy != null) {
                if (klass.isInstance(proxy)) return proxy as T
                if (doNotOverride) throw IllegalStateException("The symbol $symbol is already bound to incompatible type")
            }

            proxy = resolveConstructorFor(klass).call()
            proxy.bind(pobject, symbol)
            return proxy
        }

        private fun <T: Proxy> resolveConstructorFor(klass: KClass<T>): KFunction<T>{
            var constructor = nonArgsConstuctorsCache[klass]
            if(constructor == null){
                constructor = nonArgConstructorFor(klass)
                nonArgsConstuctorsCache[klass] = constructor
            }
            return constructor as KFunction<T>
        }

        /**
         * Returns non-arg constructor for [klass] or throws [IllegalArgumentException] if none is found.
         */
        private fun <T : Proxy> nonArgConstructorFor(klass: KClass<T>): KFunction<T> {
            return klass.constructors.firstOrNull { constructor: KFunction<T> ->
                constructor.parameters.isEmpty()
            } ?: throw IllegalArgumentException("Unable to find non-arg constructor for class: ${klass.qualifiedName}")
        }

        /**
         * The default logger singleton to be used as initial value by the default [loggerThreadLocal]. This is based upon
         * [SLF4j](https://www.slf4j.org/). If an application has a different logger singleton, it can simply place this variable. If the
         * application requires a dedicated thread-local logger instances, it should rather replace the [loggerThreadLocal] with an own
         * version that creates a correct initial thread local logger.
         */
        @JvmField
        var loggerDefault = JvmLogger()

        /**
         * The thread local platform logger. Applications can replace this, if they want an own implementation that is different per
         * thread (e.g. requires thread local string builders or alike). By default, the thread local is initialized with [loggerDefault].
         */
        @JvmField
        var loggerThreadLocal: JvmThreadLocal<PlatformLogger> = JvmThreadLocal { loggerDefault }

        /**
         * The [PlatformLogger], in Java redirected to [loggerThreadLocal].
         */
        @JvmStatic
        actual val logger: PlatformLogger by loggerThreadLocal

        /**
         * Creates a new thread-local. Should be stored only in a static immutable variable (`val`).
         * @param initializer An optional lambda to be invoked, when the thread-local is read for the first time.
         * @return The thread local.
         */
        @JvmStatic
        actual fun <T> newThreadLocal(initializer: (() -> T)?): PlatformThreadLocal<T> = JvmThreadLocal(initializer)

        /**
         * The nano-time when the class is initialized.
         */
        @JvmField
        internal val startNanos = System.nanoTime()

        /**
         * The epoch microseconds when the class is initialized.
         */
        @JvmField
        internal val epochMicros = (System.currentTimeMillis() * 1000) + ((startNanos / 1000) % 1000)

        /**
         * The epoch nanoseconds when the class is initialized.
         */
        @JvmField
        internal val epochNanos = (System.currentTimeMillis() * 1_000_000) + (startNanos % 1_000_000)

        /**
         * Returns the current epoch milliseconds.
         * @return The current epoch milliseconds.
         */
        @JvmStatic
        actual fun currentMillis(): Int64 = longToInt64(System.currentTimeMillis())

        /**
         * Returns the current epoch microseconds.
         * @return current epoch microseconds.
         */
        @JvmStatic
        actual fun currentMicros(): Int64 = longToInt64(epochMicros + ((System.nanoTime() - startNanos) / 1000))

        /**
         * Returns the current epoch nanoseconds.
         * @return current epoch nanoseconds.
         */
        actual fun currentNanos(): Int64 = longToInt64(epochNanos + (System.nanoTime() - startNanos))

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
            val mantissa = binary and 0x000f_ffff_ffff_ffffL
            return (mantissa and 0x0000_0000_1fff_ffffL) == 0L || mantissa == 0x000f_ffff_ffff_ffffL
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
        actual fun isPlv8(): Boolean = false

        @JvmStatic
        private val md5Digest = ThreadLocal.withInitial { MessageDigest.getInstance("MD5") }

        @JvmStatic
        actual fun md5(text: String): ByteArray {
            val digest = md5Digest.get()
            digest.reset()
            digest.update(text.toByteArray(Charsets.UTF_8))
            return digest.digest()
        }

        @JvmField
        internal val lz4Factory: LZ4Factory = LZ4Factory.fastestInstance()

        /**
         * Compress bytes.
         * @param raw The bytes to compress.
         * @return The deflated (compressed) bytes.
         */
        @JvmStatic
        actual fun lz4Deflate(raw: ByteArray): ByteArray {
            val compressor = lz4Factory.fastCompressor()
            val maxCompressedLength = compressor.maxCompressedLength(raw.size)
            val compressed = ByteArray(maxCompressedLength)
            val compressedLength = compressor.compress(raw, 0, raw.size, compressed, 0, maxCompressedLength)
            return compressed.copyOf(compressedLength)
        }

        /**
         * Decompress bytes.
         * @param compressed The bytes to decompress.
         * @return The inflated (decompress) bytes.
         */
        @JvmStatic
        actual fun lz4Inflate(compressed: ByteArray): ByteArray {
            // TODO: Simple multiplication of the compressed by 12 is not optimal!
            val decompressor = lz4Factory.fastDecompressor()
            val restored = ByteArray(compressed.size * 12)
            val decompressedLength = decompressor.decompress(compressed, 0, restored, 0, restored.size)
            if (decompressedLength < restored.size) {
                return restored.copyOf(decompressedLength)
            }
            return restored
        }

        /**
         * Compress bytes.
         * @param raw The bytes to compress.
         * @return The deflated (compressed) bytes.
         */
        @JvmStatic
        actual fun gzipDeflate(raw: ByteArray): ByteArray = GZip.gzip(raw)

        /**
         * Decompress bytes.
         * @param compressed The bytes to decompress.
         * @return The inflated (decompress) bytes.
         */
        @JvmStatic
        actual fun gzipInflate(compressed: ByteArray): ByteArray = GZip.gunzip(compressed)

        actual fun stackTrace(t: Throwable): String = t.stackTraceToString()

        @JvmStatic
        actual fun normalize(value: String, form: NormalizerForm): String = Normalizer.normalize(value, Normalizer.Form.valueOf(form.name))

        init {
            initialize()
        }
    }
}