@file:Suppress("NOTHING_TO_INLINE")

package com.here.naksha.lib.base

inline operator fun N_Object.contains(key: String): Boolean = N.has(this, key)
inline operator fun N_Object.contains(key: Symbol): Boolean = N.has(this, key)

inline operator fun N_Array.contains(key: String): Boolean = N.has(this, key)
inline operator fun N_Array.contains(key: Symbol): Boolean = N.has(this, key)
inline operator fun N_Array.contains(key: Int): Boolean = N.has(this, key)

inline operator fun N_Object.get(key: String): Any? = N.get(this, key)
inline operator fun N_Object.get(key: Symbol): Any? = N.get(this, key)

inline operator fun N_Array.get(key: String): Any? = N.get(this, key)
inline operator fun N_Array.get(key: Symbol): Any? = N.get(this, key)
inline operator fun N_Array.get(key: Int): Any? = N.get(this, key)

inline operator fun N_Object.set(key: String, value: Any?) = N.set(this, key, value)
inline operator fun N_Object.set(key: Symbol, value: Any?) = N.set(this, key, value)

inline operator fun N_Array.set(key: String, value: Any?) = N.set(this, key, value)
inline operator fun N_Array.set(key: Symbol, value: Any?) = N.set(this, key, value)
inline operator fun N_Array.set(key: Int, value: Any?) = N.set(this, key, value)

inline fun N_Object.delete(key: String): Any? = N.delete(this, key)
inline fun N_Object.delete(key: Symbol): Any? = N.delete(this, key)

inline fun N_Array.delete(key: Int): Any? = N.delete(this, key)
inline fun N_Array.delete(key: String): Any? = N.delete(this, key)
inline fun N_Array.delete(key: Symbol): Any? = N.delete(this, key)

inline fun symbol(key: String): Symbol = N.symbol(key)

//inline operator fun PObject.iterator() : Iterator<String> = JsObjectIterator(this)
//inline operator fun PArray.iterator() : Iterator<Int> = JsArrayIterator(this)

inline fun Int64(value: Long) = N.longToInt64(value)
inline fun Int64(value: Int) = N.toInt64(value)
inline fun Int64(value: Double, rawBits: Boolean = false) = if (rawBits) N.toInt64RawBits(value) else N.toInt64(value)

inline infix fun Int64.eq(other: Short): Boolean = this eq other.toInt()
inline infix fun Int64.eq(other: Int): Boolean = N.eqi(this, other)
inline infix fun Int64.eq(other: Int64): Boolean = N.eq(this, other)
inline infix fun Int64.eq(other: Double): Boolean = N.toDouble(this) == other
inline infix fun Short.eq(other: Int64): Boolean = other eq this
inline infix fun Int.eq(other: Int64): Boolean = other eq this
inline infix fun Double.eq(other: Int64): Boolean = other eq this

inline infix fun Int64.lt(other: Short): Boolean = this lt other.toInt()
inline infix fun Int64.lt(other: Int): Boolean = N.lti(this, other)
inline infix fun Int64.lt(other: Int64): Boolean = N.lt(this, other)
inline infix fun Int64.lt(other: Double): Boolean = N.toDouble(this) < other
inline infix fun Short.lt(other: Int64): Boolean = other gte this
inline infix fun Int.lt(other: Int64): Boolean = other gte this
inline infix fun Double.lt(other: Int64): Boolean = other gte this

inline infix fun Int64.lte(other: Short): Boolean = this lte other.toInt()
inline infix fun Int64.lte(other: Int): Boolean = N.ltei(this, other)
inline infix fun Int64.lte(other: Int64): Boolean = N.lte(this, other)
inline infix fun Int64.lte(other: Double): Boolean = N.toDouble(this) <= other
inline infix fun Short.lte(other: Int64): Boolean = other gt this
inline infix fun Int.lte(other: Int64): Boolean = other gt this
inline infix fun Double.lte(other: Int64): Boolean = other gt this

inline infix fun Int64.gt(other: Short): Boolean = this gt other.toInt()
inline infix fun Int64.gt(other: Int): Boolean = N.gti(this, other)
inline infix fun Int64.gt(other: Int64): Boolean = N.gt(this, other)
inline infix fun Int64.gt(other: Double): Boolean = N.toDouble(this) > other
inline infix fun Short.gt(other: Int64): Boolean = other lte this
inline infix fun Int.gt(other: Int64): Boolean = other lte this
inline infix fun Double.gt(other: Int64): Boolean = other lte this

inline infix fun Int64.gte(other: Short): Boolean = this gte other.toInt()
inline infix fun Int64.gte(other: Int): Boolean = N.gtei(this, other)
inline infix fun Int64.gte(other: Int64): Boolean = N.gte(this, other)
inline infix fun Int64.gte(other: Double): Boolean = N.toDouble(this) >= other
inline infix fun Short.gte(other: Int64): Boolean = other lt this
inline infix fun Int.gte(other: Int64): Boolean = other lt this
inline infix fun Double.gte(other: Int64): Boolean = other lt this

