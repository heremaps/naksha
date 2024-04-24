@file:Suppress("NOTHING_TO_INLINE")

package com.here.naksha.lib.nak

inline operator fun PObject.contains(key: String): Boolean = Nak.has(this, key)
inline operator fun PObject.contains(key: PSymbol): Boolean = Nak.has(this, key)

inline operator fun PArray.contains(key: String): Boolean = Nak.has(this, key)
inline operator fun PArray.contains(key: PSymbol): Boolean = Nak.has(this, key)
inline operator fun PArray.contains(key: Int): Boolean = Nak.has(this, key)

inline operator fun PObject.get(key: String): Any? = Nak.get(this, key)
inline operator fun PObject.get(key: PSymbol): Any? = Nak.get(this, key)

inline operator fun PArray.get(key: String): Any? = Nak.get(this, key)
inline operator fun PArray.get(key: PSymbol): Any? = Nak.get(this, key)
inline operator fun PArray.get(key: Int): Any? = Nak.get(this, key)

inline operator fun PObject.set(key: String, value: Any?) = Nak.set(this, key, value)
inline operator fun PObject.set(key: PSymbol, value: Any?) = Nak.set(this, key, value)

inline operator fun PArray.set(key: String, value: Any?) = Nak.set(this, key, value)
inline operator fun PArray.set(key: PSymbol, value: Any?) = Nak.set(this, key, value)
inline operator fun PArray.set(key: Int, value: Any?) = Nak.set(this, key, value)

inline fun PObject.delete(key: String): Any? = Nak.delete(this, key)
inline fun PObject.delete(key: PSymbol): Any? = Nak.delete(this, key)

inline fun PArray.delete(key: Int): Any? = Nak.delete(this, key)
inline fun PArray.delete(key: String): Any? = Nak.delete(this, key)
inline fun PArray.delete(key: PSymbol): Any? = Nak.delete(this, key)

inline fun symbol(key: String): PSymbol = Nak.symbol(key)

//inline operator fun PObject.iterator() : Iterator<String> = JsObjectIterator(this)
//inline operator fun PArray.iterator() : Iterator<Int> = JsArrayIterator(this)

inline fun Int64(value: Long) = Nak.longToInt64(value)
inline fun Int64(value: Int) = Nak.toInt64(value)
inline fun Int64(value: Double, rawBits: Boolean = false) = if (rawBits) Nak.toInt64RawBits(value) else Nak.toInt64(value)

inline infix fun Int64.eq(other: Short): Boolean = this eq other.toInt()
inline infix fun Int64.eq(other: Int): Boolean = Nak.eqi(this, other)
inline infix fun Int64.eq(other: Int64): Boolean = Nak.eq(this, other)
inline infix fun Int64.eq(other: Double): Boolean = Nak.toDouble(this) == other
inline infix fun Short.eq(other: Int64): Boolean = other eq this
inline infix fun Int.eq(other: Int64): Boolean = other eq this
inline infix fun Double.eq(other: Int64): Boolean = other eq this

inline infix fun Int64.lt(other: Short): Boolean = this lt other.toInt()
inline infix fun Int64.lt(other: Int): Boolean = Nak.lti(this, other)
inline infix fun Int64.lt(other: Int64): Boolean = Nak.lt(this, other)
inline infix fun Int64.lt(other: Double): Boolean = Nak.toDouble(this) < other
inline infix fun Short.lt(other: Int64): Boolean = other gte this
inline infix fun Int.lt(other: Int64): Boolean = other gte this
inline infix fun Double.lt(other: Int64): Boolean = other gte this

inline infix fun Int64.lte(other: Short): Boolean = this lte other.toInt()
inline infix fun Int64.lte(other: Int): Boolean = Nak.ltei(this, other)
inline infix fun Int64.lte(other: Int64): Boolean = Nak.lte(this, other)
inline infix fun Int64.lte(other: Double): Boolean = Nak.toDouble(this) <= other
inline infix fun Short.lte(other: Int64): Boolean = other gt this
inline infix fun Int.lte(other: Int64): Boolean = other gt this
inline infix fun Double.lte(other: Int64): Boolean = other gt this

inline infix fun Int64.gt(other: Short): Boolean = this gt other.toInt()
inline infix fun Int64.gt(other: Int): Boolean = Nak.gti(this, other)
inline infix fun Int64.gt(other: Int64): Boolean = Nak.gt(this, other)
inline infix fun Int64.gt(other: Double): Boolean = Nak.toDouble(this) > other
inline infix fun Short.gt(other: Int64): Boolean = other lte this
inline infix fun Int.gt(other: Int64): Boolean = other lte this
inline infix fun Double.gt(other: Int64): Boolean = other lte this

