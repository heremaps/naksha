package naksha.base

import naksha.base.Platform.I64_INT_MASK
import naksha.base.Platform.I64_ZERO
import naksha.base.Platform._int64
import naksha.base.Platform.i64_arr

class JsInt64 internal constructor(): Int64 {
    override fun unaryPlus(): Int64 = this

    override fun unaryMinus(): Int64 {
        i64_arr[0] = js("this.valueOf()")
        return i64_arr[0].unsafeCast<Int64>()
    }

    override fun inc(): Int64 {
        i64_arr[0] = js("this.valueOf()")
        i64_arr[0]++
        return i64_arr[0].unsafeCast<Int64>()
    }

    override fun dec(): Int64 {
        i64_arr[0] = this
        i64_arr[0]--
        return i64_arr[0].unsafeCast<Int64>()
    }

    override fun plus(other: Any): Int64 {
        i64_arr[0] = this
        i64_arr[0] += _int64(other)
        return i64_arr[0].unsafeCast<Int64>()
    }

    override fun minus(other: Any): Int64 {
        i64_arr[0] = this
        i64_arr[0] -= _int64(other)
        return i64_arr[0].unsafeCast<Int64>()
    }

    override fun times(other: Any): Int64 {
        i64_arr[0] = this
        i64_arr[0] *= _int64(other)
        return i64_arr[0].unsafeCast<Int64>()
    }

    override fun div(other: Any): Int64 {
        i64_arr[0] = this
        i64_arr[0] /= _int64(other)
        return i64_arr[0].unsafeCast<Int64>()
    }

    override fun rem(other: Any): Int64 {
        i64_arr[0] = this
        i64_arr[0] %= _int64(other)
        return i64_arr[0].unsafeCast<Int64>()
    }

    override fun compareTo(other: Any?): Int {
        i64_arr[0] = this
        i64_arr[0] -= _int64(other)
        return if (i64_arr[0] == I64_ZERO) 0 else if (i64_arr[0] < I64_ZERO) -1 else 1
    }

    override fun equals(other: Any?): Boolean = js("this.valueOf()") === _int64(other)

    override fun eq(other: Any?): Boolean = js("this.valueOf()") === _int64(other)

    override fun shr(bits: Int): Int64 = js("BigInt.asIntN(64, this.valueOf() >> BigInt(bits))").unsafeCast<Int64>()

    override fun ushr(bits: Int): Int64 = js("BigInt.asUintN(64, this.valueOf() >> BigInt(bits))").unsafeCast<Int64>()

    override fun shl(bits: Int): Int64 = js("BigInt.asIntN(64, this.valueOf() << BigInt(bits))").unsafeCast<Int64>()

    override fun and(other: Int64): Int64 =
        js("BigInt.asIntN(64, BigInt.asUintN(64,this.valueOf()) & BigInt.asUintN(64,other.valueOf()))").unsafeCast<Int64>()

    override fun or(other: Int64): Int64 =
        js("BigInt.asIntN(64, BigInt.asUintN(64,this.valueOf()) | BigInt.asUintN(64,other.valueOf()))").unsafeCast<Int64>()

    override fun xor(other: Int64): Int64 =
        js("BigInt.asIntN(64, BigInt.asUintN(64,this.valueOf()) ^ BigInt.asUintN(64,other.valueOf()))").unsafeCast<Int64>()

    override fun inv(): Int64 = js("BigInt.asIntN(64, ~(BigInt.asUintN(64,this.valueOf())))").unsafeCast<Int64>()

    override fun toByte(): Byte = js("Number(BigInt.asIntN(8,this.valueOf()))").unsafeCast<Byte>()

    override fun toShort(): Short = js("Number(BigInt.asIntN(16,this.valueOf()))").unsafeCast<Short>()

    override fun toInt(): Int = js("Number(BigInt.asIntN(32,this.valueOf()))").unsafeCast<Int>()

    override fun toLong(): Long = Platform.int64ToLong(this)

    override fun toFloat(): Float = js("Number(this.valueOf())").unsafeCast<Float>()

    override fun toDouble(): Double = js("Number(this.valueOf())").unsafeCast<Double>()

    override fun toDoubleRawBits(): Double = Platform.toDoubleRawBits(this)

    override fun hashCode(): Int {
        val u: dynamic = js("BigInt.asUintN(64, this.valueOf())")
        val lo: dynamic = u and I64_INT_MASK
        val hi: dynamic = js("BigInt.asUintN(64, u >> BigInt(32))")
        val r: dynamic = hi xor lo
        return js("BigInt.asUintN(32,r)").unsafeCast<Int>()
    }
    // var u=BigInt.u64(t); return BigInt.s32((u >> BigInt(32)) ^ (u & BigInt.MASK_LO_32));
}