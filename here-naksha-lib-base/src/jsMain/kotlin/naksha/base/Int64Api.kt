package naksha.base

internal class Int64Api {
    @OptIn(ExperimentalJsStatic::class)
    companion object {
        @JsStatic
        fun eq(t: JsInt64, o: JsInt64): Boolean = js("t == o").unsafeCast<Boolean>()
        @JsStatic
        fun eqi(t: JsInt64, o: Int): Boolean = js("t == o").unsafeCast<Boolean>()
        @JsStatic
        fun lt(t: JsInt64, o: JsInt64): Boolean = js("t < o").unsafeCast<Boolean>()
        @JsStatic
        fun lti(t: JsInt64, o: Int): Boolean = js("t < o").unsafeCast<Boolean>()
        @JsStatic
        fun lte(t: JsInt64, o: JsInt64): Boolean = js("t <= o").unsafeCast<Boolean>()
        @JsStatic
        fun ltei(t: JsInt64, o: Int): Boolean = js("t <= o").unsafeCast<Boolean>()
        @JsStatic
        fun gt(t: JsInt64, o: JsInt64): Boolean = js("t > o").unsafeCast<Boolean>()
        @JsStatic
        fun gti(t: JsInt64, o: Int): Boolean = js("t > o").unsafeCast<Boolean>()
        @JsStatic
        fun gte(t: JsInt64, o: JsInt64): Boolean = js("t >= o").unsafeCast<Boolean>()
        @JsStatic
        fun gtei(t: JsInt64, o: Int): Boolean = js("t >= o").unsafeCast<Boolean>()
        @JsStatic
        fun shr(self: JsInt64, bits: Int): JsInt64 = js("self >> BigInt(bits)").unsafeCast<JsInt64>()
        @JsStatic
        fun ushr(self: JsInt64, bits: Int): JsInt64 = js("BigInt.ushr(self, bits)").unsafeCast<JsInt64>()
        @JsStatic
        fun shl(self: JsInt64, bits: Int): JsInt64 = js("self << BigInt(bits)").unsafeCast<JsInt64>()
        @JsStatic
        fun add(self: JsInt64, o: JsInt64): JsInt64 = js("self + o").unsafeCast<JsInt64>()
        @JsStatic
        fun addi(t: JsInt64, o: Int): JsInt64 = js("t + BigInt(o)").unsafeCast<JsInt64>()
        @JsStatic
        fun sub(t: JsInt64, o: JsInt64): JsInt64 = js("t - BigInt(o)").unsafeCast<JsInt64>()
        @JsStatic
        fun subi(t: JsInt64, o: Int): JsInt64 = js("t - BigInt(o)").unsafeCast<JsInt64>()
        @JsStatic
        fun mul(t: JsInt64, o: JsInt64): JsInt64 = js("t * o").unsafeCast<JsInt64>()
        @JsStatic
        fun muli(t: JsInt64, o: Int): JsInt64 = js("t * BigInt(o)").unsafeCast<JsInt64>()
        @JsStatic
        fun mod(t: JsInt64, o: JsInt64): JsInt64 = js("t % o").unsafeCast<JsInt64>()
        @JsStatic
        fun modi(t: JsInt64, o: Int): JsInt64 = js("t % BigInt(o)").unsafeCast<JsInt64>()
        @JsStatic
        fun div(t: JsInt64, o: JsInt64): JsInt64 = js("t / o").unsafeCast<JsInt64>()
        @JsStatic
        fun divi(t: JsInt64, o: Int): JsInt64 = js("t / BigInt(o)").unsafeCast<JsInt64>()
        @JsStatic
        fun and(self: JsInt64, other: JsInt64): JsInt64 = js("BigInt.and(self,other)").unsafeCast<JsInt64>()
        @JsStatic
        fun or(self: JsInt64, other: JsInt64): JsInt64 = js("BigInt.or(self,other)").unsafeCast<JsInt64>()
        @JsStatic
        fun xor(self: JsInt64, other: JsInt64): JsInt64 = js("BigInt.xor(self,other)").unsafeCast<JsInt64>()
        @JsStatic
        fun inv(self: JsInt64): JsInt64 = js("BigInt.inv(self)").unsafeCast<JsInt64>()
    }
}