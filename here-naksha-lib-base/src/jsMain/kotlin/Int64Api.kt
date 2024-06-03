package com.here.naksha.lib.base

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
internal actual class Int64Api {
    @OptIn(ExperimentalJsStatic::class)
    actual companion object {
        @JsStatic
        actual fun eq(t: Int64, o: Int64): Boolean = js("t == o").unsafeCast<Boolean>()
        @JsStatic
        actual fun eqi(t: Int64, o: Int): Boolean = js("t == o").unsafeCast<Boolean>()
        @JsStatic
        actual fun lt(t: Int64, o: Int64): Boolean = js("t < o").unsafeCast<Boolean>()
        @JsStatic
        actual fun lti(t: Int64, o: Int): Boolean = js("t < o").unsafeCast<Boolean>()
        @JsStatic
        actual fun lte(t: Int64, o: Int64): Boolean = js("t <= o").unsafeCast<Boolean>()
        @JsStatic
        actual fun ltei(t: Int64, o: Int): Boolean = js("t <= o").unsafeCast<Boolean>()
        @JsStatic
        actual fun gt(t: Int64, o: Int64): Boolean = js("t > o").unsafeCast<Boolean>()
        @JsStatic
        actual fun gti(t: Int64, o: Int): Boolean = js("t > o").unsafeCast<Boolean>()
        @JsStatic
        actual fun gte(t: Int64, o: Int64): Boolean = js("t >= o").unsafeCast<Boolean>()
        @JsStatic
        actual fun gtei(t: Int64, o: Int): Boolean = js("t >= o").unsafeCast<Boolean>()
        @JsStatic
        actual fun shr(t: Int64, bits: Int): Int64 = js("t >> BigInt(bits)").unsafeCast<Int64>()
        @JsStatic
        actual fun ushr(t: Int64, bits: Int): Int64 = js("BigInt.ushr(t, bits)").unsafeCast<Int64>()
        @JsStatic
        actual fun shl(t: Int64, bits: Int): Int64 = js("t << BigInt(bits)").unsafeCast<Int64>()
        @JsStatic
        actual fun add(t: Int64, o: Int64): Int64 = js("t + o").unsafeCast<Int64>()
        @JsStatic
        actual fun addi(t: Int64, o: Int): Int64 = js("t + BigInt(o)").unsafeCast<Int64>()
        @JsStatic
        actual fun sub(t: Int64, o: Int64): Int64 = js("t - BigInt(o)").unsafeCast<Int64>()
        @JsStatic
        actual fun subi(t: Int64, o: Int): Int64 = js("t - BigInt(o)").unsafeCast<Int64>()
        @JsStatic
        actual fun mul(t: Int64, o: Int64): Int64 = js("t * o").unsafeCast<Int64>()
        @JsStatic
        actual fun muli(t: Int64, o: Int): Int64 = js("t * BigInt(o)").unsafeCast<Int64>()
        @JsStatic
        actual fun mod(t: Int64, o: Int64): Int64 = js("t % o").unsafeCast<Int64>()
        @JsStatic
        actual fun modi(t: Int64, o: Int): Int64 = js("t % BigInt(o)").unsafeCast<Int64>()
        @JsStatic
        actual fun div(t: Int64, o: Int64): Int64 = js("t / o").unsafeCast<Int64>()
        @JsStatic
        actual fun divi(t: Int64, o: Int): Int64 = js("t / BigInt(o)").unsafeCast<Int64>()
        @JsStatic
        actual fun and(t: Int64, o: Int64): Int64 = js("BigInt.and(t,o)").unsafeCast<Int64>()
        @JsStatic
        actual fun or(t: Int64, o: Int64): Int64 = js("BigInt.or(t,o)").unsafeCast<Int64>()
        @JsStatic
        actual fun xor(t: Int64, o: Int64): Int64 = js("BigInt.xor(t,o)").unsafeCast<Int64>()
        @JsStatic
        actual fun inv(t: Int64): Int64 = js("BigInt.inv(t)").unsafeCast<Int64>()
    }
}