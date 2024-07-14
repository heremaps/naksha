@file:Suppress("NOTHING_TO_INLINE", "unused")

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

inline fun Double.toInt64RawBits(value: Double): Int64 = Platform.toInt64RawBits(value)
inline fun Double.toLongRawBits(value: Double): Long = Platform.toInt64RawBits(value).toLong()
inline fun Long.toInt64(): Int64 = Platform.longToInt64(this)

inline fun <K: Any, V:Any> CMap(): CMap<K, V> = Platform.newCMap()

/**
 * Create a proxy or return the existing proxy.
 * @param klass the proxy class.
 * @return the proxy instance.
 * @throws IllegalArgumentException if this is no [PlatformMap], [PlatformList] or [PlatformMap].
 */
inline fun <T : Proxy> PlatformObject?.proxy(klass: KClass<T>): T {
    require(this != null)
    require(this is PlatformMap || this is PlatformList || this is PlatformDataView)
    return Platform.proxy(this, klass)
}