inline infix fun Int64.gte(other: Short): Boolean = this gte other.toInt()
inline infix fun Int64.gte(other: Int): Boolean = Nak.gtei(this, other)
inline infix fun Int64.gte(other: Int64): Boolean = Nak.gte(this, other)
inline infix fun Int64.gte(other: Double): Boolean = Nak.toDouble(this) >= other
inline infix fun Short.gte(other: Int64): Boolean = other lt this
inline infix fun Int.gte(other: Int64): Boolean = other lt this
inline infix fun Double.gte(other: Int64): Boolean = other lt this

inline operator fun Int64.plus(other: Short): Int64 = this + other.toInt()
inline operator fun Int64.plus(other: Int): Int64 = Nak.addi(this, other)
inline operator fun Int64.plus(other: Int64): Int64 = Nak.add(this, other)
inline operator fun Int64.plus(other: Double): Double = Nak.toDouble(this) + other
inline operator fun Short.plus(other: Int64): Int64 = other + this
inline operator fun Int.plus(other: Int64): Int64 = other + this
inline operator fun Double.plus(other: Int64): Double = this + Nak.toDouble(other)

inline operator fun Int64.minus(other: Short): Int64 = this - other.toInt()
inline operator fun Int64.minus(other: Int): Int64 = Nak.subi(this, other)
inline operator fun Int64.minus(other: Int64): Int64 = Nak.sub(this, other)
inline operator fun Int64.minus(other: Double): Double = Nak.toDouble(this) - other
inline operator fun Short.minus(other: Int64): Int64 = other + (-this)
inline operator fun Int.minus(other: Int64): Int64 = other + (-this)
inline operator fun Double.minus(other: Int64): Double = this - Nak.toDouble(other)

inline operator fun Int64.times(other: Short): Int64 = this * other.toInt()
inline operator fun Int64.times(other: Int): Int64 = Nak.muli(this, other)
inline operator fun Int64.times(other: Int64): Int64 = Nak.mul(this, other)
inline operator fun Int64.times(other: Double): Double = Nak.toDouble(this) * other
inline operator fun Short.times(other: Int64): Int64 = other * this
inline operator fun Int.times(other: Int64): Int64 = other * this
inline operator fun Double.times(other: Int64): Double = this * Nak.toDouble(other)

inline operator fun Int64.rem(other: Short): Int64 = this % other.toInt()
inline operator fun Int64.rem(other: Int): Int64 = Nak.modi(this, other)
inline operator fun Int64.rem(other: Int64): Int64 = Nak.mod(this, other)
inline operator fun Int64.rem(other: Double): Double = Nak.toDouble(this) % other
inline operator fun Short.rem(other: Int64): Int64 = Nak.toInt64(this) % other
inline operator fun Int.rem(other: Int64): Int64 = Nak.toInt64(this) % other
inline operator fun Double.rem(other: Int64): Double = this % Nak.toDouble(other)

inline operator fun Int64.div(other: Short): Int64 = this / other.toInt()
inline operator fun Int64.div(other: Int): Int64 = Nak.divi(this, other)
inline operator fun Int64.div(other: Int64): Int64 = Nak.div(this, other)
inline operator fun Int64.div(other: Double): Double = Nak.toDouble(this) / other
inline operator fun Short.div(other: Int64): Int64 = Nak.toInt64(this) / other
inline operator fun Int.div(other: Int64): Int64 = Nak.toInt64(this) / other
inline operator fun Double.div(other: Int64): Double = this / Nak.toDouble(other)

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

inline infix fun Int64.shr(bits: Int): Int64 = Nak.shr(this, bits)
inline infix fun Int64.ushr(bits: Int): Int64 = Nak.ushr(this, bits)
inline infix fun Int64.shl(bits: Int): Int64 = Nak.shl(this, bits)

inline infix fun Int64.and(other: Int64): Int64 = Nak.and(this, other)
inline infix fun Int64.or(other: Int64): Int64 = Nak.or(this, other)
inline infix fun Int64.xor(other: Int64): Int64 = Nak.xor(this, other)

inline operator fun Int64.unaryPlus(): Int64 = this
inline operator fun Int64.unaryMinus(): Int64 = Nak.muli(this, -1)
inline operator fun Int64.inc(): Int64 = Nak.addi(this, 1)
inline operator fun Int64.dec(): Int64 = Nak.addi(this, -1)
inline operator fun Int64.inv(): Int64 = Nak.inv(this)

inline fun Int64.toByte(): Byte = Nak.toInt(this).toByte()
inline fun Int64.toShort(): Short = Nak.toInt(this).toShort()
inline fun Int64.toInt(): Int = Nak.toInt(this)
inline fun Int64.toLong(): Long = Nak.int64ToLong(this)
inline fun Int64.toFloat(): Float = Nak.toDouble(this).toFloat()
inline fun Int64.toDouble(): Double = Nak.toDouble(this)
inline fun Int64.toDoubleRawBits(): Double = Nak.toDoubleRawBits(this)

inline operator fun PArray.iterator() : Iterator<NakPair<Int, Any?>> = NakIterator(Nak.arrayIterator(this))
inline operator fun PObject.iterator() : Iterator<NakPair<String, Any?>> = NakIterator(Nak.objectIterator(this))
