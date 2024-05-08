@file:Suppress("NOTHING_TO_INLINE")

package com.here.naksha.lib.base

inline operator fun PObject.contains(key: String): Boolean = Base.has(this, key)
inline operator fun PObject.contains(key: Symbol): Boolean = Base.has(this, key)

inline operator fun PArray.contains(key: String): Boolean = Base.has(this, key)
inline operator fun PArray.contains(key: Symbol): Boolean = Base.has(this, key)
inline operator fun PArray.contains(key: Int): Boolean = Base.has(this, key)

inline operator fun PObject.get(key: String): Any? = Base.get(this, key)
inline operator fun PObject.get(key: Symbol): Any? = Base.get(this, key)

inline operator fun PArray.get(key: String): Any? = Base.get(this, key)
inline operator fun PArray.get(key: Symbol): Any? = Base.get(this, key)
inline operator fun PArray.get(key: Int): Any? = Base.get(this, key)

inline operator fun PObject.set(key: String, value: Any?) = Base.set(this, key, value)
inline operator fun PObject.set(key: Symbol, value: Any?) = Base.set(this, key, value)

inline operator fun PArray.set(key: String, value: Any?) = Base.set(this, key, value)
inline operator fun PArray.set(key: Symbol, value: Any?) = Base.set(this, key, value)
inline operator fun PArray.set(key: Int, value: Any?) = Base.set(this, key, value)

inline fun PObject.delete(key: String): Any? = Base.delete(this, key)
inline fun PObject.delete(key: Symbol): Any? = Base.delete(this, key)

inline fun PArray.delete(key: Int): Any? = Base.delete(this, key)
inline fun PArray.delete(key: String): Any? = Base.delete(this, key)
inline fun PArray.delete(key: Symbol): Any? = Base.delete(this, key)

inline fun symbol(key: String): Symbol = Base.symbol(key)

//inline operator fun PObject.iterator() : Iterator<String> = JsObjectIterator(this)
//inline operator fun PArray.iterator() : Iterator<Int> = JsArrayIterator(this)

inline fun Int64(value: Long) = Base.longToInt64(value)
inline fun Int64(value: Int) = Base.toInt64(value)
inline fun Int64(value: Double, rawBits: Boolean = false) = if (rawBits) Base.toInt64RawBits(value) else Base.toInt64(value)

inline infix fun Int64.eq(other: Short): Boolean = this eq other.toInt()
inline infix fun Int64.eq(other: Int): Boolean = Base.eqi(this, other)
inline infix fun Int64.eq(other: Int64): Boolean = Base.eq(this, other)
inline infix fun Int64.eq(other: Double): Boolean = Base.toDouble(this) == other
inline infix fun Short.eq(other: Int64): Boolean = other eq this
inline infix fun Int.eq(other: Int64): Boolean = other eq this
inline infix fun Double.eq(other: Int64): Boolean = other eq this

inline infix fun Int64.lt(other: Short): Boolean = this lt other.toInt()
inline infix fun Int64.lt(other: Int): Boolean = Base.lti(this, other)
inline infix fun Int64.lt(other: Int64): Boolean = Base.lt(this, other)
inline infix fun Int64.lt(other: Double): Boolean = Base.toDouble(this) < other
inline infix fun Short.lt(other: Int64): Boolean = other gte this
inline infix fun Int.lt(other: Int64): Boolean = other gte this
inline infix fun Double.lt(other: Int64): Boolean = other gte this

inline infix fun Int64.lte(other: Short): Boolean = this lte other.toInt()
inline infix fun Int64.lte(other: Int): Boolean = Base.ltei(this, other)
inline infix fun Int64.lte(other: Int64): Boolean = Base.lte(this, other)
inline infix fun Int64.lte(other: Double): Boolean = Base.toDouble(this) <= other
inline infix fun Short.lte(other: Int64): Boolean = other gt this
inline infix fun Int.lte(other: Int64): Boolean = other gt this
inline infix fun Double.lte(other: Int64): Boolean = other gt this

inline infix fun Int64.gt(other: Short): Boolean = this gt other.toInt()
inline infix fun Int64.gt(other: Int): Boolean = Base.gti(this, other)
inline infix fun Int64.gt(other: Int64): Boolean = Base.gt(this, other)
inline infix fun Int64.gt(other: Double): Boolean = Base.toDouble(this) > other
inline infix fun Short.gt(other: Int64): Boolean = other lte this
inline infix fun Int.gt(other: Int64): Boolean = other lte this
inline infix fun Double.gt(other: Int64): Boolean = other lte this

inline infix fun Int64.gte(other: Short): Boolean = this gte other.toInt()
inline infix fun Int64.gte(other: Int): Boolean = Base.gtei(this, other)
inline infix fun Int64.gte(other: Int64): Boolean = Base.gte(this, other)
inline infix fun Int64.gte(other: Double): Boolean = Base.toDouble(this) >= other
inline infix fun Short.gte(other: Int64): Boolean = other lt this
inline infix fun Int.gte(other: Int64): Boolean = other lt this
inline infix fun Double.gte(other: Int64): Boolean = other lt this

