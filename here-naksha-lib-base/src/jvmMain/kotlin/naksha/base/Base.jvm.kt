package naksha.base

import com.fasterxml.jackson.annotation.JsonAutoDetect
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
import com.fasterxml.jackson.databind.type.ArrayType
import naksha.base.fn.Fn0
import net.jpountz.lz4.LZ4Factory
import sun.misc.Unsafe
import java.lang.Class.*
import java.lang.invoke.MethodHandles
import java.lang.reflect.Method
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.text.Normalizer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max
import kotlin.math.round
import kotlin.reflect.KClass

actual class Base {
    actual companion object BaseCompanion {
        /**
         * Switch to `true` to enable the new JSON support.
         */
        @JvmField
        internal val USE_NEW_JSON: AtomicBool = JvmAtomicBool(false)

        fun useNewJson(): Boolean = USE_NEW_JSON.get()

        fun enableNewJsonParser() {
            if (USE_NEW_JSON.compareAndSet(expect=false, update=true)) {
                naksha.base.JsonParser.threadLocalClass.set(JvmParser::class.java)
            }
        }

        fun disableNewJsonParser() {
            if (USE_NEW_JSON.compareAndSet(expect=true, update=false)) {
                naksha.base.JsonParser.threadLocalClass.set(naksha.base.JsonParser::class.java)
            }
        }

        @JvmField
        actual val interfaceToImplementation = AtomicMap<Any, Any>()

        @JvmField
        internal val module = SimpleModule().apply {
            addAbstractTypeMapping(Map::class.java, JvmMap::class.java)
            addAbstractTypeMapping(MutableMap::class.java, JvmMap::class.java)
            addAbstractTypeMapping(List::class.java, JvmList::class.java)
            addAbstractTypeMapping(MutableList::class.java, JvmList::class.java)
//
// We can expect that all our deserialization normally happens here
//
//            addDeserializer(Any::class.java, object : JsonDeserializer<Any?>() {
//                override fun deserialize(parser: JsonParser, context: DeserializationContext): Any? {
//                    return null
//                }
//            });
            // Custom Serializer for specific values.
            // `beanClass` is what should be serialized, the returned deserializer is than called
            //             with exactly value to turn into the `beanClass`.
            setSerializerModifier(object : BeanSerializerModifier() {
                override fun modifySerializer(
                    config: SerializationConfig,
                    beanDesc: BeanDescription,
                    serializer: JsonSerializer<*>?
                ): JsonSerializer<*>? {
                    // TODO: We need specialized serializers for Id and JsEnum!
                    val javaType = beanDesc.beanClass
                    return if (javaType == Id::class.java || BaseEnum::class.java.isAssignableFrom(javaClass)) {
                        CustomSerializer
                    } else {
                        serializer
                    }
                }

                override fun modifyArraySerializer(
                    config: SerializationConfig,
                    valueType: ArrayType,
                    beanDesc: BeanDescription,
                    serializer: JsonSerializer<*>?
                ): JsonSerializer<*>? {
                    val javaType = valueType.contentType
                    if (javaType.rawClass == Byte::class.javaPrimitiveType) {
                        // TODO: We need specialized serializer for ByteArrat!
                        // TODO: Add code for ByteArray
                    }
                    return super.modifyArraySerializer(config, valueType, beanDesc, serializer)
                }
            })
            // Custom Deserializer for specific values.
            // `beanClass` is what should be deserialized, the returned deserializer is than called
            //             with an arbitrary value to turn into the `beanClass`.
            setDeserializerModifier(object : BeanDeserializerModifier() {
                override fun modifyDeserializer(
                    config: DeserializationConfig,
                    beanDesc: BeanDescription,
                    deserializer: JsonDeserializer<*>
                ): JsonDeserializer<*> {
                    return if (Number::class.java.isAssignableFrom(beanDesc.beanClass)
                            || String::class.java == beanDesc.beanClass) {
                        CustomDeserializer
                    } else {
                        // If no special deserialization is required, delegate to the default deserializer
                        deserializer
                    }
                }

                override fun modifyArrayDeserializer(
                    config: DeserializationConfig?,
                    valueType: ArrayType?,
                    beanDesc: BeanDescription?,
                    deserializer: JsonDeserializer<*>?
                ): JsonDeserializer<*>? {
                    return super.modifyArrayDeserializer(config, valueType, beanDesc, deserializer)
                }
            })
        }

        @JvmField
        internal val objectMapper: ThreadLocal<ObjectMapper> = ThreadLocal.withInitial {
            val jsonFactory = JsonFactoryBuilder()
                //.configure(JsonFactory.Feature.INTERN_FIELD_NAMES, false)
                //.configure(JsonFactory.Feature.CANONICALIZE_FIELD_NAMES, false)
                .configure(JsonFactory.Feature.USE_THREAD_LOCAL_FOR_BUFFER_RECYCLING, true)
                .build()
            jsonFactory.configure(JsonGenerator.Feature.AUTO_CLOSE_TARGET, false)
            jsonFactory.configure(JsonParser.Feature.AUTO_CLOSE_SOURCE, false)
            JsonMapper.builder(jsonFactory)
                //.enable(MapperFeature.DEFAULT_VIEW_INCLUSION)
                //.enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
                //.enable(MapperFeature.SORT_CREATOR_PROPERTIES_FIRST)
                //.serializationInclusion(JsonInclude.Include.NON_NULL)
                .visibility(PropertyAccessor.SETTER, JsonAutoDetect.Visibility.NONE)
                .visibility(PropertyAccessor.GETTER, JsonAutoDetect.Visibility.NONE)
                .visibility(PropertyAccessor.IS_GETTER, JsonAutoDetect.Visibility.NONE)
                .visibility(PropertyAccessor.CREATOR, JsonAutoDetect.Visibility.NONE)
                .configure(SerializationFeature.CLOSE_CLOSEABLE, false)
                //.addModule(kotlinModule())
                .addModule(module)
                .build()
        }

        @JvmField
        actual val UNDEFINED: Any = Any()

        @JvmField
        actual val INVALIDATED: Any = Any()

        @JvmField
        actual val MAX_SAFE_INT_AS_DOUBLE: Double = 9007199254740991.0

        @JvmField
        actual val MAX_SAFE_INT_AS_LONG: Long = 9007199254740991L

        @JvmStatic
        internal val MAX_SAFE_INT_AS_ULONG: ULong = 9007199254740991UL

        @JvmField
        actual val MIN_SAFE_INT_AS_DOUBLE: Double = -9007199254740991.0

        @JvmField
        actual val MIN_SAFE_INT_AS_LONG: Long = -9007199254740991

        @JvmField
        actual val EPSILON: Double = Math.ulp(1.0)

        @JvmStatic
        actual fun initialize(): Boolean {
            if (initialized.compareAndSet(false, true)) {
                // TODO: Do we need to do anything?
                return true
            }
            return false
        }

        @JvmStatic
        actual fun isAssignable(source: KClass<*>, target: KClass<*>): Boolean = source.java.isAssignableFrom(target.java)

        /**
         * Tests if the [target] class or interface is either the same as, or is a superclass or superinterface of, the class or interface represented by the specified [source] parameter.
         *
         * For example `isAssignable(CharSequence, String)` will be _false_ (not every [CharSequence] is a [String]), while `isAssignable(String, CharSequence)` will be _true_ (every [String] is a [CharSequence]).
         *
         * In other words, this method tests if the [source] type can be cast down to the [target] type, so if
         * **`source as target`** is possible.
         *
         * **Warning**: An assignment is not the same as an instanceof test. For example for interfaces the example can be tricky,
         * because formally the cast from a [CharSequence] to a [String] is not an assignable form, but technically can still
         * succeed, if the object being tried to cast down is actually a string, just the compiler type is formally [CharSequence].
         * Formally this kind of cast is an assignment from [String] to [String] not being known at compile time.
         *
         * @param source The type that should be cast.
         * @param target The target type to which to cast.
         * @return _true_ if `source as target` will always succeed; so, the [source] type can be cast to the [target] type in all cases; _false_ otherwise.
         */
        @JvmStatic
        fun isAssignable(source: Class<*>, target: Class<*>): Boolean = source.isAssignableFrom(target)

        @JvmStatic
        actual fun <T : Any> klassForName(name: String): KClass<T> {
            try {
                @Suppress("UNCHECKED_CAST")
                return (forName(name, true, this::class.java.classLoader).kotlin) as KClass<T>
            } catch (e: Exception) {
                throw IllegalArgumentException("Class '$name' not found", e)
            }
        }

        @Suppress("UNCHECKED_CAST")
        @JvmStatic
        actual fun <T : Any> klassOf(o: T): KClass<T> {
            if (o is KClass<*>) return o as KClass<T>
            if (o is Class<*>) return o.kotlin as KClass<T>
            return o::class as KClass<T>
        }

        /**
         * Returns the Java class of the given Kotlin class.
         * @param kotlinClass The Kotlin class.
         * @return The Java class.
         * @since 3.0
         * @see toKClass
         */
        @JvmStatic
        fun <T : Any> toClass(kotlinClass: KClass<out T>): Class<out T> = kotlinClass.java

        /**
         * Returns the Kotlin class of the given Java class.
         * @param javaClass The Java class.
         * @return The Kotlin class.
         * @see toClass
         */
        @JvmStatic
        fun <T : Any> toKClass(javaClass: Class<out T>): KClass<out T> = javaClass.kotlin

        @JvmStatic
        actual fun doubleRawBits(i: Long): Double = java.lang.Double.longBitsToDouble(i)

        @JvmStatic
        actual fun longRawBits(d: Double): Long = java.lang.Double.doubleToRawLongBits(d)

        @JvmStatic
        actual fun isNumber(o: Any?): Boolean = o is Number

        @JvmStatic
        actual fun isScalar(o: Any?): Boolean = when (o) {
            is Number,
            is String,
            is Boolean,
            is BaseBool,
            is Literal,
            is BaseEnum,
            null -> true
            else -> false
        }

        @JvmStatic
        actual fun isInteger(o: Any?): Boolean = when(o) {
            is Byte,
            is Short,
            is Int,
            is Long -> true
            else -> false
        }

        @JvmStatic
        actual fun isFloat(o: Any?): Boolean = o is Double || o is Float

        @JvmStatic
        actual fun hashCodeOf(o: Any?): Int = o.hashCode()

        private val constructorCache = ConcurrentHashMap<Any, Any>()

        @JvmStatic
        actual fun <T : Any> newInstance(klass: KClass<out T>, vararg args: Any): T {
            try {
                return when (args.size) {
                    0 -> klass.java.getDeclaredConstructor().newInstance() as T
                    1 -> klass.java.getDeclaredConstructor(args[0].javaClass).newInstance(*args) as T
                    2 -> klass.java.getDeclaredConstructor(args[0].javaClass, args[1].javaClass).newInstance(*args) as T
                    3 -> klass.java.getDeclaredConstructor(args[0].javaClass, args[1].javaClass, args[2].javaClass).newInstance(*args) as T
                    4 -> klass.java.getDeclaredConstructor(args[0].javaClass, args[1].javaClass, args[2].javaClass, args[3].javaClass).newInstance(*args) as T
                    5 -> klass.java.getDeclaredConstructor(args[0].javaClass, args[1].javaClass, args[2].javaClass, args[3].javaClass, args[4].javaClass).newInstance(*args) as T
                    6 -> klass.java.getDeclaredConstructor(args[0].javaClass, args[1].javaClass, args[2].javaClass, args[3].javaClass, args[4].javaClass, args[5].javaClass).newInstance(*args) as T
                    7 -> klass.java.getDeclaredConstructor(args[0].javaClass, args[1].javaClass, args[2].javaClass, args[3].javaClass, args[4].javaClass, args[5].javaClass, args[6].javaClass).newInstance(*args) as T
                    8 -> klass.java.getDeclaredConstructor(args[0].javaClass, args[1].javaClass, args[2].javaClass, args[3].javaClass, args[4].javaClass, args[5].javaClass, args[6].javaClass, args[7].javaClass).newInstance(*args) as T
                    9 -> klass.java.getDeclaredConstructor(args[0].javaClass, args[1].javaClass, args[2].javaClass, args[3].javaClass, args[4].javaClass, args[5].javaClass, args[6].javaClass, args[7].javaClass, args[8].javaClass).newInstance(*args) as T
                    else -> {
                        val arg_types = Array(args.size) { args[it].javaClass }
                        klass.java.getDeclaredConstructor(*arg_types).newInstance(*args)
                    }
                }
            } catch (e: Exception) {
                val name = klass.java.canonicalName ?: klass.java.name
                throw illegalArg("Can't instantiate '$name', no constructor for given arguments found", e)
            }
        }

        @JvmStatic
        fun <T : Any> newInstance(klass: Class<out T>, vararg args: Any): T {
            try {
                return when (args.size) {
                    0 -> klass.getDeclaredConstructor().newInstance() as T
                    1 -> klass.getDeclaredConstructor(args[0].javaClass).newInstance(*args) as T
                    2 -> klass.getDeclaredConstructor(args[0].javaClass, args[1].javaClass).newInstance(*args) as T
                    3 -> klass.getDeclaredConstructor(args[0].javaClass, args[1].javaClass, args[2].javaClass).newInstance(*args) as T
                    4 -> klass.getDeclaredConstructor(args[0].javaClass, args[1].javaClass, args[2].javaClass, args[3].javaClass).newInstance(*args) as T
                    5 -> klass.getDeclaredConstructor(args[0].javaClass, args[1].javaClass, args[2].javaClass, args[3].javaClass, args[4].javaClass).newInstance(*args) as T
                    6 -> klass.getDeclaredConstructor(args[0].javaClass, args[1].javaClass, args[2].javaClass, args[3].javaClass, args[4].javaClass, args[5].javaClass).newInstance(*args) as T
                    7 -> klass.getDeclaredConstructor(args[0].javaClass, args[1].javaClass, args[2].javaClass, args[3].javaClass, args[4].javaClass, args[5].javaClass, args[6].javaClass).newInstance(*args) as T
                    8 -> klass.getDeclaredConstructor(args[0].javaClass, args[1].javaClass, args[2].javaClass, args[3].javaClass, args[4].javaClass, args[5].javaClass, args[6].javaClass, args[7].javaClass).newInstance(*args) as T
                    9 -> klass.getDeclaredConstructor(args[0].javaClass, args[1].javaClass, args[2].javaClass, args[3].javaClass, args[4].javaClass, args[5].javaClass, args[6].javaClass, args[7].javaClass, args[8].javaClass).newInstance(*args) as T
                    else -> {
                        val arg_types = Array(args.size) { args[it].javaClass }
                        klass.getDeclaredConstructor(*arg_types).newInstance(*args)
                    }
                }
            } catch (e: Exception) {
                val name = klass.canonicalName ?: klass.name
                throw illegalArg("Can't instantiate '$name', no constructor for given arguments found", e)
            }
        }

        @JvmStatic
        @Suppress("UNCHECKED_CAST")
        actual fun <T : Any> allocateInstance(klass: KClass<out T>): T = unsafe.allocateInstance(klass.java) as T

        /**
         * Creates a new instance of the given type, bypassing the constructor, so it returns the uninitialized instance.
         * @param klass The type of which to create a new instance.
         * @return The new instance.
         */
        @JvmStatic
        @Suppress("UNCHECKED_CAST")
        fun <T : Any> allocateInstance(klass: Class<out T>): T = unsafe.allocateInstance(klass) as T

        /**
         * Forces the class loader to initialize the given Kotlin class.
         * @param klass The type to initialize.
         */
        @JvmStatic
        actual fun initializeKClass(klass: KClass<*>) = initializeClass(klass.java)

        @JvmStatic
        fun initializeClass(klass: Class<*>) {
            // This code is required, because in Java 23 they removed unsafe.ensureClassInitialized, but
            // the replacement method does not exist before Java 15, this is such a nonsense!
            val lookupInstance = this.lookupInstance
            val ensureInitialized = this.ensureInitialized
            if (ensureInitialized != null && lookupInstance != null) {
                ensureInitialized.invoke(lookupInstance, klass)
                // == MethodHandles.lookup().ensureInitialized(klass.java);
            } else {
                val ensureClassInitialized = this.ensureClassInitialized
                require(ensureClassInitialized != null) { "Failed to use unsafe.ensureClassInitialized" }
                ensureClassInitialized.invoke(unsafe, klass)
                // == unsafe.ensureClassInitialized(klass.java)
            }
        }

        @Suppress("UNCHECKED_CAST")
        @JvmStatic
        actual fun <T> copy(obj: T, recursive: Boolean): T {
            return when (obj) {
                is Byte -> obj
                is Short -> obj
                is Char -> obj
                is Int -> obj
                is Long -> obj
                is Float -> obj
                is Double -> obj
                is String -> obj
                is ByteArray -> obj.copyOf() as T
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

        private fun unbox(obj: Any?): Any? = when (obj) {
            is AbstractProxy -> obj.baseObject
            is Literal -> obj.string
            is BaseEnum -> obj.string
            is BaseInt -> obj.toInt()
            is BaseLong -> obj.toLong()
            is BaseDouble -> obj.toDouble()
            is BaseRef<*> -> obj.get()
            is BaseRefNotNull<*> -> obj.get()
            is BaseWeakRef<*> -> obj.get()
            is Guid -> obj.toString()
            else -> obj
        }

        @JvmOverloads
        @JvmStatic
        actual fun toJsonString(obj: Any?, longToDataUrl: Boolean): String {
            val raw = unbox(obj) ?: return "null"
            val mapper: ObjectMapper = objectMapper.get()
            return mapper.writeValueAsString(raw)
        }

        @JvmOverloads
        @JvmStatic
        actual fun toJsonBytes(obj: Any?, longToDataUrl: Boolean): ByteArray {
            val raw = unbox(obj) ?: return "null".encodeToByteArray()
            val mapper: ObjectMapper = objectMapper.get()
            return mapper.writeValueAsBytes(raw)
        }

        @JvmOverloads
        @JvmStatic
        actual fun fromJsonString(json: String, parseDataUrls: Boolean): Any? {
            if (USE_NEW_JSON.get()) {
                val jsonParser = naksha.base.JsonParser.threadLocal()
                return jsonParser.parse(json.encodeToByteArray())
            } else {
                val mapper: ObjectMapper = objectMapper.get()
                return mapper.readValue(json, Any::class.java)
            }
        }

        @JvmOverloads
        @JvmStatic
        actual fun fromJsonBytes(json: ByteArray, parseDataUrls: Boolean): Any? {
            if (USE_NEW_JSON.get()) {
                val jsonParser = naksha.base.JsonParser.threadLocal()
                return jsonParser.parse(json)
            } else {
                val mapper: ObjectMapper = objectMapper.get()
                return mapper.readValue(json, Any::class.java)
            }
        }

        @JvmStatic
        actual fun fromPlatform(obj: Any?, importers: Array<BaseImporter>): Any? {
            for (importer in importers) {
                if (importer.canImport(obj)) return importer.importFromPlatform(obj)
            }
            throw illegalArg("No importer supports the given object")
        }

        @JvmStatic
        actual fun toPlatform(obj: Any?, exporters: List<BaseExporter>): Any? {
            val raw = unbox(obj)
            for (exporter in exporters) {
                if (exporter.canExport(raw)) return exporter.exportToPlatform(raw)
            }
            throw illegalArg("No importer supports the given object")
        }

        @JvmStatic
        actual fun isNil(any: Any?): Boolean = any == null || any === UNDEFINED || any === INVALIDATED

        /**
         * An atomic reference to a function that creates a new thread-local logger instance.
         *
         * If not explicitly set by the application, the [Slf4jLogger] is used.
         * @since 3.0
         * @see logger
         */
        @JvmField
        actual val loggerFactory = BaseRefNotNull(Fn0 { Slf4jLogger() as IBaseLogger }).withAtomic().withImmutable()

        private data class LoggerAndFactory(val factory: Fn0<IBaseLogger>, val logger: IBaseLogger = factory.call())
        private val loggerThreadLocal = ThreadLocalNotNull { LoggerAndFactory(loggerFactory.get()) }

        @JvmStatic
        actual val logger: IBaseLogger
            get() {
                val factory = loggerFactory.get()
                var loggerAndFactory = loggerThreadLocal.get()
                if (loggerAndFactory.factory !== factory) {
                    // Factory function changed, update thread local with new factory.
                    loggerAndFactory = LoggerAndFactory(factory)
                    loggerThreadLocal.set(loggerAndFactory)
                }
                return loggerAndFactory.logger
            }

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
        actual fun currentMillis(): Long = System.currentTimeMillis()

        /**
         * Returns the current epoch microseconds.
         * @return current epoch microseconds.
         */
        @JvmStatic
        actual fun currentMicros(): Long = epochMicros + ((System.nanoTime() - startNanos) / 1000)

        /**
         * Returns the current epoch nanoseconds.
         * @return current epoch nanoseconds.
         */
        actual fun currentNanos(): Long = epochNanos + (System.nanoTime() - startNanos)

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

        @JvmStatic
        actual fun isPlv8(): Boolean = false

        @JvmStatic
        actual fun encodeURIComponent(uriComponent: String): String
            = URLEncoder.encode(uriComponent, StandardCharsets.UTF_8).replace("+", "%20")

        @JvmStatic
        actual fun decodeURIComponent(encodedURI: String): String
            = URLDecoder.decode(encodedURI, StandardCharsets.UTF_8);

        @JvmStatic
        private val md5Digest = ThreadLocal.withInitial { MessageDigest.getInstance("MD5") }

        @JvmStatic
        actual fun md5(text: String): ByteArray {
            val digest = md5Digest.get()
            digest.reset()
            digest.update(text.toByteArray(Charsets.UTF_8))
            return digest.digest()
        }

        @JvmStatic
        actual fun md5(bytes: ByteArray): ByteArray {
            val digest = md5Digest.get()
            digest.reset()
            digest.update(bytes)
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

        @JvmStatic
        actual val FAL: String
            get() {
                val frame = StackWalker.getInstance().walk { it.skip(1).findFirst().get() }
                return "[${frame.fileName}:${frame.lineNumber}] "
            }

        @JvmStatic
        fun fal(): String {
            val frame = StackWalker.getInstance().walk { it.skip(1).findFirst().get() }
            return "[${frame.fileName}:${frame.lineNumber}] "
        }

        @JvmStatic
        actual fun fal(n: Int): String {
            val backtrace = max(1, n).toLong()
            val frame = StackWalker.getInstance().walk { it.skip(backtrace).findFirst().get() }
            return "[${frame.fileName}:${frame.lineNumber}] "
        }

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
        private val ensureClassInitialized: Method? // unsafe.ensureClassInitialized
        private val lookupInstance: Any? // MethodHandles.lookup()
        private val ensureInitialized: Method? // MethodHandles.lookup().ensureInitialized(klass);

        init {
            val unsafeConstructor = Unsafe::class.java.getDeclaredConstructor()
            unsafeConstructor.isAccessible = true
            unsafe = unsafeConstructor.newInstance()
            val someByteArray = ByteArray(8)
            baseOffset = unsafe.arrayBaseOffset(someByteArray.javaClass)

            var _ensureClassInitialized: Method?
            var _ensureInitialized: Method?
            var _lookupInstance: Any?
            try {
                // Note: Before Java 15, `MethodHandles.lookup().ensureInitialized(klass)` does not exist, we need to use Unsafe!
                _ensureClassInitialized = unsafe.javaClass.getMethod("ensureClassInitialized", Class::class.java)
                _lookupInstance = null
                _ensureInitialized = null
            } catch (ignore: NoSuchMethodException) {
                // In Java 23+ `Unsafe.ensureClassInitialized` does not exist, use `MethodHandles.lookup().ensureInitialized(klass)`!
                _ensureClassInitialized = null
                val lookupMethod = MethodHandles::class.java.getMethod("lookup")
                _lookupInstance = lookupMethod.invoke(null)
                _ensureInitialized = _lookupInstance.javaClass.getMethod("ensureInitialized", Class::class.java)
            }
            ensureClassInitialized = _ensureClassInitialized
            lookupInstance = _lookupInstance
            ensureInitialized = _ensureInitialized

            // TODO: Fix me before we're done!
//            interfaceToImplementation.put(IMap::class, PAnyMap::class)
//            interfaceToImplementation.put(IMutableMap::class, PAnyMap::class)
//            interfaceToImplementation.put(IArray::class, PAnyArray::class)
//            interfaceToImplementation.put(IMutableArray::class, PAnyArray::class)
//            javaInterfaceToImplementation.put(IMap::class.java, PAnyMap::class.java)
//            javaInterfaceToImplementation.put(IMutableMap::class.java, PAnyMap::class.java)
//            javaInterfaceToImplementation.put(IArray::class.java, PAnyArray::class.java)
//            javaInterfaceToImplementation.put(IMutableArray::class.java, PAnyArray::class.java)

            initialize()
        }
    }
}
