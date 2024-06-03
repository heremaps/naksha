@file:Suppress("NOTHING_TO_INLINE")

package com.here.naksha.lib.base

//inline operator fun PlatformMap.contains(key: String): Boolean = Int64Api.has(this, key)
//inline operator fun PlatformObject.contains(key: Symbol): Boolean = Platform.has(this, key)
//
//inline operator fun PlatformList.contains(key: String): Boolean = Platform.has(this, key)
//inline operator fun PlatformList.contains(key: Symbol): Boolean = Platform.has(this, key)
//inline operator fun PlatformList.contains(key: Int): Boolean = Platform.has(this, key)
//
//inline operator fun PlatformObject.get(key: String): Any? = Platform.get(this, key)
//inline operator fun PlatformObject.get(key: Symbol): Any? = Platform.get(this, key)
//
//inline operator fun PlatformList.get(key: String): Any? = Platform.get(this, key)
//inline operator fun PlatformList.get(key: Symbol): Any? = Platform.get(this, key)
//inline operator fun PlatformList.get(key: Int): Any? = Platform.get(this, key)
//
//inline operator fun PlatformObject.set(key: String, value: Any?) = Platform.set(this, key, value)
//inline operator fun PlatformObject.set(key: Symbol, value: Any?) = Platform.set(this, key, value)
//
//inline operator fun PlatformList.set(key: String, value: Any?) = Platform.set(this, key, value)
//inline operator fun PlatformList.set(key: Symbol, value: Any?) = Platform.set(this, key, value)
//inline operator fun PlatformList.set(key: Int, value: Any?) = Platform.set(this, key, value)
//
//inline fun PlatformObject.delete(key: String): Any? = Platform.delete(this, key)
//inline fun PlatformObject.delete(key: Symbol): Any? = Platform.delete(this, key)
//
//inline fun PlatformList.delete(key: Int): Any? = Platform.delete(this, key)
//inline fun PlatformList.delete(key: String): Any? = Platform.delete(this, key)
//inline fun PlatformList.delete(key: Symbol): Any? = Platform.delete(this, key)
//
//inline fun symbol(key: String): Symbol = Platform.symbol(key)

//inline operator fun PObject.iterator() : Iterator<String> = JsObjectIterator(this)
//inline operator fun PArray.iterator() : Iterator<Int> = JsArrayIterator(this)

inline fun Int64(value: Long) = Platform.longToInt64(value)
inline fun Int64(value: Int) = Platform.toInt64(value)
inline fun Int64(value: Double, rawBits: Boolean = false) = if (rawBits) Platform.toInt64RawBits(value) else Platform.toInt64(value)

inline infix fun Int64.eq(other: Short): Boolean = this eq other.toInt()
infix fun Int64.eq(other: Int): Boolean = Int64Api.eqi(this, other)
infix fun Int64.eq(other: Int64): Boolean = Int64Api.eq(this, other)
inline infix fun Int64.eq(other: Double): Boolean = Platform.toDouble(this) == other
inline infix fun Short.eq(other: Int64): Boolean = other eq this
inline infix fun Int.eq(other: Int64): Boolean = other eq this
inline infix fun Double.eq(other: Int64): Boolean = other eq this

inline infix fun Int64.lt(other: Short): Boolean = this lt other.toInt()
infix fun Int64.lt(other: Int): Boolean = Int64Api.lti(this, other)
infix fun Int64.lt(other: Int64): Boolean = Int64Api.lt(this, other)
inline infix fun Int64.lt(other: Double): Boolean = Platform.toDouble(this) < other
inline infix fun Short.lt(other: Int64): Boolean = other gte this
inline infix fun Int.lt(other: Int64): Boolean = other gte this
inline infix fun Double.lt(other: Int64): Boolean = other gte this

