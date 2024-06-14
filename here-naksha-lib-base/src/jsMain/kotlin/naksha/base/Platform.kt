package naksha.base

import kotlin.math.round
import kotlin.reflect.KClass
import kotlin.reflect.KFunction

@OptIn(ExperimentalJsExport::class)
@Suppress("MemberVisibilityCanBePrivate", "NON_EXPORTABLE_TYPE", "EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
@JsExport
actual class Platform {

    @OptIn(ExperimentalJsStatic::class)
    actual companion object {
        private var isInitialized: Boolean = false

        val objectTemplate = object : PlatformObject {}
        val listTemplate = object : PlatformList {
            override fun <T : P_List<*>> proxy(klass: KClass<T>, doNotOverride: Boolean): T {
                TODO("Not yet implemented")
            }
        }
        val mapTemplate = object : PlatformMap {
            override fun <T : P_Map<*, *>> proxy(klass: KClass<T>, doNotOverride: Boolean): T {
                TODO("Not yet implemented")
            }
        }
        val dataViewTemplate = object : PlatformDataView {
            override fun <T : P_DataView> proxy(klass: KClass<out T>, doNotOverride: Boolean): T {
                TODO("Not yet implemented")
            }
        }
        val symbolTemplate = object : Symbol {}
        val bigIntTemplate = object : Int64 {
            override fun unaryPlus(): Int64 {
                TODO("Not yet implemented")
            }

            override fun unaryMinus(): Int64 {
                TODO("Not yet implemented")
            }

            override fun inc(): Int64 {
                TODO("Not yet implemented")
            }

            override fun dec(): Int64 {
                TODO("Not yet implemented")
            }

            override fun plus(other: Any): Int64 {
                TODO("Not yet implemented")
            }

            override fun minus(other: Any): Int64 {
                TODO("Not yet implemented")
            }

            override fun times(other: Any): Int64 {
                TODO("Not yet implemented")
            }

            override fun div(other: Any): Int64 {
                TODO("Not yet implemented")
            }

            override fun rem(other: Any): Int64 {
                TODO("Not yet implemented")
            }

            override fun compareTo(other: Any?): Int {
                TODO("Not yet implemented")
            }

            override fun equals(other: Any?): Boolean {
                TODO("Not yet implemented")
            }

            override fun eq(other: Any?): Boolean {
                TODO("Not yet implemented")
            }

            override fun shr(bits: Int): Int64 {
                TODO("Not yet implemented")
            }

            override fun ushr(bits: Int): Int64 {
                TODO("Not yet implemented")
            }

            override fun shl(bits: Int): Int64 {
                TODO("Not yet implemented")
            }

            override fun and(other: Int64): Int64 {
                TODO("Not yet implemented")
            }

            override fun or(other: Int64): Int64 {
                TODO("Not yet implemented")
            }

            override fun xor(other: Int64): Int64 {
                TODO("Not yet implemented")
            }

            override fun inv(): Int64 {
                TODO("Not yet implemented")
            }

            override fun toByte(): Byte {
                TODO("Not yet implemented")
            }

            override fun toShort(): Short {
                TODO("Not yet implemented")
            }

            override fun toInt(): Int {
                TODO("Not yet implemented")
            }

            override fun toLong(): Long {
                TODO("Not yet implemented")
            }

            override fun toFloat(): Float {
                TODO("Not yet implemented")
            }

            override fun toDouble(): Double {
                TODO("Not yet implemented")
            }

            override fun toDoubleRawBits(): Double {
                TODO("Not yet implemented")
            }

            override fun hashCode(): Int = js("BigInt.hashCode(this)").unsafeCast<Int>()
        }

        // TODO: Find out what really need to be copied to make "is" working and only copy this!
        @Suppress("UNUSED_PARAMETER")
        fun copy(source: Any, target: Any) = js("""
var tp = Object.getPrototypeOf(target);
var sp = Object.getPrototypeOf(source);
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
    desc.value = sp[key]
    Object.defineProperty(tp, key, desc);
};
""")

        actual fun initialize(vararg parameters: Any?): Boolean {
            if (!isInitialized) {
                isInitialized = true
                copy(listTemplate, js("[]").unsafeCast<Any>())
                copy(mapTemplate, js("new Map()").unsafeCast<Any>())
                copy(objectTemplate, js("{}").unsafeCast<Any>())
                copy(symbolTemplate, js("Symbol()").unsafeCast<Any>())
                copy(bigIntTemplate, js("BigInt(0)").unsafeCast<Any>())
                js("""
Object.assign(DataView.prototype, {
    getByteArray: function() { if (!this.__byteArray) this.__byteArray = new Int8Array(this.buffer); return this.__byteArray; },
    getStart: function() { return this.byteOffset; },
    getEnd: function() { return this.byteOffset + this.byteLength; },
    getSize: function() { return this.byteLength; },
    getInt64: DataView.prototype.getBigInt64,
    setInt64: DataView.prototype.setBigInt64
});
Object.assign(BigInt, {
    MAX_VALUE_U64: BigInt("18446744073709551615"),
    MAX_VALUE_64: BigInt("9223372036854775807"),
    MIN_VALUE_64: BigInt("-9223372036854775808"),
    MAX_VALUE_32: BigInt("2147483647"),
    MIN_VALUE_32: BigInt("-2147483648"),
    MASK_LO_32: BigInt("0x00000000ffffffff"),
    MASK_HI_32: BigInt("0xffffffff00000000"),
    MASK_64: BigInt("0xffffffffffffffff"),
    TWO_COMPLEMENT_32: BigInt("4294967296"),
    TWO_COMPLEMENT_64: BigInt("18446744073709551616"),
    ZERO: BigInt(0),
    MINUS_ONE: BigInt(-1),
    u64: function(t) { return t & BigInt.MASK_64; },
    s64: function(t) { return t > BigInt.MAX_VALUE_64 ? t - BigInt.TWO_COMPLEMENT_64 : t; },
    s32: function(t) { var n=t & BigInt.MASK_LO_32; return Number(n > BigInt.MAX_VALUE_32 ? n - BigInt.TWO_COMPLEMENT_32 : n); },
    eq: function(t,v) { return t == v; },
    eqi: function(t,v) { return t == v; },
    lt: function(t,v) { return t < v; },
    lti: function(t,v) { return t < v; },
    lte: function(t,v) { return t <= v; },
    ltei: function(t,v) { return t <= v; },
    gt: function(t,v) { return t > v; },
    gti: function(t,v) { return t > v; },
    gte: function(t,v) { return t >= v; },
    gtei: function(t,v) { return t >= v; },
    shr: function(t,bits) { return t >> BigInt(bits); },
    ushr: function(t, bits) { return BigInt.u64(t) >> BigInt(bits); },
    shl: function(t,bits) { return t << BigInt(bits); },
    add: function(t,v) { return t + v; },
    addi: function(t,v) { return t + BigInt(v); },
    addf: function(t,v) { return t + BigInt(Math.floor(v)); },
    sub: function(t,v) { return t - v; },
    subi: function(t,v) { return t - BigInt(v); },
    subf: function(t,v) { return t - BigInt(Math.floor(v)); },
    mul: function(t,v) { return t * v; },
    muli: function(t,v) { return t * BigInt(v); },
    mulf: function(t,v) { return t * BigInt(Math.floor(v)); },
    div: function(t,v) { return t / v; },
    divi: function(t,v) { return t / BigInt(v); },
    divf: function(t,v) { return t / BigInt(Math.floor(v)); },
    and: function(t,v) { return BigInt.s64(BigInt.u64(t) & BigInt.u64(v)); },
    or: function(t,v) { return BigInt.s64(BigInt.u64(t) | BigInt.u64(v)); },
    xor: function(t,v) { return BigInt.s64(BigInt.u64(t) ^ BigInt.u64(v)); },
    inv: function(t) { return BigInt.s64(~BigInt.u64(t)); },
    equals: function(t) { return this == t; },
    hashCode: function(t) { var u=BigInt.u64(t); return BigInt.s32((u >> BigInt(32)) ^ (u & BigInt.MASK_LO_32)); }
});
""")
                return true
            }
            return false
        }

        @JsStatic
        actual val DEFAULT_SYMBOL = symbol("com.here.naksha.lib.nak")
        @JsStatic
        actual val ITERATOR: Symbol = js("Symbol.iterator").unsafeCast<Symbol>()
        @JsStatic
        actual val INT64_MAX_VALUE: Int64 = js("BigInt('9223372036854775807')").unsafeCast<Int64>()
        @JsStatic
        actual val INT64_MIN_VALUE: Int64 = js("BigInt('9223372036854775808')").unsafeCast<Int64>()
        @JsStatic
        actual val MAX_SAFE_INT: Double = 9007199254740991.0
        @JsStatic
        actual val MIN_SAFE_INT: Double = -9007199254740991.0

        @JsStatic
        actual fun intern(s: String, cd: Boolean): String = js("(cd ? s.normalize('NFC') : s.normalize('NFKC'))").unsafeCast<String>()

//        @Suppress("UNUSED_VARIABLE")
//        actual fun <T : Proxy> proxy(o: Any, klass: OldBaseKlass<T>, vararg args: Any?): T {
//            val sym = klass.symbol()
//            val raw = unbox(o)
//            var nakType: Any? = js("raw[sym]")
//            if (klass.isInstance(nakType)) return nakType.unsafeCast<T>()
//            require(klass.isAssignable(raw))
//            nakType = klass.newInstance(*args)
//            nakType.data = raw
//            js("raw[sym]=t")
//            return nakType
//        }
//
//        @Suppress("UNUSED_VARIABLE")
//        actual fun <T : Proxy> forceAssign(o: Any, klass: OldBaseKlass<T>, vararg args: Any?): T {
//            val sym = klass.symbol()
//            val raw = unbox(o)
//            var nakType: Any? = js("raw[sym]")
//            if (klass.isInstance(nakType)) return nakType.unsafeCast<T>()
//            require(klass.getPlatformKlass().isInstance(raw))
//            nakType = klass.newInstance(*args)
//            nakType.data = raw
//            js("raw[sym]=t")
//            return nakType
//        }

        //@JsStatic
        private fun symbol(key: String?): Symbol = js("(key ? Symbol.for(key) : Symbol())").unsafeCast<Symbol>()

        @Suppress("UNUSED_VARIABLE")
        @JsStatic
        actual fun newMap(vararg entries: Any?): PlatformMap {
            val map = js("new Map()").unsafeCast<PlatformMap>()
            if (entries.isNotEmpty()) {
                var i = 0
                while (i < entries.size) {
                    val key = entries[i++]
                    val value = if (i < entries.size) entries[i++] else null
                    js("map.set(key, value)")
                }
            }
            return map
        }

        @JsStatic
        actual fun newList(vararg entries: Any?): PlatformList {
            val array = js("[]").unsafeCast<PlatformList>()
            if (entries.isNotEmpty()) {
                var i = 0
                while (i < entries.size) {
                    js("array[i]=value")
                    i++
                }
            }
            return array
        }

        @JsStatic
        actual fun newByteArray(size: Int): ByteArray = ByteArray(size)

        @JsStatic
        actual fun newDataView(byteArray: ByteArray, offset: Int, size: Int): PlatformDataView = js("""
offset = offset ? Math.ceil(offset) : 0;
size = size ? Math.floor(size) : byteArray.byteLength - offset;
return new DataView(byteArray.buffer, offset, size);
""").unsafeCast<PlatformDataView>()

        @JsStatic
        actual fun unbox(value: Any?): Any? = if (value is Proxy) value.data() else value

        @JsStatic
        actual fun toInt(value: Any): Int = js("Number(value) >> 0").unsafeCast<Int>()

        @JsStatic
        actual fun toInt64(value: Any): Int64 = js("BigInt(value)").unsafeCast<Int64>()

        @JsStatic
        actual fun toDouble(value: Any): Double = js("Number(value)").unsafeCast<Double>()

        @Suppress("NON_EXPORTABLE_TYPE")
        @JsStatic
        actual fun longToInt64(value: Long): Int64 {
            val view = convertDV
            view.setInt32(0, (value ushr 32).toInt())
            view.setInt32(4, value.toInt())
            return js("view.getBigInt64(0)").unsafeCast<Int64>()
        }

        @Suppress("NON_EXPORTABLE_TYPE")
        @JsStatic
        actual fun int64ToLong(value: Int64): Long {
            val view = convertDV
            js("view.setBigInt64(0, value)")
            val hi = view.getInt32(0).unsafeCast<Int>()
            val lo = view.getInt32(4).unsafeCast<Int>()
            return ((hi.toLong() and 0xffff_ffff) shl 32) or (lo.toLong() and 0xffff_ffff).unsafeCast<Long>()
        }

        @JsStatic
        val convertDV = js("new DataView(new ArrayBuffer(16))")

        @JsStatic
        actual fun toInt64RawBits(d: Double): Int64 {
            val view = convertDV
            js("view.setFloat64(0, value)")
            return js("view.getInt64(0)").unsafeCast<Int64>()
        }

        @JsStatic
        actual fun toDoubleRawBits(i: Int64): Double {
            val view = convertDV
            js("view.setInt64(0, value)")
            return js("view.getFloat64(0)").unsafeCast<Double>()
        }

        @JsStatic
        actual fun isNumber(o: Any?): Boolean = js("o && (typeof o.valueOf()==='number' || typeof o.valueOf()==='bigint')").unsafeCast<Boolean>()

        @JsStatic
        actual fun isInteger(o: Any?): Boolean = js("o && (Number.isInteger(o) || typeof o.valueOf()==='bigint')").unsafeCast<Boolean>()

        @JsStatic
        actual fun isDouble(o: Any?): Boolean = o is Number

//        actual fun has(o: Any?, key: Any?): Boolean = js("Object.hasOwn(o, key)").unsafeCast<Boolean>()
//
//        actual fun get(o: Any, key: Any): Any? = js("o[key]")
//
//        actual fun set(o: Any, key: Any, value: Any?): Any? = js("var old=o[key]; value===undefined ? delete o[key] : o[key]=value; old")
//
//        actual fun delete(o: Any, key: Any): Any? = js("var old=o[key]; delete o[key]; old")
//
//        actual fun arrayIterator(o: PlatformList): PlatformIterator<Int,Any?> = JsArrayIterator(o)
//
//        actual fun objectIterator(o: PlatformObject): PlatformIterator<String,Any?> = JsObjectIterator(o)
//
//        actual fun count(obj: Any?): Int = if (obj != null) keys(obj).size else 0
//
//        actual fun length(a: PlatformList?): Int = js("(Array.isArray(a) ? a.length : 0)").unsafeCast<Int>()
//
//        actual fun keys(obj: Any): Array<String> = js("var k=Object.keys(o); (Array.isArray(o) ? k.splice(o.length) : k)").unsafeCast<Array<String>>()
//
//        actual fun keysOfMembers(obj: Any): Array<Symbol> = js("Object.getOwnPropertySymbols(o)").unsafeCast<Array<Symbol>>()
//
//        actual fun values(obj: Any): Array<Any?> = js("var v=Object.values(o); if (Array.isArray(o)) v.splice(o.length,v.length); v").unsafeCast<Array<Any?>>()

        @JsStatic
        actual fun compare(a: Any?, b: Any?): Int {
            TODO("Fix me, see documentation!")
        }

        @JsStatic
        actual fun hashCodeOf(o: Any?): Int {
            if (o == null) return 0
            val S = DEFAULT_SYMBOL
            val nak : dynamic = o
            if (js("nak[S] && typeof nak[S].hashCode === 'function'").unsafeCast<Boolean>()) {
                try {
                    return nak[DEFAULT_SYMBOL].hashCode().unsafeCast<Int>()
                } catch (ignore: Throwable) {
                }
            }
            // TODO: Fix me, see documentation!
            return Fnv1a32.string(Fnv1a32.start(), nak.toString())
        }

        @JsStatic
        actual fun isAssignableFrom(fromSource: KClass<*>, toTarget: KClass<*>): Boolean = TODO("Fix me, see documentation!")

        @JsStatic
        actual fun isProxyKlass(klass: KClass<*>): Boolean = TODO("Fix me, see documentation!")

        // TODO: Find the constructor in namespace of module.
        @JsStatic
        actual fun <T : Any> klassFor(constructor: KFunction<T>): KClass<out T> = js("""require('module_name').package.full.path.ClassName""").unsafeCast<KClass<T>>()

        @JsStatic
        actual fun <T : Any> klassOf(o: T) : KClass<out T> = o::class

        /**
         * The KClass for [Any].
         */
        @JsStatic
        actual val anyKlass: KClass<Any> = Any::class

        /**
         * The KClass for [Boolean].
         */
        @JsStatic
        actual val booleanKlass: KClass<Boolean> = Boolean::class

        /**
         * The KClass for [Short].
         */
        @JsStatic
        actual val shortKlass: KClass<Short> = Short::class

        /**
         * The KClass for [Int].
         */
        @JsStatic
        actual val intKlass: KClass<Int> = Int::class

        /**
         * The KClass for [Int64].
         */
        @JsStatic
        actual val int64Klass: KClass<Int64> = Int64::class

        /**
         * The KClass for [Double].
         */
        @JsStatic
        actual val doubleKlass: KClass<Double> = Double::class

        /**
         * The KClass for [String].
         */
        @JsStatic
        actual val stringKlass: KClass<String> = String::class

        /**
         * The KClass for [PlatformObject].
         */
        @JsStatic
        actual val objectKlass: KClass<PlatformObject> = PlatformObject::class

        /**
         * The KClass for [PlatformList].
         */
        @JsStatic
        actual val listKlass: KClass<PlatformList> = PlatformList::class

        /**
         * The KClass for [PlatformMap].
         */
        @JsStatic
        actual val mapKlass: KClass<PlatformMap> = PlatformMap::class

        /**
         * The KClass for [PlatformDataViewApi].
         */
        @JsStatic
        actual val dataViewKlass: KClass<PlatformDataView> = PlatformDataView::class

        /**
         * Tests if the given value is _null_ or _undefined_.
         * @param any The value to test.
         * @return _true_ if the value is _null_ or _undefined_; false otherwise.
         */
        @JsStatic
        actual fun isNil(any: Any?): Boolean = js("any===null || any===undefined").unsafeCast<Boolean>()

        /**
         * Creates an undefined value for the given type or returns the cached one.
         * @param klass The type for which to create an undefined value.
         * @return The undefined value.
         */
        @JsStatic
        actual fun <T : Any> undefinedOf(klass: KClass<T>): T {
            TODO("Not yet implemented")
        }

        /**
         * Creates a new instance of the given type.
         * @param klass The type of which to create a new instance.
         * @return The new instance.
         */
        @JsStatic
        actual fun <T : Any> newInstanceOf(klass: KClass<T>): T {
            TODO("Not yet implemented")
        }

        /**
         * Serialize the given value to JSON.
         * @param obj The object to serialize.
         * @return The JSON.
         */
        @JsStatic
        actual fun toJSON(obj: Any?, options: ToJsonOptions): String {
            TODO("Not yet implemented")
        }

        /**
         * Deserialize the given JSON.
         * @param json The JSON string to parse.
         * @return The parsed JSON.
         */
        @JsStatic
        actual fun fromJSON(json: String, options: FromJsonOptions): Any? {
            TODO("Not yet implemented")
        }

        /**
         * Convert the given platform native objects recursively into multi-platform objects. So all maps are corrected to [PlatformMap],
         * all strings starting with `data:bigint,` or Java `Long`'s are converted into [Int64]'s, lists are corrected to [PlatformList],
         * and so on. This can be used after a JSON was parsed from an arbitrary platform tool into some platform specific standard
         * objects or when exchanging data with a platform specific library that does not like the multi-platform objects.
         * @param obj The platform native objects to convert recursively.
         * @param importers The importers to use.
         * @return The given platform native objects converted into multi-platform objects.
         */
        @JsStatic
        actual fun fromPlatform(obj: Any?, importers: List<PlatformImporter>): Any? {
            TODO("Not yet implemented")
        }

        /**
         * Convert the given multi-platform objects recursively into the default platform native objects, for example [PlatformMap] may
         * become a pure `Object` in JavaScript. This is often useful when exchanging code with libraries that do not support `Map`.
         * In Java this will convert to [PlatformMap] to [LinkedHashMap].
         * @param obj The multi-platform objects to be converted into platform native objects.
         * @param exporters The exporters to use.
         * @return The platform native objects.
         */
        @JsStatic
        actual fun toPlatform(obj: Any?, exporters: List<PlatformExporter>): Any? {
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
        @JsStatic
        actual fun <T : Proxy> proxy(pobject: PlatformObject, klass: KClass<T>, doNotOverride: Boolean): T {
            TODO("Not yet implemented")
        }

        private val defaultBaseLogger = object : BaseLogger {
            // https://developer.mozilla.org/en-US/docs/Web/API/console
            // TODO: When the argument is a scalar, directly concat, only leave objects as own arguments.
            //       So, we expect that ("Hello {}", "World") returns ["Hello World"] and not ["Hello ", "World"]!
            private fun argsToArray(msg: String, vararg args: Any?): Array<Any?> {
                val result: Array<Any?> = js("[]").unsafeCast<Array<Any?>>()
                js("""
var a = 0;
var msg_arr = msg.split(/(?={})/g);
console.log(msg_arr);
var t = 0;
for (i=0; i < msg_arr.length; i++) {
  var m = msg_arr[i];
  if (m.startsWith("{}")) {
    result[t++]=args[a++];
    if (m.length > 2) result[t++]=m.substring(2);
  } else {
    result[t++]=m;
  }
}""")
                return result
            }

            override fun debug(msg: String, vararg args: Any?) {
                val a = argsToArray(msg, *args)
                js("eval('console.debug(...a)')")
            }

            override fun atDebug(msgFn: () -> String?) {
                val msg = msgFn.invoke()
                js("console.debug(msg)")
            }

            override fun info(msg: String, vararg args: Any?) {
                val a = argsToArray(msg, *args)
                js("eval('console.log(...a)')")
            }

            override fun atInfo(msgFn: () -> String?) {
                val msg = msgFn.invoke()
                js("console.log(msg)")
            }

            override fun warn(msg: String, vararg args: Any?) {
                val a = argsToArray(msg, *args)
                js("eval('console.warn(...a)')")
            }

            override fun atWarn(msgFn: () -> String?) {
                val msg = msgFn.invoke()
                js("console.warn(msg)")
            }

            override fun error(msg: String, vararg args: Any?) {
                val a = argsToArray(msg, *args)
                js("eval('console.error(...a)')")
            }

            override fun atError(msgFn: () -> String?) {
                val msg = msgFn.invoke()
                js("console.error(msg)")
            }
        }

        /**
         * The [BaseLogger].
         */
        @JsStatic
        actual val logger: BaseLogger = TODO("Create implementation")

        /**
         * Creates a new thread-local. Should be stored only in a static immutable variable (`val`).
         * @param initializer An optional lambda to be invoked, when the thread-local is read for the first time.
         * @return The thread local.
         */
        @JsStatic
        actual fun <T> newThreadLocal(initializer: (() -> T)?): BaseThreadLocal<T> {
            TODO("Not yet implemented")
        }

        // TODO: Implement high resolution timer, when available (sadly, not in PLV8):
        //       https://developer.mozilla.org/en-US/docs/Web/API/Performance/now

        /**
         * Returns the current epoch milliseconds.
         * @return The current epoch milliseconds.
         */
        @JsStatic
        actual fun currentMillis(): Int64 = js("BigInt(Date.now())").unsafeCast<Int64>()

        /**
         * Returns the current epoch microseconds.
         * @return current epoch microseconds.
         */
        @JsStatic
        actual fun currentMicros(): Int64 = js("BigInt(Date.now()*1000)").unsafeCast<Int64>()

        /**
         * Returns the current epoch nanoseconds.
         * @return current epoch nanoseconds.
         */
        actual fun currentNanos(): Int64 = js("BigInt(Date.now()*1000*1000)").unsafeCast<Int64>()

        /**
         * Generates a new random number between 0 and 1 (therefore with 53-bit random bits).
         * @return The new random number between 0 and 1.
         */
        @JsStatic
        actual fun random(): Double = js("Math.random()").unsafeCast<Double>()

        /**
         * Tests if the given 64-bit floating point number can be converted into a 32-bit floating point number without losing information.
         * @param value The 64-bit floating point number.
         * @return _true_ if the given 64-bit float can be converted into a 32-bit one without losing information; _false_ otherwise.
         */
        @JsStatic
        actual fun canBeFloat32(value: Double): Boolean {
            TODO("Fix me!")
//            // IEEE-754, 32-bit = One sign-bit, 8-bit exponent biased by 127, then 23-bit mantissa
//            // IEEE-754, 64-bit = One sign-bit, 11-bit exponent biased by 1023, then 52-bit mantissa
//            // E = 0 means denormalized number (M>0) or null (M=0)
//            // E = 255|2047 means either endless (M=0) or not a number (M>0)
//            val view = view()
//            view.setFloat64(0, value)
//            var exponent = (view.getInt16(0).toInt() ushr 4) and 0x7ff
//            if (exponent == 0 || exponent == 2047) return false
//            // Remove bias: -1023 (0) .. 1024 (2047)
//            exponent -= 1023
//            // 32-bit exponent is 8-bit with bias 127: -127 (0) .. 128 (255)
//            // We want to avoid extremes as they encode special states.
//            if (exponent < -126 || exponent > 127) return false
//            // We do not want to lose precision in mantissa either.
//            // Either the lower 29-bit of mantissa are zero (only 23-bit used) or all bits are set.
//            val mantissaHi = view.getInt32(0) and 0x000f_ffff
//            val mantissaLo = view.getInt32(4)
//            return mantissaLo and 0x1fff_ffff == 0 || (mantissaHi == 0x000f_ffff && mantissaLo == 0xffff_ffffu.toInt())
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
         * Compress bytes.
         * @param raw The bytes to compress.
         * @param offset The offset of the first byte to compress.
         * @param size The amount of bytes to compress.
         * @return The deflated (compressed) bytes.
         */
        @JsStatic
        actual fun lz4Deflate(raw: ByteArray, offset: Int, size: Int): ByteArray {
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
        @JsStatic
        actual fun lz4Inflate(
            compressed: ByteArray,
            bufferSize: Int,
            offset: Int,
            size: Int
        ): ByteArray {
            TODO("Not yet implemented")
        }

        /**
         * Compress bytes.
         * @param raw The bytes to compress.
         * @param offset The offset of the first byte to compress.
         * @param size The amount of bytes to compress.
         * @return The deflated (compressed) bytes.
         */
        @JsStatic
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
        @JsStatic
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