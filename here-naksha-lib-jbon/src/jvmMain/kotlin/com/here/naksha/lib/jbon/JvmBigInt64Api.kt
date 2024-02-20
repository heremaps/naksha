package com.here.naksha.lib.jbon

class JvmBigInt64Api : BigInt64Api {

    override fun MAX_VALUE(): BigInt64 = BigInt64Max;
    override fun MIN_VALUE(): BigInt64  = BigInt64Min
    override fun ZERO(): BigInt64  = positiveBigInts[0]
    override fun MINUS_ONE(): BigInt64 = negativeBigInts[255]

    override fun eq(t: BigInt64, o: BigInt64): Boolean {
        check(t is JvmBigInt64 && o is JvmBigInt64)
        return t.value == o.value
    }

    override fun eqi(t: BigInt64, o: Int): Boolean {
        check(t is JvmBigInt64)
        return t.value == o.toLong()
    }

    override fun lt(t: BigInt64, o: BigInt64): Boolean {
        check(t is JvmBigInt64 && o is JvmBigInt64)
        return t.value < o.value
    }

    override fun lti(t: BigInt64, o: Int): Boolean {
        check(t is JvmBigInt64)
        return t.value < o
    }

    override fun lte(t: BigInt64, o: BigInt64): Boolean {
        check(t is JvmBigInt64 && o is JvmBigInt64)
        return t.value <= o.value
    }

    override fun ltei(t: BigInt64, o: Int): Boolean {
        check(t is JvmBigInt64)
        return t.value <= o
    }

    override fun gt(t: BigInt64, o: BigInt64): Boolean {
        check(t is JvmBigInt64 && o is JvmBigInt64)
        return t.value > o.value
    }

    override fun gti(t: BigInt64, o: Int): Boolean {
        check(t is JvmBigInt64)
        return t.value > o
    }

    override fun gte(t: BigInt64, o: BigInt64): Boolean {
        check(t is JvmBigInt64 && o is JvmBigInt64)
        return t.value >= o.value
    }

    override fun gtei(t: BigInt64, o: Int): Boolean {
        check(t is JvmBigInt64)
        return t.value >= o
    }

    override fun shr(t: BigInt64, bits: Int): BigInt64 {
        check(t is JvmBigInt64)
        return JvmBigInt64(t.value shr bits)
    }

    override fun ushr(t: BigInt64, bits: Int): BigInt64 {
        check(t is JvmBigInt64)
        return JvmBigInt64(t.value ushr bits)
    }

    override fun shl(t: BigInt64, bits: Int): BigInt64 {
        check(t is JvmBigInt64)
        return JvmBigInt64(t.value shl bits)
    }

    override fun add(t: BigInt64, o: BigInt64): BigInt64 {
        check(t is JvmBigInt64 && o is JvmBigInt64)
        return JvmBigInt64(t.value + o.value)
    }

    override fun addi(t: BigInt64, o: Int): BigInt64 {
        check(t is JvmBigInt64)
        return JvmBigInt64(t.value + o)
    }

    override fun addf(t: BigInt64, o: Double): BigInt64 {
        check(t is JvmBigInt64)
        return JvmBigInt64(t.value + o.toLong())
    }

    override fun sub(t: BigInt64, o: BigInt64): BigInt64 {
        check(t is JvmBigInt64 && o is JvmBigInt64)
        return JvmBigInt64(t.value - o.value)
    }

    override fun subi(t: BigInt64, o: Int): BigInt64 {
        check(t is JvmBigInt64)
        return JvmBigInt64(t.value - o)
    }

    override fun subf(t: BigInt64, o: Double): BigInt64 {
        check(t is JvmBigInt64)
        return JvmBigInt64(t.value - o.toLong())
    }

    override fun mul(t: BigInt64, o: BigInt64): BigInt64 {
        check(t is JvmBigInt64 && o is JvmBigInt64)
        return JvmBigInt64(t.value * o.value)
    }

    override fun muli(t: BigInt64, o: Int): BigInt64 {
        check(t is JvmBigInt64)
        return JvmBigInt64(t.value * o)
    }

    override fun mulf(t: BigInt64, o: Double): BigInt64 {
        check(t is JvmBigInt64)
        return JvmBigInt64(t.value * o.toLong())
    }

    override fun mod(t: BigInt64, o: BigInt64): BigInt64 {
        check(t is JvmBigInt64 && o is JvmBigInt64)
        return JvmBigInt64(t.value % o.value)
    }

    override fun modi(t: BigInt64, o: Int): BigInt64 {
        check(t is JvmBigInt64)
        return JvmBigInt64(t.value % o.toLong())
    }

    override fun div(t: BigInt64, o: BigInt64): BigInt64 {
        check(t is JvmBigInt64 && o is JvmBigInt64)
        return JvmBigInt64(t.value / o.value)
    }

    override fun divi(t: BigInt64, o: Int): BigInt64 {
        check(t is JvmBigInt64)
        return JvmBigInt64(t.value / o)
    }

    override fun divf(t: BigInt64, o: Double): BigInt64 {
        check(t is JvmBigInt64)
        return JvmBigInt64(t.value / o.toLong())
    }

    override fun and(t: BigInt64, o: BigInt64): BigInt64 {
        check(t is JvmBigInt64 && o is JvmBigInt64)
        return JvmBigInt64(t.value and o.value)
    }

    override fun or(t: BigInt64, o: BigInt64): BigInt64 {
        check(t is JvmBigInt64 && o is JvmBigInt64)
        return JvmBigInt64(t.value or o.value)
    }

    override fun xor(t: BigInt64, o: BigInt64): BigInt64 {
        check(t is JvmBigInt64 && o is JvmBigInt64)
        return JvmBigInt64(t.value xor o.value)
    }

    override fun inv(t: BigInt64): BigInt64 {
        check(t is JvmBigInt64)
        return JvmBigInt64(t.value.inv())
    }

    override fun intToBigInt64(value: Int): BigInt64 {
        return when (value) {
            in 0..255 -> positiveBigInts[value]
            in -256..-1 -> negativeBigInts[value + 256]
            else -> JvmBigInt64(value.toLong())
        }
    }

    override fun bigInt64ToInt(value: BigInt64): Int {
        check(value is JvmBigInt64)
        return value.value.toInt()
    }

    override fun longToBigInt64(value: Long): BigInt64 {
        return JvmBigInt64(value)
    }

    override fun bigInt64ToLong(value: BigInt64): Long {
        require(value is JvmBigInt64)
        return value.value
    }

    override fun doubleToBigInt64(value: Double, raw: Boolean): BigInt64 {
        return if (raw) JvmBigInt64(java.lang.Double.doubleToRawLongBits(value)) else JvmBigInt64(value.toLong())
    }

    override fun bigInt64ToDouble(value: BigInt64, raw: Boolean): Double {
        check(value is JvmBigInt64)
        return if (raw) java.lang.Double.longBitsToDouble(value.value) else value.value.toDouble()
    }

    override fun hashCodeOf(value: BigInt64): Int = value.hashCode()
}