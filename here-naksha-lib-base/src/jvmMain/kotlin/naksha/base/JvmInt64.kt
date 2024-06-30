package naksha.base

/**
 * The JVM Int64 implementation. If an instance is needed use [Platform.longToInt64], otherwise create an instance and let the compiler
 * eliminate it.
 */
class JvmInt64(private val value: Long) : Number(), Int64 {
    @Suppress("NOTHING_TO_INLINE")
    private inline fun l(lo: Any?): Long = if (lo is Number) lo.toLong() else throw IllegalArgumentException("Number expected")
    @Suppress("NOTHING_TO_INLINE")
    private inline fun l(i: Int): Long = i.toLong()

    override fun unaryPlus(): Int64 = this
    override fun unaryMinus(): Int64 = JvmInt64(-value)
    override fun inc(): Int64 = JvmInt64(value + 1)
    override fun dec(): Int64 = JvmInt64(value - 1)
    override fun plus(other: Any): Int64 = JvmInt64(value + l(other))
    override fun minus(other: Any): Int64 = JvmInt64(value - l(other))
    override fun times(other: Any): Int64 = JvmInt64(value * l(other))
    override fun div(other: Any): Int64 = JvmInt64(value / l(other))
    override fun rem(other: Any): Int64 = JvmInt64(value % l(other))
    override fun compareTo(other: Any?): Int = value.compareTo(l(other))
    override fun shr(bits: Int): Int64 = JvmInt64(value shr bits)
    override fun ushr(bits: Int): Int64 = JvmInt64(value ushr bits)
    override fun shl(bits: Int): Int64 = JvmInt64(value shl bits)
    override fun and(other: Int64): Int64 = JvmInt64(value and l(other))
    override fun or(other: Int64): Int64 = JvmInt64(value or l(other))
    override fun xor(other: Int64): Int64 = JvmInt64(value xor l(other))
    override fun inv(): Int64 = JvmInt64(value.inv())
    override fun toByte(): Byte = value.toByte()
    override fun toShort(): Short = value.toShort()
    override fun toInt(): Int = value.toInt()
    override fun toLong(): Long = value
    override fun toFloat(): Float = value.toFloat()
    override fun toDouble(): Double = value.toDouble()
    override fun toDoubleRawBits(): Double = java.lang.Double.longBitsToDouble(value)

    override infix fun eq(other: Any?): Boolean = equals(other)
    override fun equals(other: Any?): Boolean {
        require(other is Number)
        return value == other.toLong()
    }
    override fun hashCode(): Int = (value ushr 32).toInt() xor value.toInt()
    override fun toString(): String = value.toString()
}