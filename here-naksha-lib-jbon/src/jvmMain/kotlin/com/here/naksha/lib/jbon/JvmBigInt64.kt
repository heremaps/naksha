package com.here.naksha.lib.jbon

import kotlin.math.round

class JvmBigInt64(val value: Long) : Number(), BigInt64 {
    override fun equals(other: Any?) : Boolean {
        return when(other) {
            is Float -> {
                val r = round(other)
                return other == r && value == other.toLong()
            }
            is Double -> {
                val r = round(other)
                return other == r && value == other.toLong()
            }
            is Long -> return value == other
            is JvmBigInt64 -> return value == other.value
            is Number -> return value == other.toLong()
            else -> false
        }
    }

    override fun hashCode() : Int {
        return (value ushr 32).toInt() xor value.toInt()
    }

    override fun toByte(): Byte {
        return value.toByte()
    }

    override fun toShort(): Short {
        return value.toShort()
    }

    override fun toInt(): Int {
        return value.toInt()
    }

    override fun toLong(): Long {
        return value
    }

    override fun toFloat(): Float {
        return value.toFloat()
    }

    override fun toDouble(): Double {
        return value.toDouble()
    }
}