@file:OptIn(ExperimentalJsExport::class)

package com.here.naksha.lib.jbon;

/**
 * Javascript environment.
 */
@Suppress("unused", "UnsafeCastFromDynamic", "NON_EXPORTABLE_TYPE", "UNUSED_VARIABLE")
@JsExport
open class JsEnv : IEnv {

    companion object {
        /**
         * Initializes the environment.
         */
        fun initialize() {
            if (!Jb.isInitialized()) {
                // Note: The definition of the MAX64 and MIN64 is just necessary, because Kotlin
                //       does not understand the simple notation 9223372036854775807n and alike.
                // (~(-9223372036854775808n-1n)) - 9223372036854775808n
                var jsBigInt = JsBigInt64()
                js("""
Object.assign(DataView.prototype, {
    getByteArray: function() { return new Int8Array(this.buffer); },
    getStart: function() { return this.byteOffset; },
    getEnd: function() { return this.byteOffset + this.byteLength; },
    getSize: function() { return this.byteLength; }
})
Object.assign(BigInt.prototype, Object.getPrototypeOf(jsBigInt));
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
                Jb.initialize(JsEnv(), JsMapApi(), JsBigInt64Api(), BrowserLog())
            }
        }

        /**
         * Returns the current environment, if it is not yet initialized, initializes it.
         * @return The environment.
         */
        fun get() : JsEnv {
            if (!Jb.isInitialized()) initialize()
            return Jb.env as JsEnv
        }

        private val globalDictionaries = HashMap<String, JbDict>()
        private var convertView: IDataView? = null
    }

    override fun stringify(any: Any, pretty: Boolean): String {
        return js("JSON.stringify(any, pretty)")
    }

    override fun parse(json: String): Any {
        return js("JSON.parse(json)")
    }

    override fun canBeFloat32(value: Double): Boolean {
        // IEEE-754, 32-bit = One sign-bit, 8-bit exponent biased by 127, then 23-bit mantissa
        // IEEE-754, 64-bit = One sign-bit, 11-bit exponent biased by 1023, then 52-bit mantissa
        // E = 0 means denormalized number (M>0) or null (M=0)
        // E = 255|2047 means either endless (M=0) or not a number (M>0)
        val view = view()
        view.setFloat64(0, value)
        var exponent = (view.getInt16(0).toInt() ushr 4) and 0x7ff
        if (exponent == 0 || exponent == 2047) return false
        // Remove bias: -1023 (0) .. 1024 (2047)
        exponent -= 1023
        // 32-bit exponent is 8-bit with bias 127: -127 (0) .. 128 (255)
        // We want to avoid extremes as they encode special states.
        if (exponent < -126 || exponent > 127) return false
        // We do not want to lose precision in mantissa either.
        // Either the lower 29-bit of mantissa are zero (only 23-bit used) or all bits are set.
        val mantissaHi = view.getInt32(0) and 0x000f_ffff
        val mantissaLo = view.getInt32(4)
        return mantissaLo and 0x1fff_ffff == 0 || (mantissaHi == 0x000f_ffff && mantissaLo == 0xffff_ffffu.toInt())
    }

    override fun newThreadLocal(): IThreadLocal {
        return JsThreadLocal()
    }

    override fun currentMillis(): BigInt64 {
        return js("BigInt(Date.now())")
    }

    override fun random(): Double {
        return js("Math.random()")
    }

    internal fun view() : IDataView {
        var view = convertView
        if (view == null) {
            view = get().newDataView(ByteArray(16))
            convertView = view
        }
        return view
    }

    override fun newDataView(bytes: ByteArray, offset: Int, size: Int): IDataView {
        // If invoked from javascript, arguments may be undefined, fix this!
        js("if (!offset) offset = 0; if (!size) size = bytes.byteLength;")
        val end = endOf(bytes, offset, size)
        val length = end - offset
        return js("new DataView(bytes.buffer, offset, length)")
    }

    override fun lz4Deflate(raw: ByteArray, offset: Int, size: Int): ByteArray {
        throw NotImplementedError("lz4Deflate")
    }

    override fun lz4Inflate(compressed: ByteArray, bufferSize: Int, offset: Int, size: Int): ByteArray {
        throw NotImplementedError("lz4Inflate")
    }

    override fun putGlobalDictionary(dict: JbDict) {
        val id = dict.id()
        check(id != null)
        globalDictionaries[id] = dict
    }

    override fun removeGlobalDictionary(dict: JbDict) {
        val id = dict.id()
        check(id != null)
        if (globalDictionaries[id] === dict) {
            globalDictionaries.remove(id)
        }
    }

    override fun getGlobalDictionary(id: String): JbDict? {
        return globalDictionaries[id]
    }
}