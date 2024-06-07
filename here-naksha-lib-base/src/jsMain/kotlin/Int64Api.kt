package com.here.naksha.lib.base

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
internal actual class Int64Api {
    @OptIn(ExperimentalJsStatic::class)
    actual companion object {
        @JsStatic
        fun eq(t: Int64, o: Int64): Boolean = js("t == o").unsafeCast<Boolean>()
        @JsStatic
        fun eqi(t: Int64, o: Int): Boolean = js("t == o").unsafeCast<Boolean>()
        @JsStatic
        fun lt(t: Int64, o: Int64): Boolean = js("t < o").unsafeCast<Boolean>()
        @JsStatic
        fun lti(t: Int64, o: Int): Boolean = js("t < o").unsafeCast<Boolean>()
        @JsStatic
        fun lte(t: Int64, o: Int64): Boolean = js("t <= o").unsafeCast<Boolean>()
        @JsStatic
        fun ltei(t: Int64, o: Int): Boolean = js("t <= o").unsafeCast<Boolean>()
        @JsStatic
        fun gt(t: Int64, o: Int64): Boolean = js("t > o").unsafeCast<Boolean>()
        @JsStatic
        fun gti(t: Int64, o: Int): Boolean = js("t > o").unsafeCast<Boolean>()
        @JsStatic
        fun gte(t: Int64, o: Int64): Boolean = js("t >= o").unsafeCast<Boolean>()
        @JsStatic
        fun gtei(t: Int64, o: Int): Boolean = js("t >= o").unsafeCast<Boolean>()
        @JsStatic
        actual fun shr(self: Int64, bits: Int): Int64 = js("self >> BigInt(bits)").unsafeCast<Int64>()
        @JsStatic
        actual fun ushr(self: Int64, bits: Int): Int64 = js("BigInt.ushr(self, bits)").unsafeCast<Int64>()
        @JsStatic
        actual fun shl(self: Int64, bits: Int): Int64 = js("self << BigInt(bits)").unsafeCast<Int64>()
        @JsStatic
        fun add(self: Int64, o: Int64): Int64 = js("self + o").unsafeCast<Int64>()
        @JsStatic
        fun addi(t: Int64, o: Int): Int64 = js("t + BigInt(o)").unsafeCast<Int64>()
        @JsStatic
        fun sub(t: Int64, o: Int64): Int64 = js("t - BigInt(o)").unsafeCast<Int64>()
        @JsStatic
        fun subi(t: Int64, o: Int): Int64 = js("t - BigInt(o)").unsafeCast<Int64>()
        @JsStatic
        fun mul(t: Int64, o: Int64): Int64 = js("t * o").unsafeCast<Int64>()
        @JsStatic
        fun muli(t: Int64, o: Int): Int64 = js("t * BigInt(o)").unsafeCast<Int64>()
        @JsStatic
        fun mod(t: Int64, o: Int64): Int64 = js("t % o").unsafeCast<Int64>()
        @JsStatic
        fun modi(t: Int64, o: Int): Int64 = js("t % BigInt(o)").unsafeCast<Int64>()
        @JsStatic
        fun div(t: Int64, o: Int64): Int64 = js("t / o").unsafeCast<Int64>()
        @JsStatic
        fun divi(t: Int64, o: Int): Int64 = js("t / BigInt(o)").unsafeCast<Int64>()
        @JsStatic
        actual fun and(self: Int64, other: Int64): Int64 = js("BigInt.and(self,other)").unsafeCast<Int64>()
        @JsStatic
        actual fun or(self: Int64, other: Int64): Int64 = js("BigInt.or(self,other)").unsafeCast<Int64>()
        @JsStatic
        actual fun xor(self: Int64, other: Int64): Int64 = js("BigInt.xor(self,other)").unsafeCast<Int64>()
        @JsStatic
        actual fun inv(self: Int64): Int64 = js("BigInt.inv(self)").unsafeCast<Int64>()
        actual fun unaryPlus(self: Int64): Int64 {
            TODO("Not yet implemented")
        }

        actual fun unaryMinus(self: Int64): Int64 {
            TODO("Not yet implemented")
        }

        actual fun inc(self: Int64): Int64 {
            TODO("Not yet implemented")
        }

        actual fun dec(self: Int64): Int64 {
            TODO("Not yet implemented")
        }

        actual fun plus(self: Int64, other: Any): Int64 {
            TODO("Not yet implemented")
        }

        actual fun minus(self: Int64, other: Any): Int64 {
            TODO("Not yet implemented")
        }

        actual fun times(self: Int64, other: Any): Int64 {
            TODO("Not yet implemented")
        }

        actual fun div(self: Int64, other: Any): Int64 {
            TODO("Not yet implemented")
        }

        actual fun rem(self: Int64, other: Any): Int64 {
            TODO("Not yet implemented")
        }

        actual fun compareTo(self: Int64, other: Any?): Int {
            TODO("Not yet implemented")
        }

        actual fun equals(self: Int64, other: Any?): Boolean {
            TODO("Not yet implemented")
        }
    }
}