inline infix fun Int64.lte(other: Short): Boolean = this lte other.toInt()
infix fun Int64.lte(other: Int): Boolean = Int64Api.ltei(this, other)
infix fun Int64.lte(other: Int64): Boolean = Int64Api.lte(this, other)
inline infix fun Int64.lte(other: Double): Boolean = Platform.toDouble(this) <= other
inline infix fun Short.lte(other: Int64): Boolean = other gt this
inline infix fun Int.lte(other: Int64): Boolean = other gt this
inline infix fun Double.lte(other: Int64): Boolean = other gt this

inline infix fun Int64.gt(other: Short): Boolean = this gt other.toInt()
infix fun Int64.gt(other: Int): Boolean = Int64Api.gti(this, other)
infix fun Int64.gt(other: Int64): Boolean = Int64Api.gt(this, other)
inline infix fun Int64.gt(other: Double): Boolean = Platform.toDouble(this) > other
inline infix fun Short.gt(other: Int64): Boolean = other lte this
inline infix fun Int.gt(other: Int64): Boolean = other lte this
inline infix fun Double.gt(other: Int64): Boolean = other lte this

inline infix fun Int64.gte(other: Short): Boolean = this gte other.toInt()
infix fun Int64.gte(other: Int): Boolean = Int64Api.gtei(this, other)
infix fun Int64.gte(other: Int64): Boolean = Int64Api.gte(this, other)
inline infix fun Int64.gte(other: Double): Boolean = Platform.toDouble(this) >= other
inline infix fun Short.gte(other: Int64): Boolean = other lt this
inline infix fun Int.gte(other: Int64): Boolean = other lt this
inline infix fun Double.gte(other: Int64): Boolean = other lt this

inline operator fun Int64.plus(other: Short): Int64 = this + other.toInt()
operator fun Int64.plus(other: Int): Int64 = Int64Api.addi(this, other)
operator fun Int64.plus(other: Int64): Int64 = Int64Api.add(this, other)
inline operator fun Int64.plus(other: Double): Double = Platform.toDouble(this) + other
inline operator fun Short.plus(other: Int64): Int64 = other + this
inline operator fun Int.plus(other: Int64): Int64 = other + this
inline operator fun Double.plus(other: Int64): Double = this + Platform.toDouble(other)

inline operator fun Int64.minus(other: Short): Int64 = this - other.toInt()
operator fun Int64.minus(other: Int): Int64 = Int64Api.subi(this, other)
operator fun Int64.minus(other: Int64): Int64 = Int64Api.sub(this, other)
inline operator fun Int64.minus(other: Double): Double = Platform.toDouble(this) - other
inline operator fun Short.minus(other: Int64): Int64 = other + (-this)
inline operator fun Int.minus(other: Int64): Int64 = other + (-this)
inline operator fun Double.minus(other: Int64): Double = this - Platform.toDouble(other)

inline operator fun Int64.times(other: Short): Int64 = this * other.toInt()
operator fun Int64.times(other: Int): Int64 = Int64Api.muli(this, other)
operator fun Int64.times(other: Int64): Int64 = Int64Api.mul(this, other)
inline operator fun Int64.times(other: Double): Double = Platform.toDouble(this) * other
inline operator fun Short.times(other: Int64): Int64 = other * this
inline operator fun Int.times(other: Int64): Int64 = other * this
inline operator fun Double.times(other: Int64): Double = this * Platform.toDouble(other)

inline operator fun Int64.rem(other: Short): Int64 = this % other.toInt()
operator fun Int64.rem(other: Int): Int64 = Int64Api.modi(this, other)
operator fun Int64.rem(other: Int64): Int64 = Int64Api.mod(this, other)
inline operator fun Int64.rem(other: Double): Double = Platform.toDouble(this) % other
inline operator fun Short.rem(other: Int64): Int64 = Platform.toInt64(this) % other
inline operator fun Int.rem(other: Int64): Int64 = Platform.toInt64(this) % other
inline operator fun Double.rem(other: Int64): Double = this % Platform.toDouble(other)

