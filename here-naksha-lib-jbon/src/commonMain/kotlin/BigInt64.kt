@file:OptIn(ExperimentalJsExport::class)
@file:Suppress("NOTHING_TO_INLINE")

package com.here.naksha.lib.jbon

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/**
 * Native 64-bit integer representation.
 */
@JsExport
interface BigInt64

inline fun BigInt64(value: Long) = Jb.int64.longToBigInt64(value)
inline fun BigInt64(value: Int) = Jb.int64.intToBigInt64(value)
inline fun BigInt64(value: Double, rawBits: Boolean = false) = Jb.int64.doubleToBigInt64(value, rawBits)
inline fun isBigInt64(any:Any?) : Boolean = any is BigInt64
inline fun asBigInt64(any:Any?) : BigInt64 = any as BigInt64
inline infix fun BigInt64.eq(other: BigInt64): Boolean = Jb.int64.eq(this, other)
inline infix fun BigInt64.eqi(other: Int): Boolean = Jb.int64.eqi(this, other)
inline infix fun BigInt64.lt(other: BigInt64): Boolean = Jb.int64.lt(this, other)
inline infix fun BigInt64.lti(other: Int): Boolean = Jb.int64.lti(this, other)
inline infix fun BigInt64.lte(other: BigInt64): Boolean = Jb.int64.lte(this, other)
inline infix fun BigInt64.ltei(other: Int): Boolean = Jb.int64.ltei(this, other)
inline infix fun BigInt64.gt(other: BigInt64): Boolean = Jb.int64.gt(this, other)
inline infix fun BigInt64.gti(other: Int): Boolean = Jb.int64.gti(this, other)
inline infix fun BigInt64.gte(other: BigInt64): Boolean = Jb.int64.gte(this, other)
inline infix fun BigInt64.gtei(other: Int): Boolean = Jb.int64.gtei(this, other)
inline infix fun BigInt64.shr(bits: Int): BigInt64 = Jb.int64.shr(this, bits)
inline infix fun BigInt64.ushr(bits: Int): BigInt64 = Jb.int64.ushr(this, bits)
inline infix fun BigInt64.shl(bits: Int): BigInt64 = Jb.int64.shl(this, bits)
inline infix fun BigInt64.add(other: BigInt64): BigInt64 = Jb.int64.add(this, other)
inline infix fun BigInt64.addi(other: Int): BigInt64 = Jb.int64.addi(this, other)
inline infix fun BigInt64.addf(other: Double): BigInt64 = Jb.int64.addf(this, other)
inline operator fun BigInt64.plus(other: BigInt64): BigInt64 = Jb.int64.add(this, other)
inline operator fun BigInt64.plus(other: Int): BigInt64 = Jb.int64.addi(this, other)
inline infix fun BigInt64.sub(other: BigInt64): BigInt64 = Jb.int64.sub(this, other)
inline infix fun BigInt64.subi(other: Int): BigInt64 = Jb.int64.subi(this, other)
inline infix fun BigInt64.subf(other: Double): BigInt64 = Jb.int64.subf(this, other)
inline operator fun BigInt64.minus(other: BigInt64): BigInt64 = Jb.int64.sub(this, other)
inline operator fun BigInt64.minus(other: Int): BigInt64 = Jb.int64.subi(this, other)
inline infix fun BigInt64.mul(other: BigInt64): BigInt64 = Jb.int64.mul(this, other)
inline infix fun BigInt64.muli(other: Int): BigInt64 = Jb.int64.muli(this, other)
inline infix fun BigInt64.mulf(other: Double): BigInt64 = Jb.int64.mulf(this, other)
inline operator fun BigInt64.times(other: BigInt64): BigInt64 = Jb.int64.mul(this, other)
inline operator fun BigInt64.times(other: Int): BigInt64 = Jb.int64.muli(this, other)
inline infix fun BigInt64.mod(other: BigInt64): BigInt64 = Jb.int64.mod(this, other)
inline infix fun BigInt64.modi(other: Int): BigInt64 = Jb.int64.modi(this, other)
inline operator fun BigInt64.rem(other: BigInt64): BigInt64 = Jb.int64.mod(this, other)
inline operator fun BigInt64.rem(other: Int): BigInt64 = Jb.int64.modi(this, other)
inline operator fun BigInt64.div(other: BigInt64): BigInt64 = Jb.int64.div(this, other)
inline operator fun BigInt64.div(other: Int): BigInt64 = Jb.int64.divi(this, other)
inline infix fun BigInt64.divi(other: Int): BigInt64 = Jb.int64.divi(this, other)
inline infix fun BigInt64.divf(other: Double): BigInt64 = Jb.int64.divf(this, other)
inline infix fun BigInt64.and(other: BigInt64): BigInt64 = Jb.int64.and(this, other)
inline infix fun BigInt64.or(other: BigInt64): BigInt64 = Jb.int64.or(this, other)
inline infix fun BigInt64.xor(other: BigInt64): BigInt64 = Jb.int64.xor(this, other)
inline operator fun BigInt64.compareTo(other:BigInt64) : Int {
    val diff = this sub other
    return if (diff eqi 0) 0 else if (diff lti 0) -1 else 1
}
inline operator fun BigInt64.compareTo(other:Int) : Int {
    val diff = this subi other
    return if (diff eqi 0) 0 else if (diff lti 0) -1 else 1
}
inline operator fun BigInt64.unaryPlus(): BigInt64 = this
inline operator fun BigInt64.unaryMinus(): BigInt64 = Jb.int64.muli(this, -1)
inline operator fun BigInt64.inc(): BigInt64 = Jb.int64.addi(this, 1)
inline operator fun BigInt64.dec(): BigInt64 = Jb.int64.addi(this, -1)
inline fun BigInt64.inv(): BigInt64 = Jb.int64.inv(this)
inline fun BigInt64.toByte(): Byte = toInt().toByte()
inline fun BigInt64.toShort(): Short = toInt().toShort()
inline fun BigInt64.toInt(): Int = Jb.int64.toInt(this)
inline fun BigInt64.toLong(): Long = Jb.int64.toLong(this)
inline fun BigInt64.toFloat(): Float = toDouble().toFloat()
inline fun BigInt64.toDouble(): Double = Jb.int64.toDouble(this)
inline fun BigInt64.toDoubleRawBits(): Double = Jb.int64.toDoubleRawBits(this)
