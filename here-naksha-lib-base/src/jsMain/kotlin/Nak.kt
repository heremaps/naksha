@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING", "OPT_IN_USAGE", "unused", "UNUSED_VARIABLE")

package com.here.naksha.lib.nak

@Suppress("MemberVisibilityCanBePrivate", "ACTUAL_ANNOTATIONS_NOT_MATCH_EXPECT")
@JsExport
actual class Nak {
    actual companion object {
        private var isInitialized: Boolean = false

        val arrayTemplate = object : PArray {}
        val objectTemplate = object : PObject {}
        val symbolTemplate = object : PSymbol {}
        val bigIntTemplate = object : Int64 {
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

        actual fun initNak(vararg parameters: Any?): Boolean {
            if (!isInitialized) {
                isInitialized = true
                copy(arrayTemplate, js("[]").unsafeCast<Any>())
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

        actual val NAK_SYM = symbol("com.here.naksha.lib.nak")
        actual val undefined: Any = js("undefined").unsafeCast<Any>()
        actual val INT64_MAX_VALUE: Int64 = js("BigInt('9223372036854775807')").unsafeCast<Int64>()
        actual val INT64_MIN_VALUE: Int64 = js("BigInt('9223372036854775808')").unsafeCast<Int64>()
        actual val MAX_SAFE_INT: Double = 9007199254740991.0
        actual val MIN_SAFE_INT: Double = -9007199254740991.0

        actual fun <P, T : NakType<P>> type(o: P, symbol: PSymbol): T? = js("o ? o[symbol] : undefined").unsafeCast<T?>()

        @Suppress("UNUSED_VARIABLE")
        actual fun <P, T : NakType<P>> cast(o: P, klass: NakClass<P, T>): T {
            var sym = klass.symbol()
            var t: Any? = js("o[sym]")
            if (klass.isInstance(t)) return t.unsafeCast<T>()
            require(klass.canCast(o))
            t = klass.create(o)
            js("o[sym]=t")
            return t
        }

        @Suppress("UNUSED_VARIABLE")
        actual fun <P, T : NakType<P>> force(o: P, klass: NakClass<P, T>): T {
            var sym = klass.symbol()
            var t: Any? = js("o[sym]")
            if (klass.isInstance(t)) return t.unsafeCast<T>()
            //require(klass.canCast(o)) <-- only difference to cast!
            t = klass.create(o)
            js("o[sym]=t")
            return t
        }

        actual fun canCast(o: Any?, klass: NakClass<*, *>): Boolean = klass.canCast(o)

        actual fun symbol(key: String): PSymbol = js("Symbol.for(key)").unsafeCast<PSymbol>()

        @Suppress("UNUSED_VARIABLE")
        actual fun newObject(vararg entries: Any?): PObject {
            val o = js("{}").unsafeCast<PObject>()
            if (entries.isNotEmpty()) {
                var i = 0
                while (i < entries.size) {
                    val key = entries[i++]
                    val value = if (i < entries.size) entries[i++] else null
                    js("o[key]=value")
                }
            }
            return o
        }

        actual fun newArray(vararg entries: Any?): PArray {
            val a = js("[]").unsafeCast<PArray>()
            if (entries.isNotEmpty()) {
                var i = 0
                while (i < entries.size) {
                    js("o[i]=value")
                    i++
                }
            }
            return a
        }

        actual fun newByteArray(size: Int): ByteArray = ByteArray(size)

        actual fun newDataView(byteArray: ByteArray, offset: Int, size: Int): PDataView = js("""
offset = offset ? Math.ceil(offset) : 0;
size = size ? Math.floor(size) : byteArray.byteLength - offset;
return new DataView(byteArray.buffer, offset, size);
""").unsafeCast<PDataView>()

        actual fun unbox(o: Any?): Any? = if (o is NakType<*>) o.data else o

        actual fun toInt(value: Any): Int = js("Number(value) >> 0").unsafeCast<Int>()

        actual fun toInt64(value: Any): Int64 = js("BigInt(value)").unsafeCast<Int64>()

        actual fun toDouble(value: Any): Double = js("Number(value)").unsafeCast<Double>()

        @Suppress("NON_EXPORTABLE_TYPE")
        actual fun longToInt64(value: Long): Int64 {
            val view = convertDV
            view.setInt32(0, (value ushr 32).toInt())
            view.setInt32(4, value.toInt())
            return js("view.getBigInt64(0)").unsafeCast<Int64>()
        }

        @Suppress("NON_EXPORTABLE_TYPE")
        actual fun int64ToLong(value: Int64): Long {
            val view = convertDV
            js("view.setBigInt64(0, value)")
            val hi = view.getInt32(0).unsafeCast<Int>()
            val lo = view.getInt32(4).unsafeCast<Int>()
            return ((hi.toLong() and 0xffff_ffff) shl 32) or (lo.toLong() and 0xffff_ffff).unsafeCast<Long>()
        }

        val convertDV = js("new DataView(new ArrayBuffer(16))")

        actual fun toInt64RawBits(d: Double): Int64 {
            val view = convertDV
            js("view.setFloat64(0, value)")
            return js("view.getInt64(0)").unsafeCast<Int64>()
        }

        actual fun toDoubleRawBits(i: Int64): Double {
            val view = convertDV
            js("view.setInt64(0, value)")
            return js("view.getFloat64(0)").unsafeCast<Double>()
        }

        actual fun isNative(o: Any?): Boolean = o !is NakType<*>

        actual fun isString(o: Any?): Boolean = o is String

        actual fun isNumber(o: Any?): Boolean = o is Number || isInt64(o)

        actual fun isInteger(o: Any?): Boolean = isInt(o) || isInt64(o)

        actual fun isDouble(o: Any?): Boolean = o is Number

        actual fun isInt(o: Any?): Boolean = js("Number.isInteger(o)").unsafeCast<Boolean>()

        actual fun isInt64(o: Any?): Boolean = js("typeof o === 'bigint'").unsafeCast<Boolean>() || o is Int64

        actual fun isObject(o: Any?): Boolean = o != null
                && !isNumber(o)
                && !isString(o)
                && !isArray(o)
                && !isSymbol(o)
                && !isByteArray(o)
                && !isDataView(o)

        actual fun isArray(o: Any?): Boolean = js("Array.isArray(o)").unsafeCast<Boolean>()

        actual fun isSymbol(o: Any?): Boolean = js("typeof o === 'symbol'").unsafeCast<Boolean>()

        actual fun isByteArray(o: Any?): Boolean = o is ByteArray

        actual fun isDataView(o: Any?): Boolean = o is PDataView

        actual fun has(o: Any?, key: Any?): Boolean = js("Object.hasOwn(o, key)").unsafeCast<Boolean>()

        actual fun get(o: Any, key: Any): Any? = js("o[key]")

        actual fun set(o: Any, key: Any, value: Any?): Any? = js("var old=o[key]; value===undefined ? delete o[key] : o[key]=value; old")

        actual fun delete(o: Any, key: Any): Any? = js("var old=o[key]; delete o[key]; old")

        actual fun objectIterator(o: PObject): PIterator<String,Any?> = JsObjectIterator(o)

        actual fun arrayIterator(o: PArray): PIterator<Int,Any?> = JsArrayIterator(o)

        actual fun keys(o: Any): Array<String> = js("var k=Object.keys(o); (Array.isArray(o) ? k.splice(o.length) : k)").unsafeCast<Array<String>>()

        actual fun symbols(o: Any): Array<PSymbol> = js("Object.getOwnPropertySymbols(o)").unsafeCast<Array<PSymbol>>()

        actual fun values(o: Any): Array<Any?> = js("var v=Object.values(o); if (Array.isArray(o)) v.splice(o.length,v.length); v").unsafeCast<Array<Any?>>()

        actual fun eq(t: Int64, o: Int64): Boolean = js("t == o").unsafeCast<Boolean>()
        actual fun eqi(t: Int64, o: Int): Boolean = js("t == o").unsafeCast<Boolean>()
        actual fun lt(t: Int64, o: Int64): Boolean = js("t < o").unsafeCast<Boolean>()
        actual fun lti(t: Int64, o: Int): Boolean = js("t < o").unsafeCast<Boolean>()
        actual fun lte(t: Int64, o: Int64): Boolean = js("t <= o").unsafeCast<Boolean>()
        actual fun ltei(t: Int64, o: Int): Boolean = js("t <= o").unsafeCast<Boolean>()
        actual fun gt(t: Int64, o: Int64): Boolean = js("t > o").unsafeCast<Boolean>()
        actual fun gti(t: Int64, o: Int): Boolean = js("t > o").unsafeCast<Boolean>()
        actual fun gte(t: Int64, o: Int64): Boolean = js("t >= o").unsafeCast<Boolean>()
        actual fun gtei(t: Int64, o: Int): Boolean = js("t >= o").unsafeCast<Boolean>()
        actual fun shr(t: Int64, bits: Int): Int64 = js("t >> BigInt(bits)").unsafeCast<Int64>()
        actual fun ushr(t: Int64, bits: Int): Int64 = js("BigInt.ushr(t, bits)").unsafeCast<Int64>()
        actual fun shl(t: Int64, bits: Int): Int64 = js("t << BigInt(bits)").unsafeCast<Int64>()
        actual fun add(t: Int64, o: Int64): Int64 = js("t + o").unsafeCast<Int64>()
        actual fun addi(t: Int64, o: Int): Int64 = js("t + BigInt(o)").unsafeCast<Int64>()
        actual fun sub(t: Int64, o: Int64): Int64 = js("t - BigInt(o)").unsafeCast<Int64>()
        actual fun subi(t: Int64, o: Int): Int64 = js("t - BigInt(o)").unsafeCast<Int64>()
        actual fun mul(t: Int64, o: Int64): Int64 = js("t * o").unsafeCast<Int64>()
        actual fun muli(t: Int64, o: Int): Int64 = js("t * BigInt(o)").unsafeCast<Int64>()
        actual fun mod(t: Int64, o: Int64): Int64 = js("t % o").unsafeCast<Int64>()
        actual fun modi(t: Int64, o: Int): Int64 = js("t % BigInt(o)").unsafeCast<Int64>()
        actual fun div(t: Int64, o: Int64): Int64 = js("t / o").unsafeCast<Int64>()
        actual fun divi(t: Int64, o: Int): Int64 = js("t / BigInt(o)").unsafeCast<Int64>()
        actual fun and(t: Int64, o: Int64): Int64 = js("BigInt.and(t,o)").unsafeCast<Int64>()
        actual fun or(t: Int64, o: Int64): Int64 = js("BigInt.or(t,o)").unsafeCast<Int64>()
        actual fun xor(t: Int64, o: Int64): Int64 = js("BigInt.xor(t,o)").unsafeCast<Int64>()
        actual fun inv(t: Int64): Int64 = js("BigInt.inv(t)").unsafeCast<Int64>()

        actual fun compare(a: Any?, b: Any?): Int {
            TODO("Fix me, see documentation!")
        }

        actual fun hashCodeOf(o: Any?): Int {
            if (o == null) return 0
            val S = NAK_SYM
            val nak : dynamic = o
            if (js("nak[S] && typeof nak[S].hashCode === 'function'").unsafeCast<Boolean>()) {
                try {
                    return nak[NAK_SYM].hashCode().unsafeCast<Int>()
                } catch (ignore: Throwable) {
                }
            }
            // TODO: Fix me, see documentation!
            return Fnv1a32.string(Fnv1a32.start(), nak.toString())
        }
    }
}