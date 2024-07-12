package naksha.base

import kotlin.math.round
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.createInstance

@OptIn(ExperimentalJsExport::class)
@Suppress("MemberVisibilityCanBePrivate", "NON_EXPORTABLE_TYPE", "EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING", "OPT_IN_USAGE")
@JsExport
actual object Platform {
    private var isInitialized: Boolean = false

    internal val U64_MAX_VALUE = js("BigInt.asUintN(64,BigInt('18446744073709551615'))").unsafeCast<Int64>()
    internal val I64_MAX_VALUE = js("BigInt.asIntN(64,BigInt('9223372036854775807'))").unsafeCast<Int64>()
    internal val I64_MIN_VALUE = js("BigInt.asIntN(64,BigInt('-9223372036854775808'))").unsafeCast<Int64>()
    internal val I64_ZERO = js("BigInt.asIntN(64,BigInt(0))").unsafeCast<Int64>()
    internal val I64_ONE = js("BigInt.asIntN(64, BigInt(1))").unsafeCast<Int64>()
    internal val I64_MINUS_ONE = js("BigInt.asIntN(64,BigInt(-1))").unsafeCast<Int64>()
    internal val I64_BYTE_MASK = js("BigInt.asUintN(64,BigInt('0xff'))").unsafeCast<Int64>()
    internal val I64_SHORT_MASK = js("BigInt.asUintN(64,BigInt('0xffff'))").unsafeCast<Int64>()
    internal val I64_INT_MASK = js("BigInt.asUintN(64,BigInt('0xffffffff'))").unsafeCast<Int64>()
    internal val I64_INT64_MASK = js("BigInt.asUintN(64,BigInt('0xffffffffffffffff'))").unsafeCast<Int64>()
    internal val I64_TWO_COMPLEMENT_32 = js("BigInt.asUintN(64,BigInt('4294967296'))").unsafeCast<Int64>()
    internal val I64_TWO_COMPLEMENT_64 = js("BigInt.asUintN(64,BigInt('18446744073709551616'))").unsafeCast<Int64>()

    /**
     * Ensures that the given value is a real 64-bit integer.
     * @param number the value being any [Number] or a [Int64].
     * @return the value as real [Int64].
     */
    internal fun _int64(number: dynamic): Int64 {
        require(number !== undefined) { "Illegal Int64: undefined" }
        require(number !== null) { "Illegal Int64: null" }
        val v = number.valueOf()
        val type = jsTypeOf(v)
        if (type == "bigint") return v.unsafeCast<Int64>()
        if (type == "number") {
            if (js("Number.isInteger(v)").unsafeCast<Boolean>()) {
                return js("BigInt.asIntN(64,BigInt(v))").unsafeCast<Int64>()
            }
            return js("BigInt.asIntN(64,BigInt(Math.round(v)))").unsafeCast<Int64>()
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
    private fun copyPrototypeToPrototype(source: Any, target: Any) = js(
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

    val objectTemplate = object : PlatformObject {}
    val listTemplate = object : PlatformList {
        override fun <T : Proxy> proxy(klass: KClass<T>): T = proxy(this, klass)
    }
    val mapTemplate = object : PlatformMap {
        override fun <T : Proxy> proxy(klass: KClass<T>): T = proxy(this, klass)
    }
    val dataViewTemplate = object : PlatformDataView {
        override fun <T : Proxy> proxy(klass: KClass<T>): T = proxy(this, klass)
    }
    val symbolTemplate = object : Symbol {}
    val weakRefTemplate = object : WeakRef<Int> {
        override fun deref(): Int = 0
    }

    actual fun initialize(): Boolean {
        if (!isInitialized) {
            isInitialized = true
            copyPrototypeToPrototype(objectTemplate, js("{}").unsafeCast<Any>())
            copyPrototypeToPrototype(listTemplate, js("[]").unsafeCast<Any>())
            copyPrototypeToPrototype(mapTemplate, js("new Map()").unsafeCast<Any>())
            copyPrototypeToPrototype(dataViewTemplate, js("new DataView(new ArrayBuffer(0))").unsafeCast<Any>())

            copyPrototypeToPrototype(symbolTemplate, js("Symbol()").unsafeCast<Any>())
            copyPrototypeToPrototype(weakRefTemplate, js("new WeakRef(Object(0))").unsafeCast<Any>())
            copyPrototypeToPrototype(JsInt64(), js("BigInt(0)").unsafeCast<Any>())
            // Patch the Int64::class, so that it works as expected (it should only detect BigInt!)
            val i64Class = Int64::class
            js(
                """
            var pt = Object.getPrototypeOf(i64Class);
            var keys = Object.getOwnPropertyNames(pt);
            var isInstanceOfName = null;
            var i;
            for (i in keys) {
                var key = keys[i];
                if (key.startsWith("isInstance")) isInstanceOfName = key;
            };
            // Note: Do not override pt[isInstanceOfName]!
            //       If we do this, then all isInstanceOf calls are overloaded, 
            //       but we only want to overload the one of Int64::class!
            i64Class[isInstanceOfName] = function(value) {
              return value != null && typeof value.valueOf()==="bigint";
            };"""
            )
            return true
        }
        return false
    }

    actual val DEFAULT_SYMBOL = symbol("com.here.naksha.lib.nak")

    actual val ITERATOR: Symbol = js("Symbol.iterator").unsafeCast<Symbol>()

    actual val INT64_MAX_VALUE: Int64 = js("BigInt('9223372036854775807')").unsafeCast<Int64>()

    actual val INT64_MIN_VALUE: Int64 = js("BigInt('-9223372036854775808')").unsafeCast<Int64>()

    actual val MAX_SAFE_INT: Double = 9007199254740991.0

    actual val MIN_SAFE_INT: Double = -9007199254740991.0

    actual val EPSILON: Double = js("Number.EPSILON").unsafeCast<Double>()

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

    actual fun newMap(vararg entries: Any?): PlatformMap {
        val map = js("new Map()")
        if (entries.isNotEmpty()) {
            var i = 0
            while (i < entries.size) {
                val key = entries[i++]
                val value = if (i < entries.size) entries[i++] else null
                map.set(key, value)
            }
        }
        return map.unsafeCast<PlatformMap>()
    }

    actual fun <K : Any, V : Any> newCMap(): CMap<K, V> = JsCMap<K, V>()

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

    actual fun newByteArray(size: Int): ByteArray = ByteArray(size)

    actual fun newDataView(byteArray: ByteArray, offset: Int, size: Int): PlatformDataView =
        js("new DataView(byteArray.buffer, offset, size)").unsafeCast<PlatformDataView>()

    // Note: Some values in JavaScript must be wrapped into an object (like string literals or numbers), however,
    //       not all numbers of string literals, so we only wrap, when really necessary!

    actual fun <T : Any> newWeakRef(referent: T): WeakRef<T> =
        js("try { return new WeakRef(referent); } catch(e) { return new WeakRef(Object(referent)); }").unsafeCast<WeakRef<T>>()

    /**
     * Creates a new reentrant lock.
     * @return the created reentrant lock.
     */
    actual fun newLock(): PlatformLock = JsLock()

    actual fun unbox(value: Any?): Any? {
        if (value == null) return value
        if (value is Proxy) return value.platformObject()
        return if (isScalar(value)) value.asDynamic().valueOf() else value
    }

    actual fun toInt(value: Any): Int = when (value) {
        is Long -> value.toInt()
        is Int64 -> value.toInt()
        is Number -> value.toInt()
        is String -> value.toInt()
        else -> throw IllegalArgumentException("Failed to convert object to int")
    }

    actual fun toInt64(value: Any): Int64 = when (value) {
        is Long -> longToInt64(value)
        is Int64 -> value
        is Byte, Short, Int -> js("BigInt(value)").unsafeCast<Int64>()
        is Float, Double -> js("BigInt(Math.round(value))").unsafeCast<Int64>()
        is String -> js("BigInt(value)").unsafeCast<Int64>()
        else -> throw IllegalArgumentException("Failed to convert object to int64")
    }

    actual fun toDouble(value: Any): Double = when (value) {
        is Long -> value.toDouble()
        is Int64 -> value.toDouble()
        is Number -> value.toDouble()
        is String -> value.toDouble()
        else -> throw IllegalArgumentException("Failed to convert object to double")
    }

    @Suppress("NON_EXPORTABLE_TYPE")
    actual fun longToInt64(value: Long): Int64 {
        val view = convertView
        view.setInt32(0, (value ushr 32).toInt())
        view.setInt32(4, value.toInt())
        return view.getBigInt64(0).unsafeCast<Int64>()
    }

    @Suppress("NON_EXPORTABLE_TYPE")
    actual fun int64ToLong(value: Int64): Long {
        val view = convertView
        view.setBigInt64(0, value)
        val hi = view.getInt32(0).unsafeCast<Int>()
        val lo = view.getInt32(4).unsafeCast<Int>()
        return ((hi.toLong() and 0xffff_ffff) shl 32) or (lo.toLong() and 0xffff_ffff).unsafeCast<Long>()
    }

    internal val convertView: dynamic = js("new DataView(new ArrayBuffer(16))")

    actual fun toInt64RawBits(d: Double): Int64 {
        convertView.setFloat64(0, d)
        return convertView.getBigInt64(0).unsafeCast<Int64>()
    }

    actual fun toDoubleRawBits(i: Int64): Double {
        convertView.setBigInt64(0, i)
        return convertView.getFloat64(0).unsafeCast<Double>()
    }

    actual fun isNumber(o: Any?): Boolean =
        js("o && (typeof o.valueOf()==='number' || typeof o.valueOf()==='bigint')").unsafeCast<Boolean>()

    actual fun isScalar(o: Any?): Boolean {
        if (o == null) return true
        return when (jsTypeOf(o.asDynamic().valueOf())) {
            "string", "number", "bigint", "boolean" -> true
            else -> false
        }
    }

    actual fun isInteger(o: Any?): Boolean = js("o && (Number.isInteger(o) || typeof o.valueOf()==='bigint')").unsafeCast<Boolean>()

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

    actual fun compare(a: Any?, b: Any?): Int {
        TODO("Fix me, see documentation!")
    }

    actual fun hashCodeOf(o: Any?): Int {
        if (o == null) return 0
        val S = DEFAULT_SYMBOL
        val nak: dynamic = o
        if (nak[S] != null && jsTypeOf(nak[S].hashCode) == "function") {
            try {
                return nak[DEFAULT_SYMBOL].hashCode().unsafeCast<Int>()
            } catch (ignore: Throwable) {
            }
        }
        // TODO: Fix me, see documentation!
        return Fnv1a32.string(Fnv1a32.start(), nak.toString())
    }

    private val assignables = HashMap<KClass<*>, HashMap<KClass<*>, Boolean>>()

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

    actual fun isProxyKlass(klass: KClass<*>): Boolean = isAssignable(klass, Proxy::class)

    // TODO: Find the constructor in namespace of module.
    actual fun <T : Any> klassFor(constructor: KFunction<T>): KClass<out T> =
        js("""require('module_name').package.full.path.ClassName""").unsafeCast<KClass<T>>()

    @Suppress("UNCHECKED_CAST")
    actual fun <T : Any> klassOf(o: T): KClass<T> = o::class as KClass<T>

    /**
     * The KClass for [Any].
     */
    actual val anyKlass: KClass<Any> = Any::class

    /**
     * The KClass for [Boolean].
     */
    actual val booleanKlass: KClass<Boolean> = Boolean::class

    /**
     * The KClass for [Short].
     */
    actual val shortKlass: KClass<Short> = Short::class

    /**
     * The KClass for [Int].
     */
    actual val intKlass: KClass<Int> = Int::class

    /**
     * The KClass for [Int64].
     */
    actual val int64Klass: KClass<Int64> = Int64::class

    /**
     * The KClass for [Double].
     */
    actual val doubleKlass: KClass<Double> = Double::class

    /**
     * The KClass for [String].
     */
    actual val stringKlass: KClass<String> = String::class

    /**
     * The KClass for [PlatformObject].
     */
    actual val objectKlass: KClass<PlatformObject> = PlatformObject::class

    /**
     * The KClass for [PlatformList].
     */
    actual val listKlass: KClass<PlatformList> = PlatformList::class

    /**
     * The KClass for [PlatformMap].
     */
    actual val mapKlass: KClass<PlatformMap> = PlatformMap::class

    /**
     * The KClass for [PlatformDataViewApi].
     */
    actual val dataViewKlass: KClass<PlatformDataView> = PlatformDataView::class

    /**
     * Tests if the given value is _null_ or _undefined_.
     * @param any The value to test.
     * @return _true_ if the value is _null_ or _undefined_; false otherwise.
     */
    actual fun isNil(any: Any?): Boolean = js("any===null || any===undefined").unsafeCast<Boolean>()

    /**
     * Creates a new instance of the given type.
     * @param klass The type of which to create a new instance.
     * @return The new instance.
     * @throws IllegalArgumentException If the given class does not have a parameterless constructor.
     */
    actual fun <T : Any> newInstanceOf(klass: KClass<out T>): T {
        try {
            return klass.createInstance()
        } catch (e: Exception) {
            if (e is IllegalArgumentException) throw e
            throw IllegalArgumentException(e)
        }
    }

    /**
     * Creates a new instance of the given type, bypassing the constructor, so it returns the uninitialized class.
     * @param klass The type of which to create a new instance.
     * @return The new instance.
     */
    actual fun <T : Any> allocateInstance(klass: KClass<out T>): T {
        // We can bypass the constructor, but before we do this, we need to ensure that the companion object
        // is created, and all other things of the class are ready. We can only do this by initializing the class!
        initializeKlass(klass)
        val constructor = klass.js
        return js("Object.create(constructor.prototype)").unsafeCast<T>()
    }

    private val initializedClasses = HashMap<KClass<*>, Boolean>()

    actual fun initializeKlass(klass: KClass<*>) {
        if (!initializedClasses.containsKey(klass)) {
            try {
                klass.createInstance()
            } catch (ignore: Throwable) {
            } finally {
                initializedClasses[klass] = true
            }
        }
    }

    /**
     * Serialize the given value to JSON.
     * @param obj The object to serialize.
     * @return The JSON.
     */
    actual fun toJSON(obj: Any?, options: ToJsonOptions): String {
        val o = if (obj is Proxy) obj.platformObject() else obj
        return js(
            """JSON.stringify(o, function(k, v) {
  if (!v) return v;
  if (v.valueOf() instanceof Map) return Object.fromEntries(v.valueOf().entries());
  if (typeof v.valueOf() === "bigint") return "data:bigint;dec,"+String(v);
  return v;
})"""
        ).unsafeCast<String>()
    }

    /**
     * Deserialize the given JSON.
     * @param json The JSON string to parse.
     * @return The parsed JSON.
     */
    actual fun fromJSON(json: String, options: FromJsonOptions): Any? = js(
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

    /**
     * Convert the given platform native objects recursively into multi-platform objects. So all maps are corrected to [PlatformMap],
     * all strings starting with `data:bigint,` or Java `Long`'s are converted into [Int64]'s, lists are corrected to [PlatformList],
     * and so on. This can be used after a JSON was parsed from an arbitrary platform tool into some platform specific standard
     * objects or when exchanging data with a platform specific library that does not like the multi-platform objects.
     * @param obj The platform native objects to convert recursively.
     * @param importers The importers to use.
     * @return The given platform native objects converted into multi-platform objects.
     */
    actual fun fromPlatform(obj: Any?, importers: List<PlatformImporter>): Any? {
        TODO("Not yet implemented fromPlatform")
    }

    /**
     * Convert the given multi-platform objects recursively into the default platform native objects, for example [PlatformMap] may
     * become a pure `Object` in JavaScript. This is often useful when exchanging code with libraries that do not support `Map`.
     * In Java this will convert to [PlatformMap] to [LinkedHashMap].
     * @param obj The multi-platform objects to be converted into platform native objects.
     * @param exporters The exporters to use.
     * @return The platform native objects.
     */
    actual fun toPlatform(obj: Any?, exporters: List<PlatformExporter>): Any? {
        TODO("Not yet implemented toPlatform")
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
    actual fun <T : Proxy> proxy(pobject: PlatformObject, klass: KClass<T>, doNotOverride: Boolean): T {
        val o = pobject.asDynamic()
        val sym = Symbols.of(klass)
        val p = o[sym]
        if (klass.isInstance(p)) return p.unsafeCast<T>()
        check(!doNotOverride || p !is Proxy) { "new proxy forbidden, because doNotOverride set" }
        val proxy = klass.createInstance()
        proxy.bind(pobject, sym)
        return proxy
    }

    /**
     * The [PlatformLogger].
     */
    @Suppress("SENSELESS_COMPARISON")
    actual val logger: PlatformLogger = object : PlatformLogger {
        // https://plv8.github.io/#-code-plv8-elog-code-
        private val plv8: dynamic = js("typeof plv8=='object' && typeof plv8.log=='function' ? plv8 : null")
        private val _DEBUG: dynamic = if (plv8 != null) js("DEBUG1") else null
        private val _INFO: dynamic = if (plv8 != null) js("INFO") else null
        private val _WARN: dynamic = if (plv8 != null) js("WARNING") else null
        private val _ERROR: dynamic = if (plv8 != null) js("ERROR") else null

        // https://developer.mozilla.org/en-US/docs/Web/API/console
        // TODO: When the argument is a scalar, directly concat, only leave objects as own arguments.
        //       So, we expect that ("Hello {}", "World") returns ["Hello World"] and not ["Hello ", "World"]!
        private fun toString(msg: String, vararg args: Any?): String {
            var r = ""
            var ai = 0
            val msg_arr = js("msg.split(/(?={})/g)")
            var v: dynamic
            var i = 0
            while (i < msg_arr.length.unsafeCast<Int>()) {
                val m = msg_arr[i].replace("%", "%%").unsafeCast<String>()
                if (m.startsWith("{}")) {
                    v = args[ai++]
                    if (v !== null && v !== undefined && (jsTypeOf(v.valueOf()) == "object")) {
                        r += toJSON(v) + m.substring(2)
                    } else {
                        r += v + m.substring(2)
                    }
                } else {
                    r += m;
                }
                i++
            }
            return r
        }

        override fun debug(msg: String, vararg args: Any?) {
            // TODO: Report compiler bug, and add FAQ to describe coding hints for JavaScript
            //       this compiles into `toString_1(this, msg, args.slice())`, but if called
            //       from JavaScript, and it implements an @JsExport, the user will call without
            //       arguments, which means that args is undefined, so the compiler need to
            //       adjust for this, and if something is explicitly exported to JavaScript,
            //       it needs to adjust, so at least something like:
            //       `toString_1(this, msg, args ? args.slice() : [])`
            val m = if (args == null || args.size == 0) msg else toString(msg, *args)
            if (plv8 != null) plv8.log(_DEBUG, m) else console.log(m)
        }

        override fun atDebug(msgFn: () -> String?) {
            val m = msgFn.invoke()
            if (plv8 != null) plv8.log(_DEBUG, m) else console.log(m)
        }

        override fun info(msg: String, vararg args: Any?) {
            val m = if (args == null || args.size == 0) msg else toString(msg, *args)
            if (plv8 != null) plv8.log(_INFO, m) else console.info(m)
        }

        override fun atInfo(msgFn: () -> String?) {
            val m = msgFn.invoke()
            if (plv8 != null) plv8.log(_INFO, m) else console.info(m)
        }

        override fun warn(msg: String, vararg args: Any?) {
            val m = if (args == null || args.size == 0) msg else toString(msg, *args)
            if (plv8 != null) plv8.log(_WARN, m) else console.info(m)
        }

        override fun atWarn(msgFn: () -> String?) {
            val m = msgFn.invoke()
            if (plv8 != null) plv8.log(_WARN, m) else console.info(m)
        }

        override fun error(msg: String, vararg args: Any?) {
            val m = if (args == null || args.size == 0) msg else toString(msg, *args)
            if (plv8 != null) plv8.log(_ERROR, m) else console.info(m)
        }

        override fun atError(msgFn: () -> String?) {
            val m = msgFn.invoke()
            if (plv8 != null) plv8.log(_ERROR, m) else console.info(m)
        }
    }

    /**
     * Creates a new thread-local. Should be stored only in a static immutable variable (`val`).
     * @param initializer An optional lambda to be invoked, when the thread-local is read for the first time.
     * @return The thread local.
     */

    actual fun <T> newThreadLocal(initializer: (() -> T)?): PlatformThreadLocal<T> {
        TODO("Not yet implemented newThreadLocal")
    }

    // TODO: Implement high resolution timer, when available (sadly, not in PLV8):
    //       https://developer.mozilla.org/en-US/docs/Web/API/Performance/now

    /**
     * Returns the current epoch milliseconds.
     * @return The current epoch milliseconds.
     */
    actual fun currentMillis(): Int64 = js("BigInt(Date.now())").unsafeCast<Int64>()

    /**
     * Returns the current epoch microseconds.
     * @return current epoch microseconds.
     */
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
    actual fun random(): Double = js("Math.random()").unsafeCast<Double>()

    private val MANTISSA_MASK = Int64(0x000f_ffff_ffff_ffffL)
    private val MANTISSA_LO_MASK = Int64(0x0000_0000_1fff_ffffL)

    /**
     * Tests if the given 64-bit floating point number can be converted into a 32-bit floating point number without losing information.
     * @param value The 64-bit floating point number.
     * @return _true_ if the given 64-bit float can be converted into a 32-bit one without losing information; _false_ otherwise.
     */
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
        val mantissa = view.getBigInt64(0).unsafeCast<Int64>() and MANTISSA_MASK
        return (mantissa and MANTISSA_LO_MASK) == I64_ZERO || mantissa == MANTISSA_MASK
    }

    private const val MIN_INT_VALUE_AS_DOUBLE = Int.MIN_VALUE.toDouble()
    private const val MAX_INT_VALUE_AS_DOUBLE = Int.MAX_VALUE.toDouble()

    /**
     * Tests if the given 64-bit floating point number can be converted into a 32-bit integer without losing information.
     * @param value The 64-bit floating point number.
     * @return _true_ if the given 64-bit float can be converted into a 32-bit integer without losing information; _false_ otherwise.
     */
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
    actual fun lz4Deflate(raw: ByteArray, offset: Int, size: Int): ByteArray {
        TODO("Not yet implemented lz4Deflate")
    }

    /**
     * Decompress bytes.
     * @param compressed The bytes to decompress.
     * @param bufferSize The amount of bytes that are decompressed, if unknown, set 0.
     * @param offset The offset of the first byte to decompress.
     * @param size The amount of bytes to decompress.
     * @return The inflated (decompress) bytes.
     */
    actual fun lz4Inflate(
        compressed: ByteArray,
        bufferSize: Int,
        offset: Int,
        size: Int
    ): ByteArray {
        TODO("Not yet implemented lz4Inflate")
    }

    /**
     * Compress bytes.
     * @param raw The bytes to compress.
     * @param offset The offset of the first byte to compress.
     * @param size The amount of bytes to compress.
     * @return The deflated (compressed) bytes.
     */
    actual fun gzipDeflate(raw: ByteArray, offset: Int, size: Int): ByteArray {
        TODO("Not yet implemented gzipDeflate")
    }

    /**
     * Decompress bytes.
     * @param compressed The bytes to decompress.
     * @param bufferSize The amount of bytes that are decompressed, if unknown, set 0.
     * @param offset The offset of the first byte to decompress.
     * @param size The amount of bytes to decompress.
     * @return The inflated (decompress) bytes.
     */
    actual fun gzipInflate(
        compressed: ByteArray,
        bufferSize: Int,
        offset: Int,
        size: Int
    ): ByteArray {
        TODO("Not yet implemented gzipInflate")
    }

    actual fun stackTrace(t: Throwable): String = t.stackTraceToString()

    init {
        initialize()
    }
}