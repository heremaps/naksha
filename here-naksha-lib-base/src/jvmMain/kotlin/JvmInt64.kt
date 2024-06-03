package com.here.naksha.lib.base

/**
 * The JVM Int64 implementation. If an instance is needed use [Platform.longToInt64], otherwise create an instance and let the compiler
 * eliminate it.
 */
class JvmInt64(private val value: Long) : Number(), Int64 {

    override fun toByte(): Byte = value.toByte()

    override fun toShort(): Short = value.toShort()

    override fun toInt(): Int = value.toInt()

    override fun toLong(): Long = value

    override fun toFloat(): Float = value.toFloat()

    override fun toDouble(): Double = value.toDouble()

    override fun toString(): String = value.toString()
}