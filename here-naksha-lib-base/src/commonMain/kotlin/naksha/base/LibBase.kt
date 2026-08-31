@file:Suppress("NOTHING_TO_INLINE", "unused")

// This will be exposed
// - in JavaScript at the namespace: naksha.base.{name}
// - jn Java at the class naksha.base.LibBaseKt.{name}
package naksha.base

import kotlin.reflect.KClass
import kotlin.reflect.cast

fun asInt64(any: Any?): Long = any as Long

const val INT_TO_UNSIGNED_INT64_MASK = 0xffff_ffffL

inline fun Double.toInt64RawBits(): Long = toRawBits()
inline fun Double.toLongRawBits(): Long = toRawBits()
inline fun Double.toInt64(): Long = Platform.toInt64(this)
inline fun Long.toInt64(): Long = this
inline fun Int.toInt64(): Long = toLong()

/**
 * Convert the integer into an unsigned 64-bit integer, so `-1` becomes `4294967295` _(aka `0xffffffff`)_.
 */
inline fun Int.toUnsignedInt64(): Long = toLong() and INT_TO_UNSIGNED_INT64_MASK

inline fun <K : Any, V : Any> AtomicMap(): AtomicMap<K, V> = Platform.newAtomicMap()
inline fun AtomicBool(initialValue: Boolean = false): AtomicBool = Platform.newAtomicBool(initialValue)
inline fun AtomicInt(initialValue: Int = 0): AtomicInt = Platform.newAtomicInt(initialValue)
inline fun AtomicInt64(initialValue: Long = 0): AtomicInt64 = Platform.newAtomicInt64(initialValue)
inline fun <T : Any> AtomicRef(referee: T?): AtomicRef<T> = Platform.newAtomicRef(referee)
inline fun <T : Any> AtomicNonNullRef(referee: T): AtomicNonNullRef<T> = Platform.newAtomicNonNullRef(referee)
inline fun <T : Any> WeakRef(referee: T): WeakRef<T> = Platform.newWeakRef(referee)

/**
 * Cast this, if possible, into the given proxy.
 * @param klass the proxy class to return.
 * @return this as the requested proxy or `null`.
 * @since 3.0
 */
@Suppress("UNCHECKED_CAST")
inline fun <T : Proxy> Any?.proxy(klass: KClass<T>): T? {
    if (this == null) return null
    if (klass.isInstance(this)) return this as T
    if (this is PlatformMap) return this.proxy(klass)
    if (this is Proxy) return this.proxy(klass)
    return null
}

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
