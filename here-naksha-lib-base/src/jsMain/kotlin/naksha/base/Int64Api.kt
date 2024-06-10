package naksha.base

internal class Int64Api {
    @OptIn(ExperimentalJsStatic::class)
    companion object {
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
        fun shr(self: Int64, bits: Int): Int64 = js("self >> BigInt(bits)").unsafeCast<Int64>()
        @JsStatic
        fun ushr(self: Int64, bits: Int): Int64 = js("BigInt.ushr(self, bits)").unsafeCast<Int64>()
        @JsStatic
        fun shl(self: Int64, bits: Int): Int64 = js("self << BigInt(bits)").unsafeCast<Int64>()
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
        fun and(self: Int64, other: Int64): Int64 = js("BigInt.and(self,other)").unsafeCast<Int64>()
        @JsStatic
        fun or(self: Int64, other: Int64): Int64 = js("BigInt.or(self,other)").unsafeCast<Int64>()
        @JsStatic
        fun xor(self: Int64, other: Int64): Int64 = js("BigInt.xor(self,other)").unsafeCast<Int64>()
        @JsStatic
        fun inv(self: Int64): Int64 = js("BigInt.inv(self)").unsafeCast<Int64>()
    }
}