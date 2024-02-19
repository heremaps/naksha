package com.here.naksha.lib.jbon

@JvmInline
value class JvmBigInt64(val value: Long) : BigInt64 {
    fun toByte(): Byte {
        return value.toByte()
    }

    fun toShort(): Short {
        return value.toShort()
    }

    fun toInt(): Int {
        return value.toInt()
    }

    fun toLong(): Long {
        return value
    }

    fun toFloat(): Float {
        return value.toFloat()
    }

    fun toDouble(): Double {
        return value.toDouble()
    }

    override fun toString() : String {
        return value.toString()
    }
}