inline operator fun Int64.plus(other: Short): Int64 = this + other.toInt()
inline operator fun Int64.plus(other: Int): Int64 = Base.addi(this, other)
inline operator fun Int64.plus(other: Int64): Int64 = Base.add(this, other)
inline operator fun Int64.plus(other: Double): Double = Base.toDouble(this) + other
inline operator fun Short.plus(other: Int64): Int64 = other + this
inline operator fun Int.plus(other: Int64): Int64 = other + this
inline operator fun Double.plus(other: Int64): Double = this + Base.toDouble(other)

inline operator fun Int64.minus(other: Short): Int64 = this - other.toInt()
inline operator fun Int64.minus(other: Int): Int64 = Base.subi(this, other)
inline operator fun Int64.minus(other: Int64): Int64 = Base.sub(this, other)
inline operator fun Int64.minus(other: Double): Double = Base.toDouble(this) - other
inline operator fun Short.minus(other: Int64): Int64 = other + (-this)
inline operator fun Int.minus(other: Int64): Int64 = other + (-this)
inline operator fun Double.minus(other: Int64): Double = this - Base.toDouble(other)

inline operator fun Int64.times(other: Short): Int64 = this * other.toInt()
inline operator fun Int64.times(other: Int): Int64 = Base.muli(this, other)
inline operator fun Int64.times(other: Int64): Int64 = Base.mul(this, other)
inline operator fun Int64.times(other: Double): Double = Base.toDouble(this) * other
inline operator fun Short.times(other: Int64): Int64 = other * this
inline operator fun Int.times(other: Int64): Int64 = other * this
inline operator fun Double.times(other: Int64): Double = this * Base.toDouble(other)

inline operator fun Int64.rem(other: Short): Int64 = this % other.toInt()
inline operator fun Int64.rem(other: Int): Int64 = Base.modi(this, other)
inline operator fun Int64.rem(other: Int64): Int64 = Base.mod(this, other)
inline operator fun Int64.rem(other: Double): Double = Base.toDouble(this) % other
inline operator fun Short.rem(other: Int64): Int64 = Base.toInt64(this) % other
inline operator fun Int.rem(other: Int64): Int64 = Base.toInt64(this) % other
inline operator fun Double.rem(other: Int64): Double = this % Base.toDouble(other)

inline operator fun Int64.div(other: Short): Int64 = this / other.toInt()
inline operator fun Int64.div(other: Int): Int64 = Base.divi(this, other)
inline operator fun Int64.div(other: Int64): Int64 = Base.div(this, other)
inline operator fun Int64.div(other: Double): Double = Base.toDouble(this) / other
inline operator fun Short.div(other: Int64): Int64 = Base.toInt64(this) / other
inline operator fun Int.div(other: Int64): Int64 = Base.toInt64(this) / other
inline operator fun Double.div(other: Int64): Double = this / Base.toDouble(other)

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

inline infix fun Int64.shr(bits: Int): Int64 = Base.shr(this, bits)
inline infix fun Int64.ushr(bits: Int): Int64 = Base.ushr(this, bits)
inline infix fun Int64.shl(bits: Int): Int64 = Base.shl(this, bits)

inline infix fun Int64.and(other: Int64): Int64 = Base.and(this, other)
inline infix fun Int64.or(other: Int64): Int64 = Base.or(this, other)
inline infix fun Int64.xor(other: Int64): Int64 = Base.xor(this, other)

inline operator fun Int64.unaryPlus(): Int64 = this
inline operator fun Int64.unaryMinus(): Int64 = Base.muli(this, -1)
inline operator fun Int64.inc(): Int64 = Base.addi(this, 1)
inline operator fun Int64.dec(): Int64 = Base.addi(this, -1)
inline operator fun Int64.inv(): Int64 = Base.inv(this)

inline fun Int64.toByte(): Byte = Base.toInt(this).toByte()
inline fun Int64.toShort(): Short = Base.toInt(this).toShort()
inline fun Int64.toInt(): Int = Base.toInt(this)
inline fun Int64.toLong(): Long = Base.int64ToLong(this)
inline fun Int64.toFloat(): Float = Base.toDouble(this).toFloat()
inline fun Int64.toDouble(): Double = Base.toDouble(this)
inline fun Int64.toDoubleRawBits(): Double = Base.toDoubleRawBits(this)

inline operator fun PArray.iterator(): Iterator<RawPair<Int, Any?>> = KtIterator(Base.arrayIterator(this))
inline operator fun PObject.iterator(): Iterator<RawPair<String, Any?>> = KtIterator(Base.objectIterator(this))

fun returnNull(): String? = null

inline fun notUndefined(value: Any?, err: (() -> String?) = ::returnNull): Any? = if (value === Base.undefined) throw ClassCastException(err()) else value

inline fun notNull(value: Any?, err: (() -> String?) = ::returnNull): Any = if (value === null) throw ClassCastException(err()) else value
