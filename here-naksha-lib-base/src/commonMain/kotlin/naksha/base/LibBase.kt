@file:Suppress("NOTHING_TO_INLINE", "unused")

// This will be exposed
// - in JavaScript at the namespace: naksha.base.{name}
// - jn Java at the class naksha.base.LibBaseKt.{name}
package naksha.base

import kotlin.reflect.KClass

inline fun Int64(value: Long) = Platform.longToInt64(value)
inline fun Int64(value: Int) = Platform.toInt64(value)
inline fun Int64(value: Double, rawBits: Boolean = false) = if (rawBits) Platform.toInt64RawBits(value) else Platform.toInt64(value)
fun asInt64(any: Any?): Int64 = any as Int64

inline infix fun Short.eq(other: Int64): Boolean = other eq this
inline infix fun Int.eq(other: Int64): Boolean = other eq this
inline infix fun Double.eq(other: Int64): Boolean = other eq this

inline operator fun Short.plus(other: Int64): Int64 = other + this
inline operator fun Int.plus(other: Int64): Int64 = other + this
inline operator fun Double.plus(other: Int64): Double = this + Platform.toDouble(other)

inline operator fun Short.minus(other: Int64): Int64 = other + (-this)
inline operator fun Int.minus(other: Int64): Int64 = other + (-this)
inline operator fun Double.minus(other: Int64): Double = this - Platform.toDouble(other)

inline operator fun Short.times(other: Int64): Int64 = other * this
inline operator fun Int.times(other: Int64): Int64 = other * this
inline operator fun Double.times(other: Int64): Double = this * Platform.toDouble(other)

inline operator fun Short.rem(other: Int64): Int64 = Platform.toInt64(this) % other
inline operator fun Int.rem(other: Int64): Int64 = Platform.toInt64(this) % other
inline operator fun Double.rem(other: Int64): Double = this % Platform.toDouble(other)

inline operator fun Short.div(other: Int64): Int64 = Platform.toInt64(this) / other
inline operator fun Int.div(other: Int64): Int64 = Platform.toInt64(this) / other
inline operator fun Double.div(other: Int64): Double = this / Platform.toDouble(other)

inline operator fun Short.compareTo(other: Int64): Int {
    val diff = this - other
    return if (diff eq 0) 0 else if (diff < 0) -1 else 1
}

inline operator fun Int.compareTo(other: Int64): Int {
    val diff = this - other
    return if (diff eq 0) 0 else if (diff < 0) -1 else 1
}

inline operator fun Double.compareTo(other: Int64): Int {
    val diff = this - other
    return if (diff == 0.0) 0 else if (diff <= 0.0) -1 else 1
}

val INT_TO_UNSIGNED_INT64_MASK = Int64(0xffff_ffff)

inline fun Double.toInt64RawBits(): Int64 = Platform.toInt64RawBits(this)
inline fun Double.toLongRawBits(): Long = Platform.toInt64RawBits(this).toLong()
inline fun Double.toInt64(): Int64 = Platform.toInt64(this)
inline fun Long.toInt64(): Int64 = Platform.longToInt64(this)
inline fun Int.toInt64(): Int64 = Platform.toInt64(this)

/**
 * Convert the integer into an unsigned 64-bit integer, so `-1` becomes `4294967295` _(aka `0xffffffff`)_.
 */
inline fun Int.toUnsignedInt64(): Int64 = Platform.toInt64(this) and INT_TO_UNSIGNED_INT64_MASK

inline fun <K : Any, V : Any> AtomicMap(): AtomicMap<K, V> = Platform.newAtomicMap()
inline fun AtomicBool(initialValue: Boolean = false): AtomicBool = Platform.newAtomicBool(initialValue)
inline fun AtomicInt(initialValue: Int = 0): AtomicInt = Platform.newAtomicInt(initialValue)
inline fun AtomicInt64(initialValue: Int64): AtomicInt64 = Platform.newAtomicInt64(initialValue)
inline fun AtomicInt64(initialValue: Long = 0): AtomicInt64 = Platform.newAtomicInt64(initialValue.toInt64())
inline fun <T : Any> AtomicRef(referee: T?): AtomicRef<T> = Platform.newAtomicRef(referee)
inline fun <T : Any> AtomicNonNullRef(referee: T): AtomicNonNullRef<T> = Platform.newAtomicNonNullRef(referee)
inline fun <T : Any> WeakRef(referee: T): WeakRef<T> = Platform.newWeakRef(referee)

/**
 * Create a proxy or return the existing proxy.
 * @param klass the proxy class.
 * @return the proxy instance.
 * @throws IllegalArgumentException if this is no [PlatformMap], [PlatformList] or [PlatformMap].
 */
inline fun <T : Proxy> PlatformObject?.proxy(klass: KClass<T>): T {
    require(this != null)
    return Platform.proxy(this, klass)
}

/**
 * Remove the given element from the array, if it was contained in the array.
 * @param element the element to remove.
 * @return the new array or this, if the element was not part of the array.
 */
inline operator fun <reified T> Array<T>.minus(element: T?): Array<T> {
    val i = indexOf(element)
    if (i < 0) return this
    var si = 0
    val newArray = arrayOfNulls<T>(size - 1)
    var ni = 0
    while (si < size) {
        if (si != i) newArray[ni++] = this[si]
        si++
    }
    @Suppress("UNCHECKED_CAST")
    return newArray as Array<T>
}
