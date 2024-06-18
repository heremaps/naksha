
//package com.here.naksha.lib.jbon
//
//@Suppress("UnsafeCastFromDynamic")
//class JsBigInt64Api : BigInt64Api {
//    override fun MAX_VALUE(): BigInt64 = js("BigInt.MAX_VALUE_64")
//    override fun MIN_VALUE(): BigInt64 = js("BigInt.MIN_VALUE_64")
//    override fun ZERO(): BigInt64 = js("BigInt.ZERO")
//    override fun MINUS_ONE(): BigInt64 = js("BigInt.MINUS_ONE")
//
//    override fun intToBigInt64(value: Int): BigInt64 = js("BigInt(value)")
//    override fun bigInt64ToInt(value: BigInt64): Int = js("Number(value)")
//    override fun eq(t: BigInt64, o: BigInt64): Boolean = js("t == o")
//    override fun eqi(t: BigInt64, o: Int): Boolean = js("t == o")
//    override fun lt(t: BigInt64, o: BigInt64): Boolean = js("t < o")
//    override fun lti(t: BigInt64, o: Int): Boolean = js("t < o")
//    override fun lte(t: BigInt64, o: BigInt64): Boolean = js("t <= o")
//    override fun ltei(t: BigInt64, o: Int): Boolean = js("t <= o")
//    override fun gt(t: BigInt64, o: BigInt64): Boolean = js("t > o")
//    override fun gti(t: BigInt64, o: Int): Boolean = js("t > o")
//    override fun gte(t: BigInt64, o: BigInt64): Boolean = js("t >= o")
//    override fun gtei(t: BigInt64, o: Int): Boolean = js("t >= o")
//    override fun shr(t: BigInt64, bits: Int): BigInt64 = js("t >> BigInt(bits)")
//    override fun ushr(t: BigInt64, bits: Int): BigInt64 = js("BigInt.ushr(t, bits)")
//    override fun shl(t: BigInt64, bits: Int): BigInt64 = js("t << BigInt(bits)")
//    override fun add(t: BigInt64, o: BigInt64): BigInt64 = js("t + o")
//    override fun addi(t: BigInt64, o: Int): BigInt64 = js("t + BigInt(o)")
//    override fun addf(t: BigInt64, o: Double): BigInt64 = js("t + BigInt(o)")
//    override fun sub(t: BigInt64, o: BigInt64): BigInt64 = js("t - BigInt(o)")
//    override fun subi(t: BigInt64, o: Int): BigInt64 = js("t - BigInt(o)")
//    override fun subf(t: BigInt64, o: Double): BigInt64 = js("t - BigInt(o)")
//    override fun mul(t: BigInt64, o: BigInt64): BigInt64 = js("t * o")
//    override fun muli(t: BigInt64, o: Int): BigInt64 = js("t * BigInt(o)")
//    override fun mulf(t: BigInt64, o: Double): BigInt64 = js("t * BigInt(o)")
//    override fun mod(t: BigInt64, o: BigInt64): BigInt64 = js("t % o")
//    override fun modi(t: BigInt64, o: Int): BigInt64 = js("t % BigInt(o)")
//    override fun div(t: BigInt64, o: BigInt64): BigInt64 = js("t / o")
//    override fun divi(t: BigInt64, o: Int): BigInt64 = js("t / BigInt(o)")
//    override fun divf(t: BigInt64, o: Double): BigInt64 = js("t / BigInt(o)")
//    override fun and(t: BigInt64, o: BigInt64): BigInt64 = js("BigInt.and(t,o)")
//    override fun or(t: BigInt64, o: BigInt64): BigInt64 = js("BigInt.or(t,o)")
//    override fun xor(t: BigInt64, o: BigInt64): BigInt64 = js("BigInt.xor(t,o)")
//    override fun inv(t: BigInt64): BigInt64 = js("BigInt.inv(t)")
//    override fun longToBigInt64(value: Long): BigInt64 {
//        val view = JsEnv.get().view()
//        view.setInt32(0, (value ushr 32).toInt())
//        view.setInt32(4, value.toInt())
//        return js("view.getBigInt64(0)")
//    }
//    override fun bigInt64ToLong(value: BigInt64): Long {
//        val view = JsEnv.get().view()
//        js("view.setBigInt64(0, value)")
//        val hi = view.getInt32(0)
//        val lo = view.getInt32(4)
//        return ((hi.toLong() and 0xffff_ffff) shl 32) or (lo.toLong() and 0xffff_ffff)
//    }
//    override fun doubleToBigInt64(value: Double, raw: Boolean): BigInt64 {
//        if (raw) {
//            val view = JsEnv.get().view()
//            js("view.setFloat64(0, value)")
//            return js("view.getBigInt64(0)")
//        }
//        return js("BigInt(Math.floor(value))")
//    }
//    override fun bigInt64ToDouble(value: BigInt64, raw: Boolean): Double {
//        if (raw) {
//            val view = JsEnv.get().view()
//            js("view.setBigInt64(0, value)")
//            return js("view.getFloat64(0)")
//        }
//        return js("Number(value)")
//    }
//    override fun hashCodeOf(v: BigInt64): Int = js("BigInt.hashCode(v)")
//}