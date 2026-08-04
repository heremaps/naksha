package naksha.base

import naksha.base.fn.Fn0
import kotlin.jvm.JvmStatic
import kotlin.math.round
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.createInstance

@JsExport
actual class Base {
    actual companion object BaseCompanion {
        @Suppress("NON_EXPORTABLE_TYPE")
        @JsStatic
        actual val interfaceToImplementation = AtomicMap<Any, Any>()

        private var isInitialized: Boolean = false

        internal val U64_MAX_VALUE = js("BigInt.asUintN(64,BigInt('18446744073709551615'))").unsafeCast<Long>()
        internal val I64_MAX_VALUE = js("BigInt.asIntN(64,BigInt('9223372036854775807'))").unsafeCast<Long>()
        internal val I64_MIN_VALUE = js("BigInt.asIntN(64,BigInt('-9223372036854775808'))").unsafeCast<Long>()
        internal val I64_ZERO = js("BigInt.asIntN(64,BigInt(0))").unsafeCast<Long>()
        internal val I64_ONE = js("BigInt.asIntN(64, BigInt(1))").unsafeCast<Long>()
        internal val I64_MINUS_ONE = js("BigInt.asIntN(64,BigInt(-1))").unsafeCast<Long>()
        internal val I64_BYTE_MASK = js("BigInt.asUintN(64,BigInt('0xff'))").unsafeCast<Long>()
        internal val I64_SHORT_MASK = js("BigInt.asUintN(64,BigInt('0xffff'))").unsafeCast<Long>()
        internal val I64_INT_MASK = js("BigInt.asUintN(64,BigInt('0xffffffff'))").unsafeCast<Long>()
        internal val I64_INT64_MASK = js("BigInt.asUintN(64,BigInt('0xffffffffffffffff'))").unsafeCast<Long>()
        internal val I64_TWO_COMPLEMENT_32 = js("BigInt.asUintN(64,BigInt('4294967296'))").unsafeCast<Long>()
        internal val I64_TWO_COMPLEMENT_64 = js("BigInt.asUintN(64,BigInt('18446744073709551616'))").unsafeCast<Long>()

        /**
         * Ensures that the given value is a real 64-bit integer.
         * @param number the value being any [Number] or a [Long].
         * @return the value as real [Long].
         */
        internal fun _int64(number: dynamic): Long {
            require(number !== undefined) { "Illegal Int64: undefined" }
            require(number !== null) { "Illegal Int64: null" }
            val v = number.valueOf()
            val type = jsTypeOf(v)
            if (type == "bigint") return v.unsafeCast<Long>()
            if (type == "number") {
                if (js("Number.isInteger(v)").unsafeCast<Boolean>()) {
                    return js("BigInt.asIntN(64,BigInt(v))").unsafeCast<Long>()
                }
                return js("BigInt.asIntN(64,BigInt(Math.round(v)))").unsafeCast<Long>()
            }
            throw IllegalArgumentException("Illegal Int64: $number::$type")
        }

        /**
         * An array of 16 64-bit signed integers.
         */
        internal val i64_arr: dynamic = js("new BigInt64Array(16)")

        /**
         * An array of 16 64-bit unsigned integers.
         */
        internal val u64_arr: dynamic = js("new BigUint64Array(16)")

        // TODO: Find out what really need to be copied to make "is" working and only copy this!
        @Suppress("UNUSED_PARAMETER")
        internal fun copyPrototypeToPrototype(source: Any, target: Any) = js(
            """
            var sp = Object.getPrototypeOf(source);
            var tp = Object.getPrototypeOf(target);
            var symbols = Object.getOwnPropertySymbols(sp);
            var i;
            for (i in symbols) {
                var symbol = symbols[i];
                tp[symbol] = sp[symbol];
            };
            var desc = {enumerable:false,writable:true,value:null};
            var keys = Object.getOwnPropertyNames(sp);
            for (i in keys) {
                var key = keys[i];
                desc.value = sp[key];
                if ("constructor"==key) continue;
                Object.defineProperty(tp, key, desc);
            };"""
        )

        @JsStatic
        actual fun initialize(): Boolean {
            if (!isInitialized) {
                isInitialized = true
                return true
            }
            return false
        }

        @JsStatic
        actual val UNDEFINED: Any = js("undefined").unsafeCast<Any>()

        @JsStatic
        actual val INVALIDATED: Any = Any()

        @JsStatic
        actual val MAX_SAFE_INT_AS_DOUBLE: Double = 9007199254740991.0

        @JsStatic
        actual val MAX_SAFE_INT_AS_LONG: Long = 9007199254740991

        @JsStatic
        actual val MIN_SAFE_INT_AS_DOUBLE: Double = -9007199254740991.0

        @JsStatic
        actual val MIN_SAFE_INT_AS_LONG: Long = -9007199254740991

        @JsStatic
        actual val EPSILON: Double = js("Number.EPSILON").unsafeCast<Double>()

        private val convertView: dynamic = js("new DataView(new ArrayBuffer(16))")

        @JsStatic
        actual fun longRawBits(d: Double): Long {
            convertView.setFloat64(0, d)
            return convertView.getBigInt64(0).unsafeCast<Long>()
        }

        @JsStatic
        actual fun doubleRawBits(i: Long): Double {
            convertView.setBigInt64(0, i)
            return convertView.getFloat64(0).unsafeCast<Double>()
        }

        @JsStatic
        actual fun isNumber(o: Any?): Boolean =
            js("o && (typeof o.valueOf()==='number' || typeof o.valueOf()==='bigint')").unsafeCast<Boolean>()

        @JsStatic
        actual fun isScalar(o: Any?): Boolean {
            if (o == null) return true
            return when (jsTypeOf(o.asDynamic().valueOf())) {
                "string", "number", "bigint", "boolean" -> true
                else -> false
            }
        }

        @JsStatic
        actual fun isInteger(o: Any?): Boolean
            = js("o && (Number.isInteger(o) || typeof o.valueOf()==='bigint')").unsafeCast<Boolean>()

        @JsStatic
        actual fun isFloat(o: Any?): Boolean = o is Number

        @JsStatic
        actual fun hashCodeOf(o: Any?): Int = o.hashCode()

        private val assignables = HashMap<KClass<*>, HashMap<KClass<*>, Boolean>>()

        @Suppress("NON_EXPORTABLE_TYPE")
        @JsStatic
        actual fun isAssignable(source: KClass<*>, target: KClass<*>): Boolean {
            var assignable = assignables[target]
            if (assignable == null) {
                assignable = HashMap()
                assignables[target] = assignable
            }
            var isAssignable = assignable[source]
            if (isAssignable == null) {
                isAssignable = target.isInstance(allocateInstance(source))
                assignable[source] = isAssignable
            }
            return isAssignable
        }

        /**
         * Returns the [KClass] created **by** the given constructor. This is mainly for JavaScript, it will simply query a cached and if not
         * found, it will create an instance, query the [KClass] using [klassOf] and add it into the cache. Therefore, the cost of
         * creating an instance to get the [KClass] is only paid ones in the lifetime of an application.
         * @param constructor The constructor.
         * @return The [KClass] that is created **by** this constructor.
         * @throws IllegalArgumentException If the given constructor does not create any valid Kotlin object.
         */
        @Suppress("NON_EXPORTABLE_TYPE")
        @JsStatic
        fun <T : Any> klassFor(constructor: KFunction<T>): KClass<out T>
            = (js("Object.create(constructor.prototype)") as T)::class

        @Suppress("NON_EXPORTABLE_TYPE", "UNCHECKED_CAST")
        @JsStatic
        actual fun <T : Any> klassForName(name: String): KClass<T> {
            @Suppress("CanBeVal") //
            var instance: T? = null
            js("""var i=0; var p=name.split("."); var k=globalThis;
while (k && i<p.length) k=k[p[i++]];
if (typeof k==='function') instance=Object.create(k.prototype);""")
            if (instance == null) throw IllegalArgumentException("Class not found '$name'")
            return instance::class as KClass<T>
        }

        @Suppress("NON_EXPORTABLE_TYPE", "UNCHECKED_CAST")
        @JsStatic
        actual fun <T : Any> klassOf(o: T): KClass<T> = o::class as KClass<T>

        /**
         * Tests if the given value is _null_ or _undefined_.
         * @param any The value to test.
         * @return _true_ if the value is _null_ or _undefined_; false otherwise.
         */
        @JsStatic
        actual fun isNil(any: Any?): Boolean {
            val UNDEFINED = Base.UNDEFINED
            val INVALIDATED = Base.INVALIDATED
            return js("any==null || any==undefined || any===UNDEFINED || any===INVALIDATED").unsafeCast<Boolean>()
        }

        @Suppress("NON_EXPORTABLE_TYPE")
        @JsStatic
        actual fun <T : Any> newInstance(klass: KClass<out T>, vararg args: Any): T {
            try {
                // See: https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Reflect/construct
                val constructor = klass.js
                return js("Reflect.construct(constructor, args)").unsafeCast<T>()
            } catch (e: Exception) {
                val name = klass.simpleName
                throw illegalArg("The class '$name' has no primary constructor for the given arguments", e)
            }
        }

        /**
         * Creates a new instance of the given type.
         *
         * This is native JavaScript variant of [newInstance], it does not require a [KClass].
         * @param constructor The constructor function to create a new instance.
         * @param args The arguments to be passed to the constructor; must not be `null`!
         * @return The new instance.
         * @throws NakshaException with [ILLEGAL_ARGUMENT][NakshaError.ILLEGAL_ARGUMENT] if there is no matching constructor found.
         * @see newInstance
         */
        @JsStatic
        fun <T : Any> newJsInstance(constructor: dynamic, vararg args: Any): T {
            try {
                // See: https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Reflect/construct
                return js("Reflect.construct(constructor, args)").unsafeCast<T>()
            } catch (e: Exception) {
                val name = constructor.name ?: "unknown function"
                throw illegalArg("The class '$name' has no primary constructor for the given arguments", e)
            }
        }

        @Suppress("NON_EXPORTABLE_TYPE")
        @JsStatic
        actual fun <T : Any> allocateInstance(klass: KClass<out T>): T {
            // We can bypass the constructor, but before we do this, we need to ensure that the companion object
            // is created, and all other things of the class are ready. We can only do this by initializing the class!
            initializeKClass(klass)
            val constructor = klass.js
            return js("Object.create(constructor.prototype)").unsafeCast<T>()
        }

        /**
         * Creates a new instance of the given type, bypassing the constructor, so it returns the uninitialized instance.
         * @param constructor The constructor to use to create a new instance.
         * @return The new instance.
         * @since 3.0
         */
        @JsStatic
        fun <T : Any> allocateJsInstance(constructor: dynamic): T {
            // We can bypass the constructor, but before we do this, we need to ensure that the companion object
            // is created, and all other things of the class are ready. We can only do this by initializing the class!
            initializeKClass(constructor)
            return js("Object.create(constructor.prototype)").unsafeCast<T>()
        }

        private val initializedClasses = HashMap<KClass<*>, Boolean>()

        @Suppress("NON_EXPORTABLE_TYPE")
        @JsStatic
        actual fun initializeKClass(klass: KClass<*>) {
            if (!initializedClasses.containsKey(klass)) {
                try {
                    @OptIn(ExperimentalJsReflectionCreateInstance::class)
                    klass.createInstance()
                } catch (_: Throwable) {
                    // We can ignore exceptions, even when our call fails, we can be sure that
                    // the class has been initialized.
                } finally {
                    initializedClasses[klass] = true
                }
            }
        }

        /**
         * Forces the class loader to initialize the given class.
         * @param constructor The type to initialize.
         */
        @JsStatic
        fun initializeJsClass(constructor: dynamic) {
            initializeKClass(constructor)
        }

        // TODO: Implement in Kotlin!
        @JsStatic
        actual fun <T> copy(obj: T, recursive: Boolean): T = js("""
if (typeof obj !== 'object' || obj === null) return obj;
if (Array.isArray(obj)) {
  if (recursive) {
    var copy = [];
    for (v in obj) copy.push(arguments.callee(item, true));
    return copy;
  }
  return obj.slice();
}
if (obj instanceof Map) {
  if (recursive) {
    var map = new Map();
    var it = obj[Symbol.iterator]();
    var e = it.next();
    while (!e.done) {
      map.set(e.value[0], arguments.callee(e.value[1], true));
      e = it.next();
    }
    return map;
  }
  return new Map(obj);
}
if (obj instanceof DataView) {
  return new DataView(obj.buffer.slice(0));
}
if (obj instanceof Object) {
  if (recursive) {
    var map = new Map();
    for (key in obj) {
        if (obj.hasOwnProperty(key)) map.set(key, arguments.callee(obj[key], true));
    }
    return map;
  }
  return new Map(Object.entries(v));
}
return obj;
""").unsafeCast<T>()

        // TODO: Replace with our own JSON parser/stringifier !
        private fun toJson(longToDataUrl: Boolean, value: dynamic): dynamic {
            return when (val raw = if (value is AbstractProxy) (value as AbstractProxy).baseObject else value) {
                is IMap -> {
                    val result: dynamic = js("{}")
                    val map = raw as IMap
                    map.forEach { k, v -> result[k.string] = toJson(longToDataUrl, v) }
                    result
                }
                is IArray -> {
                    val result: dynamic = js("[]")
                    val array = raw as IArray
                    for (i in 0 until array.length) {
                        result[i] = toJson(longToDataUrl, array[i])
                    }
                    result
                }
                is WeakRef<*>,
                is BaseRef<*>,
                is BaseRefNotNull<*> -> toJson(longToDataUrl, raw.get())
                is BaseInt -> raw.get()
                is BaseDouble -> raw.get()
                is BaseLong -> {
                    val v = raw as Long
                    if (v !in MIN_SAFE_INT_AS_LONG..MAX_SAFE_INT_AS_LONG) "data:bigint,$v" else v.toDouble()
                }
                is ByteArray -> {
                    val v = raw as ByteArray
                    TODO("Turn byte-array into data-url aka 'data:int8array,base64;...'")
                }
                else -> value
            }
        }

        @JsStatic
        actual fun toJsonString(obj: Any?, longToDataUrl: Boolean): String {
            // TODO: Recognize `longToDataUrl` argument!
            // TODO: Convert to Kotlin code
            val jsonParser: dynamic = js("""JSON""")
            val fn: (dynamic, dynamic) -> dynamic = { key, value -> when (value) {
                is IMap -> {
                    val result: dynamic = js("{}")
                    value.forEach { k, v -> result[k] = toJson( longToDataUrl, v) }
                    result
                }
                else -> value
            }}
            return jsonParser.stringify(obj, fn).unsafeCast<String>()
        }

        @JsStatic
        actual fun toJsonBytes(obj: Any?, longToDataUrl: Boolean): ByteArray {
            return toJsonString(obj, longToDataUrl).encodeToByteArray()
        }

        @JsStatic
        actual fun fromJsonString(json: String, parseDataUrls: Boolean): Any? {
            return js(
                """JSON.parse(json, function(k, v) {
  if (!v) return v;
  if (typeof v === "string" && v.startsWith("data:bigint")) {
    var i = v.indexOf(",");
    return BigInt(v.substring(i+1));
  }
  if (!Array.isArray(v) && typeof v === "object") return new Map(Object.entries(v));
  return v;
})"""
            ).unsafeCast<Any?>()
        }

        @JsStatic
        actual fun fromJsonBytes(json: ByteArray, parseDataUrls: Boolean): Any? {
            return fromJsonString(json.decodeToString(), parseDataUrls)
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

        @JsStatic
        actual fun fromPlatform(obj: Any?, importers: Array<BaseImporter>): Any? {
            for (importer in importers) {
                if (importer.canImport(obj)) return importer.importFromPlatform(obj)
            }
            throw illegalArg("No importer supports the given object")
        }

        @JsStatic
        actual fun toPlatform(obj: Any?, exporters: List<BaseExporter>): Any? {
            val raw = unbox(obj)
            for (exporter in exporters) {
                if (exporter.canExport(raw)) return exporter.exportToPlatform(raw)
            }
            throw illegalArg("No importer supports the given object")
        }

        @JsStatic
        actual val loggerFactory = BaseRefNotNull(Fn0 { JsLogger() as IBaseLogger }).withAtomic().withImmutable()

        private var theFactory = loggerFactory.get()
        private var theLogger: IBaseLogger = theFactory.call()

        @JsStatic
        actual val logger: IBaseLogger
            get() {
                val factory = loggerFactory.get()
                if (theFactory !== factory) {
                    // Factory function changed, update thread local with new factory.
                    theFactory = loggerFactory.get()
                    theLogger = theFactory.call()
                }
                return theLogger
            }

        // TODO: Implement high resolution timer, when available (sadly, not in PLV8):
        //       https://developer.mozilla.org/en-US/docs/Web/API/Performance/now

        /**
         * Returns the current epoch milliseconds.
         * @return The current epoch milliseconds.
         */
        @JsStatic
        actual fun currentMillis(): Long = js("BigInt(Date.now())").unsafeCast<Long>()

        /**
         * Returns the current epoch microseconds.
         * @return current epoch microseconds.
         */
        @JsStatic
        actual fun currentMicros(): Long = js("BigInt(Date.now()*1000)").unsafeCast<Long>()

        /**
         * Returns the current epoch nanoseconds.
         * @return current epoch nanoseconds.
         */
        @JsStatic
        actual fun currentNanos(): Long = js("BigInt(Date.now()*1000*1000)").unsafeCast<Long>()

        /**
         * Generates a new random number between 0 and 1 (therefore with 53-bit random bits).
         * @return The new random number between 0 and 1.
         */
        @JsStatic
        actual fun random(): Double = js("Math.random()").unsafeCast<Double>()

        private val MANTISSA_MASK = 0x000f_ffff_ffff_ffffL
        private val MANTISSA_LO_MASK = 0x0000_0000_1fff_ffffL

        /**
         * Tests if the given 64-bit floating point number can be converted into a 32-bit floating point number without losing information.
         * @param value The 64-bit floating point number.
         * @return _true_ if the given 64-bit float can be converted into a 32-bit one without losing information; _false_ otherwise.
         */
        @JsStatic
        actual fun canBeFloat32(value: Double): Boolean {
            // IEEE-754, 32-bit = One sign-bit, 8-bit exponent biased by 127, then 23-bit mantissa
            // IEEE-754, 64-bit = One sign-bit, 11-bit exponent biased by 1023, then 52-bit mantissa
            // E = 0 means denormalized number (M>0) or null (M=0)
            // E = 255|2047 means either endless (M=0) or not a number (M>0)
            val view = convertView
            view.setFloat64(0, value)
            var exponent = (view.getInt16(0).unsafeCast<Int>() ushr 4) and 0x7ff
            if (exponent == 0 || exponent == 2047) return false
            // Remove bias: -1023 (0) .. 1024 (2047)
            exponent -= 1023
            // 32-bit exponent is 8-bit with bias 127: -127 (0) .. 128 (255)
            // We want to avoid extremes as they encode special states.
            if (exponent < -126 || exponent > 127) return false
            // We do not want to lose precision in mantissa either.
            // Either the lower 29-bit of mantissa are zero (only 23-bit used) or all bits are set.
            val mantissa = view.getBigInt64(0).unsafeCast<Long>() and MANTISSA_MASK
            return (mantissa and MANTISSA_LO_MASK) == I64_ZERO || mantissa == MANTISSA_MASK
        }

        private const val MIN_INT_VALUE_AS_DOUBLE = Int.MIN_VALUE.toDouble()
        private const val MAX_INT_VALUE_AS_DOUBLE = Int.MAX_VALUE.toDouble()

        /**
         * Tests if the given 64-bit floating point number can be converted into a 32-bit integer without losing information.
         * @param value The 64-bit floating point number.
         * @return _true_ if the given 64-bit float can be converted into a 32-bit integer without losing information; _false_ otherwise.
         */
        @JsStatic
        actual fun canBeInt32(value: Double): Boolean {
            val rounded = round(value)
            return rounded == value && (rounded in MIN_INT_VALUE_AS_DOUBLE..MAX_INT_VALUE_AS_DOUBLE)
        }

        /**
         * Tests if this code is executed within a PostgresQL database using [PLV8 extension](https://plv8.github.io/).
         * @return _true_ if this code is executed within PostgresQL database using [PLV8 extension](https://plv8.github.io/).
         */
        @JsStatic
        actual fun isPlv8(): Boolean = js("typeof plv8==='object'").unsafeCast<Boolean>()

        @JsStatic
        actual fun encodeURIComponent(uriComponent: String): String = js("encodeURIComponent(uriComponent)") as String

        @JsStatic
        actual fun decodeURIComponent(encodedURI: String): String = js("decodeURIComponent(encodedURI)") as String

        /**
         * Calculates the MD5 hash about the given text.
         *
         * @param text the text to hash.
         * @return the MD5 hash, being a byte-array with size 16 (128-bit).
         */
        @JsStatic
        actual fun md5(text: String): ByteArray {
            if (isPlv8()) return js("plv8.execute(\"SELECT digest(\$1,'md5') as i\",[text])[0].i").unsafeCast<ByteArray>()
            // TODO: Use SubtleCrypto-API in the browser: https://developer.mozilla.org/en-US/docs/Web/API/SubtleCrypto
            throw UnsupportedOperationException("md5 is not implemented in the current environment")
        }

        /**
         * Calculates the MD5 hash about the given text.
         *
         * @param bytes the bytes to hash.
         * @return the MD5 hash, being a byte-array with size 16 (128-bit).
         */
        @JsName("md5Bytes")
        @JsStatic
        actual fun md5(bytes: ByteArray): ByteArray {
            if (isPlv8()) return js("plv8.execute(\"SELECT digest(\$1,'md5') as i\",[bytes])[0].i").unsafeCast<ByteArray>()
            // TODO: Use SubtleCrypto-API in the browser: https://developer.mozilla.org/en-US/docs/Web/API/SubtleCrypto
            throw UnsupportedOperationException("md5 is not implemented in the current environment")
        }

        /**
         * Compress bytes.
         * @param raw the bytes to compress.
         * @return the deflated (compressed) bytes.
         */
        @JsStatic
        actual fun lz4Deflate(raw: ByteArray): ByteArray {
            if (isPlv8()) js("""plv8.execute('SELECT lz4_deflate($1::bytea)::bytea as c',[raw])[0].c""").unsafeCast<ByteArray>()
            // TODO: Use Stream-API in the browser: https://developer.mozilla.org/en-US/docs/Web/API/Streams_API
            throw UnsupportedOperationException("lz4Deflate is not implemented in the current environment")
        }

        /**
         * Decompress bytes.
         * @param compressed the bytes to decompress.
         * @return the inflated (raw) bytes.
         */
        @JsStatic
        actual fun lz4Inflate(compressed: ByteArray): ByteArray {
            if (isPlv8()) js("""plv8.execute('SELECT lz4_inflate($1::bytea)::bytea as c',[compressed])[0].c""").unsafeCast<ByteArray>()
            // TODO: Use Stream-API in the browser: https://developer.mozilla.org/en-US/docs/Web/API/Streams_API
            throw UnsupportedOperationException("lz4Inflate is not implemented in the current environment")
        }

        /**
         * Compress bytes.
         * @param raw the bytes to compress.
         * @return the deflated (compressed) bytes.
         */
        @JsStatic
        actual fun gzipDeflate(raw: ByteArray): ByteArray {
            if (isPlv8()) js("""plv8.execute('SELECT gzip($1::bytea)::bytea as c',[raw])[0].c""").unsafeCast<ByteArray>()
            // TODO: Use Stream-API in the browser: https://developer.mozilla.org/en-US/docs/Web/API/Streams_API
            throw UnsupportedOperationException("lz4Inflate is not implemented in the current environment")
        }

        /**
         * Decompress bytes.
         * @param compressed The bytes to decompress.
         * @return the inflated (raw) bytes.
         */
        @JsStatic
        actual fun gzipInflate(compressed: ByteArray): ByteArray {
            if (isPlv8()) js("""plv8.execute('SELECT gunzip($1::bytea)::bytea as c',[compressed])[0].c""").unsafeCast<ByteArray>()
            // TODO: Use Stream-API in the browser: https://developer.mozilla.org/en-US/docs/Web/API/Streams_API
            throw UnsupportedOperationException("lz4Inflate is not implemented in the current environment")
        }

        @JsStatic
        actual fun stackTrace(t: Throwable): String = t.stackTraceToString()

        @JsStatic
        actual fun normalize(value: String, form: NormalizerForm): String {
            val formString = form.name
            return js("value.normalize(formString)").unsafeCast<String>()
        }

        @JsStatic
        actual val FAL: String
            get() {
                if (!isPlv8()) return ""
                return js("""
(function() {
    var orig = Error.prepareStackTrace;
    Error.prepareStackTrace = function(_, stack) { return stack; };
    var err = new Error();
    Error.captureStackTrace(err, callerInfo);
    var frame = err.stack[0];
    Error.prepareStackTrace = orig;
    return "[" + frame.getFileName() + ":" + frame.getLineNumber() + "] ";
})()
""").unsafeCast<String>()
            }

        @JsStatic
        actual fun fal(n: Int): String {
            if (!isPlv8()) return ""
            return js("""
(function() {
    var orig = Error.prepareStackTrace;
    Error.prepareStackTrace = function(_, stack) { return stack; };
    var err = new Error();
    Error.captureStackTrace(err, callerInfo);
    var frame = err.stack[Math.max(0, (n||1)-1) || 0];
    Error.prepareStackTrace = orig;
    return "[" + frame.getFileName() + ":" + frame.getLineNumber() + "] ";
})()
""").unsafeCast<String>()
        }

        init {
            initialize()
        }

    }
}
