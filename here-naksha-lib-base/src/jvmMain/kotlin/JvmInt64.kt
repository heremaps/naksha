package com.here.naksha.lib.nak

/**
 * The JVM Int64 implementation. If an instance is needed use [Nak.longToInt64], otherwise create an instance and let the compiler
 * eliminate it.
 */
@JvmInline
value class JvmInt64(private val value: Long) : Int64 {

    fun toByte(): Byte = value.toByte()

    fun toShort(): Short = value.toShort()

    fun toInt(): Int = value.toInt()

    fun toLong(): Long = value

    fun toFloat(): Float = value.toFloat()

    fun toDouble(): Double = value.toDouble()

}