inline operator fun Int64.div(other: Short): Int64 = this / other.toInt()
operator fun Int64.div(other: Int): Int64 = Int64Api.divi(this, other)
operator fun Int64.div(other: Int64): Int64 = Int64Api.div(this, other)
inline operator fun Int64.div(other: Double): Double = Platform.toDouble(this) / other
inline operator fun Short.div(other: Int64): Int64 = Platform.toInt64(this) / other
inline operator fun Int.div(other: Int64): Int64 = Platform.toInt64(this) / other
inline operator fun Double.div(other: Int64): Double = this / Platform.toDouble(other)

inline operator fun Int64.compareTo(other: Short): Int {
    val diff = this - other
    return if (diff eq 0) 0 else if (diff lt 0) -1 else 1
}

inline operator fun Int64.compareTo(other: Int): Int {
    val diff = this - other
    return if (diff eq 0) 0 else if (diff lt 0) -1 else 1
}

inline operator fun Int64.compareTo(other: Int64): Int {
    val diff = this - other
    return if (diff eq 0) 0 else if (diff lt 0) -1 else 1
}

inline operator fun Int64.compareTo(other: Double): Int {
    val diff = this - other
    return if (diff == 0.0) 0 else if (diff <= 0.0) -1 else 1
}

inline operator fun Short.compareTo(other: Int64): Int {
    val diff = this - other
    return if (diff eq 0) 0 else if (diff lt 0) -1 else 1
}

inline operator fun Int.compareTo(other: Int64): Int {
    val diff = this - other
    return if (diff eq 0) 0 else if (diff lt 0) -1 else 1
}

inline operator fun Double.compareTo(other: Int64): Int {
    val diff = this - other
    return if (diff == 0.0) 0 else if (diff <= 0.0) -1 else 1
}

infix fun Int64.shr(bits: Int): Int64 = Int64Api.shr(this, bits)
infix fun Int64.ushr(bits: Int): Int64 = Int64Api.ushr(this, bits)
infix fun Int64.shl(bits: Int): Int64 = Int64Api.shl(this, bits)

infix fun Int64.and(other: Int64): Int64 = Int64Api.and(this, other)
infix fun Int64.or(other: Int64): Int64 = Int64Api.or(this, other)
infix fun Int64.xor(other: Int64): Int64 = Int64Api.xor(this, other)

inline operator fun Int64.unaryPlus(): Int64 = this
operator fun Int64.unaryMinus(): Int64 = Int64Api.muli(this, -1)
operator fun Int64.inc(): Int64 = Int64Api.addi(this, 1)
operator fun Int64.dec(): Int64 = Int64Api.addi(this, -1)
operator fun Int64.inv(): Int64 = Int64Api.inv(this)

inline fun Int64.toByte(): Byte = Platform.toInt(this).toByte()
inline fun Int64.toShort(): Short = Platform.toInt(this).toShort()
inline fun Int64.toInt(): Int = Platform.toInt(this)
inline fun Int64.toLong(): Long = Platform.int64ToLong(this)
inline fun Int64.toFloat(): Float = Platform.toDouble(this).toFloat()
inline fun Int64.toDouble(): Double = Platform.toDouble(this)
inline fun Int64.toDoubleRawBits(): Double = Platform.toDoubleRawBits(this)

//inline operator fun PlatformList.iterator(): Iterator<P_MapEntry<Int, Any?>> = KtIterator(Platform.arrayIterator(this))
//inline operator fun PlatformObject.iterator(): Iterator<P_MapEntry<String, Any?>> = KtIterator(Platform.objectIterator(this))

//fun returnNull(): String? = null
//
//inline fun notUndefined(value: Any?, err: (() -> String?) = ::returnNull): Any? = if (value === Platform.undefined) throw ClassCastException(err()) else value
//
//inline fun notNull(value: Any?, err: (() -> String?) = ::returnNull): Any = if (value === null) throw ClassCastException(err()) else value