inline operator fun Int64.plus(other: Short): Int64 = this + other.toInt()
inline operator fun Int64.plus(other: Int): Int64 = N.addi(this, other)
inline operator fun Int64.plus(other: Int64): Int64 = N.add(this, other)
inline operator fun Int64.plus(other: Double): Double = N.toDouble(this) + other
inline operator fun Short.plus(other: Int64): Int64 = other + this
inline operator fun Int.plus(other: Int64): Int64 = other + this
inline operator fun Double.plus(other: Int64): Double = this + N.toDouble(other)

inline operator fun Int64.minus(other: Short): Int64 = this - other.toInt()
inline operator fun Int64.minus(other: Int): Int64 = N.subi(this, other)
inline operator fun Int64.minus(other: Int64): Int64 = N.sub(this, other)
inline operator fun Int64.minus(other: Double): Double = N.toDouble(this) - other
inline operator fun Short.minus(other: Int64): Int64 = other + (-this)
inline operator fun Int.minus(other: Int64): Int64 = other + (-this)
inline operator fun Double.minus(other: Int64): Double = this - N.toDouble(other)

inline operator fun Int64.times(other: Short): Int64 = this * other.toInt()
inline operator fun Int64.times(other: Int): Int64 = N.muli(this, other)
inline operator fun Int64.times(other: Int64): Int64 = N.mul(this, other)
inline operator fun Int64.times(other: Double): Double = N.toDouble(this) * other
inline operator fun Short.times(other: Int64): Int64 = other * this
inline operator fun Int.times(other: Int64): Int64 = other * this
inline operator fun Double.times(other: Int64): Double = this * N.toDouble(other)

inline operator fun Int64.rem(other: Short): Int64 = this % other.toInt()
inline operator fun Int64.rem(other: Int): Int64 = N.modi(this, other)
inline operator fun Int64.rem(other: Int64): Int64 = N.mod(this, other)
inline operator fun Int64.rem(other: Double): Double = N.toDouble(this) % other
inline operator fun Short.rem(other: Int64): Int64 = N.toInt64(this) % other
inline operator fun Int.rem(other: Int64): Int64 = N.toInt64(this) % other
inline operator fun Double.rem(other: Int64): Double = this % N.toDouble(other)

inline operator fun Int64.div(other: Short): Int64 = this / other.toInt()
inline operator fun Int64.div(other: Int): Int64 = N.divi(this, other)
inline operator fun Int64.div(other: Int64): Int64 = N.div(this, other)
inline operator fun Int64.div(other: Double): Double = N.toDouble(this) / other
inline operator fun Short.div(other: Int64): Int64 = N.toInt64(this) / other
inline operator fun Int.div(other: Int64): Int64 = N.toInt64(this) / other
inline operator fun Double.div(other: Int64): Double = this / N.toDouble(other)

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

inline infix fun Int64.shr(bits: Int): Int64 = N.shr(this, bits)
inline infix fun Int64.ushr(bits: Int): Int64 = N.ushr(this, bits)
inline infix fun Int64.shl(bits: Int): Int64 = N.shl(this, bits)

inline infix fun Int64.and(other: Int64): Int64 = N.and(this, other)
inline infix fun Int64.or(other: Int64): Int64 = N.or(this, other)
inline infix fun Int64.xor(other: Int64): Int64 = N.xor(this, other)

inline operator fun Int64.unaryPlus(): Int64 = this
inline operator fun Int64.unaryMinus(): Int64 = N.muli(this, -1)
inline operator fun Int64.inc(): Int64 = N.addi(this, 1)
inline operator fun Int64.dec(): Int64 = N.addi(this, -1)
inline operator fun Int64.inv(): Int64 = N.inv(this)

inline fun Int64.toByte(): Byte = N.toInt(this).toByte()
inline fun Int64.toShort(): Short = N.toInt(this).toShort()
inline fun Int64.toInt(): Int = N.toInt(this)
inline fun Int64.toLong(): Long = N.int64ToLong(this)
inline fun Int64.toFloat(): Float = N.toDouble(this).toFloat()
inline fun Int64.toDouble(): Double = N.toDouble(this)
inline fun Int64.toDoubleRawBits(): Double = N.toDoubleRawBits(this)

inline operator fun N_Array.iterator(): Iterator<P_Entry<Int, Any?>> = KtIterator(N.arrayIterator(this))
inline operator fun N_Object.iterator(): Iterator<P_Entry<String, Any?>> = KtIterator(N.objectIterator(this))

fun returnNull(): String? = null

inline fun notUndefined(value: Any?, err: (() -> String?) = ::returnNull): Any? = if (value === N.undefined) throw ClassCastException(err()) else value

inline fun notNull(value: Any?, err: (() -> String?) = ::returnNull): Any = if (value === null) throw ClassCastException(err()) else value
