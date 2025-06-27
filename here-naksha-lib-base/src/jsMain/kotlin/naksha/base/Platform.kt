@file:Suppress("OPT_IN_USAGE", "EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package naksha.base

import naksha.base.PlatformMapApi.PlatformMapApi_C.map_get
import naksha.base.fn.Fn0
import naksha.base.fn.Fn1
import kotlin.math.round
import kotlin.reflect.KClass
import kotlin.reflect.KFunction

@JsExport
actual class Platform {
    actual companion object Platform_C {
        @JsStatic
        actual val isJvm: Boolean = false
        @JsStatic
        actual val isJs: Boolean = true

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

        @Suppress("UNUSED_PARAMETER")
        internal fun copyPrototypeToPrototype(source: Any, target: Any) = js("""
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
                if (key=="constructor") continue;
                if (tp.hasOwnProperty(key)) continue;
                desc.value = sp[key];
                Object.defineProperty(tp, key, desc);
            };"""
        )

        @JsStatic
        actual fun initialize(): Boolean {
            if (!isInitialized) {
                isInitialized = true
                // TODO: eval is really bad here, we need to replace it, but how? Kotlin really sucks with js(...) execution!
                js("""
                    var buffer = new ArrayBuffer(16);
                    var view = new DataView(buffer);
                    var pt = Object.getPrototypeOf(view);
                    pt.getInt64 = pt.getBigInt64;
                    pt.setInt64 = pt.setBigInt64;
                    Object.defineProperty(pt, '_byteArray', {
                        value: null,
                        enumerable: false,
                        writable: true
                    });
                    eval("Object.defineProperty(pt,'byteArray',{enumerable:true,get:function(){let v=this._byteArray;if(v instanceof Uint8Array)return v;v=new Uint8Array(this.buffer);this._byteArray=v;return v;}});");
                """)
                // This is the last eval:
                /*
                    Object.defineProperty(pt, 'byteArray', {
                        enumerable: true,
                        get() {
                           let v = this._byteArray;
                           if (v instanceof Uint8Array) return v;
                           v = new Uint8Array(this.buffer);
                           this._byteArray = v;
                           return v;
                        }
                    });
                 */
                val objectTemplate = object : PlatformObject {}
                val listTemplate = object : PlatformList {}
                val mapTemplate = object : PlatformMap {}
                val dataViewTemplate = object : PlatformDataView {
                    override val byteArray: ByteArray
                        get() = TODO()
                    override val byteLength: Int
                        get() = 0
                    override val byteOffset: Int
                        get() = 0
                    override fun getFloat32(byteOffset: Int, littleEndian: Boolean): Float = 0.0f
                    override fun setFloat32(byteOffset: Int, value: Float, littleEndian: Boolean) {}
                    override fun getFloat64(byteOffset: Int, littleEndian: Boolean): Double = 0.0
                    override fun setFloat64(byteOffset: Int, value: Double, littleEndian: Boolean) {}
                    override fun getInt8(byteOffset: Int): Byte = 0
                    override fun setInt8(byteOffset: Int, value: Byte) {}
                    override fun getInt16(byteOffset: Int, littleEndian: Boolean): Short = 0
                    override fun setInt16(byteOffset: Int, value: Short, littleEndian: Boolean) {}
                    override fun getInt32(byteOffset: Int, littleEndian: Boolean): Int = 0
                    override fun setInt32(byteOffset: Int, value: Int, littleEndian: Boolean) {}
                    override fun getInt64(byteOffset: Int, littleEndian: Boolean): Int64 = Int64(0)
                    override fun setInt64(byteOffset: Int, value: Int64, littleEndian: Boolean) {}
                }
                val symbolTemplate = object : Symbol {}
                val weakRefTemplate = object : WeakRef<Int> {
                    override fun deref(): Int = 0
                }

                copyPrototypeToPrototype(objectTemplate, js("{}").unsafeCast<Any>())
                copyPrototypeToPrototype(listTemplate, js("[]").unsafeCast<Any>())
                copyPrototypeToPrototype(mapTemplate, js("new Map()").unsafeCast<Any>())
                copyPrototypeToPrototype(dataViewTemplate, js("new DataView(new ArrayBuffer(0))").unsafeCast<Any>())

                copyPrototypeToPrototype(symbolTemplate, js("Symbol()").unsafeCast<Any>())
                copyPrototypeToPrototype(weakRefTemplate, js("new WeakRef(Object(0))").unsafeCast<Any>())
                copyPrototypeToPrototype(JsInt64(), js("BigInt(0)").unsafeCast<Any>())
                // Patch the Int64::class, so that it works as expected (it should only detect BigInt!)
                val i64Class = Int64::class
                js("""
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
                    };
                """)
                return true
            }
            return false
        }

        private val byConstructor: HashMap<JsClass<*>, JsPlatformType<*>> = HashMap()

        /**
         * Query the [JsPlatformType] instance for the given full qualified name.
         * @param name the full qualified name of a type.
         * @return the [JsPlatformType].
         * @since 3.0
         */
        @Suppress("UNCHECKED_CAST")
        @JsStatic
        actual fun <T: Any> forName(name: String): PlatformType<T>? {
            var pType: JsPlatformType<T>? = JsPlatformType.byName[name] as JsPlatformType<T>?
            if (pType != null) return pType
            pType = forNameAlias[name] as JsPlatformType<T>?
            if (pType != null) return pType

            @Suppress("CanBeVal") //
            var instance: T? = null
            js("""var i=0; var p=name.split("."); var k=globalThis;
while (k && i<p.length) k=k[p[i++]];
if (typeof k==='function') instance=Object.create(k.prototype);""")
            if (instance == null) return null
            pType = forInstance(instance).unsafeCast<JsPlatformType<T>>()
            pType.name = name
            return pType
        }

        @JsStatic
        actual fun forJsonType(jsonType: String?): PlatformTypeList {
            val all = JsPlatformType.byJsonType[jsonType]
            return if (all != null) PlatformTypeList(*all) else PlatformTypeList()
        }

        /**
         * A reflective method to find the first type that has in a JSON representation the property `type` set to the given value, and that is _(or implements)_ the given type.
         *
         * @param jsonType The value read from the `type` property of a JSON object.
         * @param type The [PlatformType] that is searched for.
         * @return either the first matching [PlatformType] or `null`, if no type matches.
         * @since 3.0
         */
        @JsStatic
        fun <T> forFirstJsonType(jsonType: String?, type: PlatformType<T>): PlatformType<T>? = forFirstJsonType(jsonType, type, null)

        @Suppress("UNCHECKED_CAST")
        @JsStatic
        actual fun <T> forFirstJsonType(jsonType: String?, type: PlatformType<T>, test: Fn1<Boolean, PlatformType<*>>?): PlatformType<T>? {
            val foundTypes = JsPlatformType.byJsonType[jsonType] ?: return null
            for (foundType in foundTypes) {
                if (foundType.isAssignableTo(type) && (test==null || test.call(foundType))) {
                    return foundType as PlatformType<T>
                }
            }
            return null
        }

        /**
         * Query the [JsPlatformType] instance for the given Kotlin class.
         * @param kClass the Kotlin class for which to return the [JsPlatformType].
         * @return the [JsPlatformType].
         * @since 3.0
         */
        @Suppress("NON_EXPORTABLE_TYPE")
        @JsStatic
        actual fun <T: Any> forKClass(kClass: KClass<T>): PlatformType<T> = forJsClass(kClass.js)

        /**
         * Query the [JsPlatformType] instance for the given JavaScript constructor.
         * @param constructor the JavaScript constructor for which to return the [JsPlatformType].
         * @return the [JsPlatformType].
         * @since 3.0
         */
        @Suppress("UNCHECKED_CAST")
        @JsStatic
        fun <T: Any> forJsClass(constructor: JsClass<T>): JsPlatformType<T> {
            var pType: JsPlatformType<T>? = byConstructor[constructor] as JsPlatformType<T>?
            if (pType != null) return pType
            pType = JsPlatformType(constructor)
            byConstructor[pType.jsClass] = pType
            return pType
        }

        /**
         * Query the [JsPlatformType] of the given instance.
         * @param instance the instance for which to return the [JsPlatformType].
         * @return the [JsPlatformType].
         * @since 3.0
         */
        @Suppress("UNCHECKED_CAST")
        @JsStatic
        actual fun <T: Any> forInstance(instance: T): PlatformType<T> {
            val constructor: JsClass<T> = js("instance.constructor").unsafeCast<JsClass<T>>()
            val pType: JsPlatformType<T>? = byConstructor[constructor] as JsPlatformType<T>?
            if (pType != null) return pType
            return forKClass(instance::class as KClass<T>)
        }

        private val IDENTITY_HASH_CODE_SYMBOL = Symbols.forName("identityHashCode")
        private var nextIdentityHashCode = 1

        @JsStatic
        actual fun identityHashCode(obj: Any?): Int {
            if (obj == null) return 0
            val primitive = unboxPrimitiveOrNull(obj)
            if (primitive != null) {
                val type = jsTypeOf(primitive)
                if (type == "boolean") return if (primitive.unsafeCast<Boolean>()) 1 else 0
                if (type == "number") return primitive.unsafeCast<Double>().toInt()
                if (type == "string") return primitive.unsafeCast<String>().hashCode()
                if (type == "bigint") return primitive.unsafeCast<Int64>().hashCode()
                if (type == "symbol") return primitive.unsafeCast<Symbol>().hashCode()
                throw generalException("Found unknown primitive type: $primitive")
            }
            var hashCode = obj.asDynamic()[IDENTITY_HASH_CODE_SYMBOL] as Int?
            if (hashCode == null) {
                hashCode = nextIdentityHashCode++
                obj.asDynamic()[IDENTITY_HASH_CODE_SYMBOL] = hashCode
            }
            return hashCode
        }

        @JsStatic
        actual val UNDEFINED: Any = js("undefined").unsafeCast<Any>()

        @JsStatic
        actual val DEFAULT_SYMBOL = js("Symbol.for(\"com.here.naksha\")").unsafeCast<Symbol>()

        @JsStatic
        actual val ITERATOR: Symbol = js("Symbol.iterator").unsafeCast<Symbol>()

        @JsStatic
        actual val INT64_MAX_VALUE: Int64 = js("BigInt('9223372036854775807')").unsafeCast<Int64>()

        @JsStatic
        actual val INT64_MIN_VALUE: Int64 = js("BigInt('-9223372036854775808')").unsafeCast<Int64>()

        @JsStatic
        actual val MAX_SAFE_INT: Double = 9007199254740991.0

        @JsStatic
        actual val MAX_SAFE_INT64: Int64 = js("BigInt('9007199254740991')").unsafeCast<Int64>()

        @JsStatic
        actual val MIN_SAFE_INT: Double = -9007199254740991.0

        @JsStatic
        actual val MIN_SAFE_INT64: Int64 = js("BigInt('-9007199254740991')").unsafeCast<Int64>()

        @JsStatic
        actual val EPSILON: Double = js("Number.EPSILON").unsafeCast<Double>()

        @JsStatic
        actual val forNameAlias: AtomicMap<String, PlatformType<*>> = AtomicMap()

        @JsStatic
        actual fun intern(s: String, cd: Boolean): String = js("(cd ? s.normalize('NFC') : s.normalize('NFKC'))").unsafeCast<String>()

        @JsStatic
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

        @JsStatic
        actual fun <K, V> newAtomicMap(): AtomicMap<K, V> = JsAtomicMap()

        @JsStatic
        actual fun <R: Any> newAtomicRef(startValue: R?): AtomicRef<R> = JsAtomicRef(startValue)

        @JsStatic
        actual fun <R: Any> newAtomicNonNullRef(startValue: R): AtomicNonNullRef<R> = JsAtomicNonNullRef(startValue)

        @JsStatic
        actual fun newAtomicBool(startValue: Boolean): AtomicBool = JsAtomicBool(startValue)

        @JsStatic
        actual fun newAtomicInt(startValue: Int): AtomicInt = JsAtomicInt(startValue)

        @JsStatic
        actual fun newAtomicInt64(startValue: Int64): AtomicInt64 = JsAtomicInt64(startValue)

        @JsStatic
        actual fun listOf(vararg entries: Any?): PlatformList {
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
        actual fun listOfArray(elements: Array<*>): PlatformList
            = elements.unsafeCast<PlatformList>()

        @JsStatic
        actual fun newList(capacity: Int): PlatformList = js("[]").unsafeCast<PlatformList>()

        @JsStatic
        actual fun newByteArray(size: Int): ByteArray = ByteArray(size)

        @JsStatic
        actual fun newDataView(byteArray: ByteArray, offset: Int, size: Int): PlatformDataView =
            js("new DataView(byteArray.buffer, offset, size)").unsafeCast<PlatformDataView>()

        // Note: Some values in JavaScript must be wrapped into an object (like string literals or numbers), however,
        //       not all numbers of string literals, so we only wrap, when really necessary!

        @JsStatic
        actual fun <T : Any> newWeakRef(referent: T): WeakRef<T> =
            js("try { return new WeakRef(referent); } catch(e) { return new WeakRef(Object(referent)); }").unsafeCast<WeakRef<T>>()

        /**
         * Creates a new reentrant lock.
         * @return the created reentrant lock.
         */
        @JsStatic
        actual fun newLock(): PlatformLock = JsLock()

        @JsStatic
        actual fun unbox(value: Any?): Any? {
            if (value == null) return null
            if (value is Proxy) return value.platformObject()
            if (value is JsEnum) return value.value
            return unboxPrimitive(value)
        }

        /**
         * Unbox primitive.
         *
         * @param value The value that should be unboxed.
         * @return the unboxed primitive, if [value] is a boxed primitive; [value] otherwise.
         * @since 3.0
         */
        internal fun unboxPrimitive(value: Any?): Any? {
            if (value is Long) return Int64(value) //
            return js(
                """switch (Object.prototype.toString.call(value)) {
case '[object BigInt]':  return BigInt.prototype.valueOf.call(value);
case '[object Number]':  return Number.prototype.valueOf.call(value);
case '[object String]':  return String.prototype.valueOf.call(value);
case '[object Boolean]': return Boolean.prototype.valueOf.call(value);
case '[object Symbol]':  return Symbol.prototype.valueOf.call(value);
default:                 return value;
}""")
        }

        /**
         * Unbox primitive.
         *
         * @param value The value that should be unboxed.
         * @return the unboxed primitive, if [value] is a boxed primitive; `null` otherwise.
         * @since 3.0
         */
        internal fun unboxPrimitiveOrNull(value: Any?): Any? {
            if (value is Long) return Int64(value) //
            return js(
                """switch (Object.prototype.toString.call(value)) {
case '[object BigInt]':  return BigInt.prototype.valueOf.call(value);
case '[object Number]':  return Number.prototype.valueOf.call(value);
case '[object String]':  return String.prototype.valueOf.call(value);
case '[object Boolean]': return Boolean.prototype.valueOf.call(value);
case '[object Symbol]':  return Symbol.prototype.valueOf.call(value);
default:                 return null;
}""")
        }

        @Suppress("ACTUAL_ANNOTATIONS_NOT_MATCH_EXPECT")
        @JsStatic
        actual fun <T> box(raw: Any?, type: PlatformType<T>, alternative: T?, init: Fn0<T?>?): T?
            = boxInto(raw, type, alternative, init)

        @JsStatic
        actual fun toInt(value: Any): Int = when (value) {
            is Long -> value.toInt()
            is Int64 -> value.toInt()
            is Number -> value.toInt()
            is String -> value.toInt()
            else -> throw IllegalArgumentException("Failed to convert object to int")
        }

        @JsStatic
        actual fun toInt64(value: Any): Int64 = when (value) {
            is Long -> longToInt64(value)
            is Int64 -> value
            is Byte, Short, Int -> js("BigInt(value)").unsafeCast<Int64>()
            is Float, Double -> js("BigInt(Math.round(value))").unsafeCast<Int64>()
            is String -> js("BigInt(value)").unsafeCast<Int64>()
            else -> throw IllegalArgumentException("Failed to convert object to int64")
        }

        @JsStatic
        actual fun toDouble(value: Any): Double = when (value) {
            is Long -> value.toDouble()
            is Int64 -> value.toDouble()
            is Number -> value.toDouble()
            is String -> value.toDouble()
            else -> throw IllegalArgumentException("Failed to convert object to double")
        }

        @JsStatic
        actual fun intToInt64(value: Int): Int64 = js("BigInt(value)").unsafeCast<Int64>()

        @JsStatic
        @Suppress("NON_EXPORTABLE_TYPE")
        actual fun longToInt64(value: Long): Int64 {
            val view = convertView
            val high32: Int = (value ushr 32).toInt()
            val low32: Int = value.toInt()
            view.setInt32(0, high32)
            view.setInt32(4, low32)
            val v = view.getBigInt64(0).unsafeCast<Int64>()
            return v
        }

        @JsStatic
        @Suppress("NON_EXPORTABLE_TYPE")
        actual fun int64ToLong(value: Int64): Long {
            val view = convertView
            view.setBigInt64(0, value)
            val hi = view.getInt32(0).unsafeCast<Int>()
            val lo = view.getInt32(4).unsafeCast<Int>()
            return ((hi.toLong() and 0xffff_ffff) shl 32) or (lo.toLong() and 0xffff_ffff).unsafeCast<Long>()
        }

        private val convertView: dynamic = js("new DataView(new ArrayBuffer(16))")

        @JsStatic
        actual fun toInt64RawBits(d: Double): Int64 {
            convertView.setFloat64(0, d)
            return convertView.getBigInt64(0).unsafeCast<Int64>()
        }

        @JsStatic
        actual fun toDoubleRawBits(i: Int64): Double {
            convertView.setBigInt64(0, i)
            return convertView.getFloat64(0).unsafeCast<Double>()
        }

        @JsStatic
        actual fun isNumber(o: Any?): Boolean =
            js("o && (typeof o.valueOf()==='number' || typeof o.valueOf()==='bigint')").unsafeCast<Boolean>()

        @JsStatic
        actual fun isPlatformObject(o: Any?) : Boolean {
            val value = unbox(o)
            return js("""(value !== null && (typeof value === 'object' && (
    Array.isArray(value) ||
    value instanceof Map ||
    value instanceof DataView ||
    Object.prototype.toString.call(value) === '[object Object]'
)))""").unsafeCast<Boolean>()
        }

        @JsStatic
        actual fun asPlatformObject(o: Any?): PlatformObject {
            if (o == null) throw illegalArg("Cannot cast null to PlatformObject")
            return o.unsafeCast<PlatformObject>()
        }

        @JsStatic
        actual fun isScalar(o: Any?): Boolean {
            if (o == null) return true
            return when (jsTypeOf(o.asDynamic().valueOf())) {
                "string", "number", "bigint", "boolean", "symbol" -> true
                else -> false
            }
        }

        @JsStatic
        actual fun isInteger(o: Any?): Boolean = js("o && (Number.isInteger(o) || typeof o.valueOf()==='bigint')").unsafeCast<Boolean>()

        @JsStatic
        actual fun isDouble(o: Any?): Boolean = o is Number

        @JsStatic
        actual fun compare(a: Any?, b: Any?): Int {
            TODO("Fix me, see documentation!")
        }

        @JsStatic
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

        /**
         * The KClass for [Any].
         */
        @Suppress("NON_EXPORTABLE_TYPE")
        @JsStatic
        actual val anyKlass: KClass<Any> = Any::class

        /**
         * The KClass for [Boolean].
         */
        @Suppress("NON_EXPORTABLE_TYPE")
        @JsStatic
        actual val booleanKlass: KClass<Boolean> = Boolean::class

        /**
         * The KClass for [Short].
         */
        @Suppress("NON_EXPORTABLE_TYPE")
        @JsStatic
        actual val shortKlass: KClass<Short> = Short::class

        /**
         * The KClass for [Int].
         */
        @Suppress("NON_EXPORTABLE_TYPE")
        @JsStatic
        actual val intKlass: KClass<Int> = Int::class

        /**
         * The KClass for [Int64].
         */
        @Suppress("NON_EXPORTABLE_TYPE")
        @JsStatic
        actual val int64Klass: KClass<Int64> = Int64::class

        /**
         * The KClass for [Double].
         */
        @Suppress("NON_EXPORTABLE_TYPE")
        @JsStatic
        actual val doubleKlass: KClass<Double> = Double::class

        /**
         * The KClass for [String].
         */
        @Suppress("NON_EXPORTABLE_TYPE")
        @JsStatic
        actual val stringKlass: KClass<String> = String::class

        /**
         * The KClass for [PlatformObject].
         */
        @Suppress("NON_EXPORTABLE_TYPE")
        @JsStatic
        actual val objectKlass: KClass<PlatformObject> = PlatformObject::class

        /**
         * The KClass for [PlatformList].
         */
        @Suppress("NON_EXPORTABLE_TYPE")
        @JsStatic
        actual val listKlass: KClass<PlatformList> = PlatformList::class

        /**
         * The KClass for [PlatformMap].
         */
        @Suppress("NON_EXPORTABLE_TYPE")
        @JsStatic
        actual val mapKlass: KClass<PlatformMap> = PlatformMap::class

        /**
         * The KClass for [PlatformDataViewApi].
         */
        @Suppress("NON_EXPORTABLE_TYPE")
        @JsStatic
        actual val dataViewKlass: KClass<PlatformDataView> = PlatformDataView::class

        /**
         * Tests if the given value is _null_ or _undefined_.
         * @param any The value to test.
         * @return _true_ if the value is _null_ or _undefined_; false otherwise.
         */
        @JsStatic
        actual fun isNil(any: Any?): Boolean = js("any===null || any===undefined").unsafeCast<Boolean>()

        @JsStatic
        actual fun <T> copy(obj: T?, recursive: Boolean): T? {
            if (obj == null) return null
            if (obj is JsonValue) return (if (recursive) obj.duplicate() else obj).unsafeCast<T>()
            return js("""
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
""").unsafeCast<T?>()
        }

        @JsName("toJson")
        @JsStatic
        actual fun toJson(obj: Any?): String = toJson(obj, ToJsonOptions.DEFAULT)

        @JsName("toJsonWithOptions")
        @JsStatic
        actual fun toJson(obj: Any?, options: ToJsonOptions): String {
            val o = unbox(obj)
            return js(
                """JSON.stringify(o, function(k, v) {
  if (!v) return v;
  if (v.valueOf() instanceof Map) return Object.fromEntries(v.valueOf().entries());
  if (typeof v.valueOf() === "bigint") return "data:bigint;dec,"+String(v);
  return v;
})"""
            ).unsafeCast<String>()
        }

        @JsStatic
        actual val globalDetectors: AtomicSet<TypeDetector> = AtomicSet(arrayOf())

        @JsStatic
        @Suppress("UNCHECKED_CAST")
        actual fun detectMap(map: PlatformMap, detectors: AtomicSet<TypeDetector>?): PlatformType<MapProxy<String,*>> {
            if (detectors != null) {
                val detected: PlatformType<MapProxy<String, *>>? = detectors.forEach(backwards = true) {
                    val t = it.detectMap(map)
                    if (t != null) AbortVisit.with(t)
                }
                if (detected != null) return detected
            }
            // Custom detectors where not provided or failed.

            // Run global detector.
            val globalDetectors = this.globalDetectors
            val detected: PlatformType<MapProxy<String, *>>? = globalDetectors.forEach(backwards = true) {
                val t = it.detectMap(map)
                if (t != null) AbortVisit.with(t)
            }
            if (detected != null) return detected
            // Global detection failed.

            // Perform standard detection using `type` property.
            val type_name = map_get(map, "type")
            if (type_name is String) {
                val all = JsPlatformType.byJsonType[type_name]
                if (!all.isNullOrEmpty()) {
                    var i = all.size
                    while (--i >= 0) {
                        val type = all[i]
                        if (type.isProxy() && type.isInstantiatable && type.isAssignableTo(MapProxy.TYPE)) {
                            return type as PlatformType<MapProxy<String, *>>
                        }
                    }
                }
            }
            // Nothing was available, return AnyObject.
            return AnyObject.TYPE as PlatformType<MapProxy<String,*>>
        }

        @JsStatic
        actual fun fromJson(json: String): Any?
            = fromJson(json, Any_TYPE, FromJsonOptions.DEFAULT)

        @JsName("fromJsonWithOptions")
        @JsStatic
        actual fun fromJson(json: String, options: FromJsonOptions): Any?
           = fromJson(json, Any_TYPE, options)

        @JsName("fromJsonAs")
        @JsStatic
        actual fun <T> fromJson(json: String, type: PlatformType<T>): T?
            = fromJson(json, type, FromJsonOptions.DEFAULT)

        @Suppress("UNCHECKED_CAST")
        @JsName("fromJsonAsWithOptions")
        @JsStatic
        actual fun <T> fromJson(json: String, type: PlatformType<T>, options: FromJsonOptions): T? {
            val raw = js(
                """JSON.parse(json, function(k, v) {
  if (!v) return v;
  if (typeof v === "string" && v.startsWith("data:bigint")) {
    var i = v.indexOf(",");
    return BigInt(v.substring(i+1));
  }
  if (!Array.isArray(v) && typeof v === "object") return new Map(Object.entries(v));
  return v;
})""").unsafeCast<Any?>()
            val detectors = options.detectors
            if (detectors != null && type == Any_TYPE && raw is PlatformMap) {
                return detectMap(raw, detectors).proxy(raw) as T
            }
            return box(raw, type)
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
        @JsStatic
        actual fun toPlatform(obj: Any?, exporters: List<PlatformExporter>): Any? {
            TODO("Not yet implemented toPlatform")
        }

        /**
         * The [PlatformLogger].
         */
        @JsStatic
        actual val logger: PlatformLogger = JsLogger()

        /**
         * Creates a new thread-local. Should be stored only in a static immutable variable (`val`).
         * @param initializer An optional lambda to be invoked, when the thread-local is read for the first time.
         * @return The thread local.
         */
        @JsStatic
        actual fun <T> newThreadLocal(initializer: Fn0<T?>?): PlatformThreadLocal<T> {
            return JsThreadLocal(initializer)
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
        @JsStatic
        actual fun currentNanos(): Int64 = js("BigInt(Date.now()*1000*1000)").unsafeCast<Int64>()

        /**
         * Generates a new random number between 0 and 1 (therefore with 53-bit random bits).
         * @return The new random number between 0 and 1.
         */
        @JsStatic
        actual fun random(): Double = js("Math.random()").unsafeCast<Double>()

        private val MANTISSA_MASK = Int64(0x000f_ffff_ffff_ffffL)
        private val MANTISSA_LO_MASK = Int64(0x0000_0000_1fff_ffffL)

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
         * @param text the text to hash.
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
            throw UnsupportedOperationException("gzipDeflate is not implemented in the current environment")
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
            throw UnsupportedOperationException("gzipInflate is not implemented in the current environment")
        }

        @JsStatic
        actual fun stackTrace(t: Throwable): String = t.stackTraceToString()

        @JsStatic
        actual fun normalize(value: String, form: NormalizerForm): String {
            val formString = form.name
            return js("value.normalize(formString)").unsafeCast<String>()
        }

        init {
            initialize()
        }
    }
}
