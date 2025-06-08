@file:Suppress("NOTHING_TO_INLINE", "unused")

// This will be exposed
// - in JavaScript at the namespace: naksha.base.{name}
// - jn Java at the class naksha.base.NakshaBaseKt.{name}
package naksha.base

import naksha.base.NakshaError.NakshaErrorCompanion.COLLECTION_EXISTS
import naksha.base.NakshaError.NakshaErrorCompanion.COLLECTION_NOT_FOUND
import naksha.base.NakshaError.NakshaErrorCompanion.CONFLICT
import naksha.base.NakshaError.NakshaErrorCompanion.EXCEPTION
import naksha.base.NakshaError.NakshaErrorCompanion.FEATURE_EXISTS
import naksha.base.NakshaError.NakshaErrorCompanion.FEATURE_NOT_FOUND
import naksha.base.NakshaError.NakshaErrorCompanion.FORBIDDEN
import naksha.base.NakshaError.NakshaErrorCompanion.ILLEGAL_ARGUMENT
import naksha.base.NakshaError.NakshaErrorCompanion.ILLEGAL_ID
import naksha.base.NakshaError.NakshaErrorCompanion.ILLEGAL_STATE
import naksha.base.NakshaError.NakshaErrorCompanion.MAP_EXISTS
import naksha.base.NakshaError.NakshaErrorCompanion.MAP_NOT_FOUND
import naksha.base.NakshaError.NakshaErrorCompanion.NOT_FOUND
import naksha.base.NakshaError.NakshaErrorCompanion.UNSUPPORTED_OPERATION
import naksha.base.Platform.PlatformCompanion.asPlatformObject
import naksha.base.Platform.PlatformCompanion.detectMap
import naksha.base.Platform.PlatformCompanion.forKClass
import naksha.base.Platform.PlatformCompanion.isPlatformObject
import naksha.base.fn.Fn0
import naksha.base.fn.Fn1

/**
 * The package name `naksha.base`.
 * @since 3.0
 */
const val PACKAGE_NAME = "naksha.base"

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
 * Compare objects using the like method preferable, otherwise use equals.
 */
@Suppress("NOTHING_TO_INLINE")
inline infix fun Any.like(other: Any?): Boolean {
    if (this is Like && this.like(other)) return true
    if (other is Like && other.like(this)) return true
    return this == other
}

/**
 * Create a proxy or return the existing proxy.
 * @param type the proxy class.
 * @return the proxy instance.
 * @throws IllegalArgumentException if this is no [PlatformMap], [PlatformList] or [PlatformMap].
 */
inline fun <T : Proxy> PlatformObject?.proxy(type: PlatformType<T>): T {
    if (this == null) throw generalException("null")
    return type.proxy(this)
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

/**
 * The [PlatformType] for `any`.
 * @since 3.0
 */
val Any_TYPE = forKClass(Any::class)

/**
 * The [PlatformType] for `boolean`.
 * @since 3.0
 */
val Boolean_TYPE = forKClass(Boolean::class)

/**
 * The [PlatformType] for `int`.
 * @since 3.0
 */
val Int_Type = forKClass(Int::class)

/**
 * The [PlatformType] for `int64`.
 * @since 3.0
 */
val Int64_TYPE = forKClass(Int64::class)

/**
 * The [PlatformType] for `double`.
 * @since 3.0
 */
val Double_TYPE = forKClass(Double::class)

/**
 * The [PlatformType] for `string`.
 * @since 3.0
 */
val String_TYPE = forKClass(String::class)

/**
 * The [PlatformType] for `ByteArray`.
 * @since 3.0
 */
val ByteArray_TYPE = forKClass(ByteArray::class)

/**
 * The [PlatformType] for `throwable`.
 * @since 3.0
 */
val Throwable_TYPE = forKClass(Throwable::class)

/**
 * The [PlatformType] for `exception`.
 * @since 3.0
 */
val Exception_TYPE = forKClass(Exception::class)

/**
 * The [PlatformType] of [AtomicBool].
 * @since 3.0
 */
val AtomicBool_TYPE: PlatformType<AtomicBool> = forKClass(AtomicBool::class).withPackageName(PACKAGE_NAME)

/**
 * The [PlatformType] of [AtomicInt].
 * @since 3.0
 */
val AtomicInt_TYPE: PlatformType<AtomicInt> = forKClass(AtomicInt::class).withPackageName(PACKAGE_NAME)

/**
 * The [PlatformType] of [AtomicInt64].
 * @since 3.0
 */
val AtomicInt64_TYPE: PlatformType<AtomicInt64> = forKClass(AtomicInt64::class).withPackageName(PACKAGE_NAME)

/**
 * The [PlatformType] of [AtomicMap].
 * @since 3.0
 */
val AtomicMap_TYPE: PlatformType<AtomicMap<*,*>> = forKClass(AtomicMap::class).withPackageName(PACKAGE_NAME)

/**
 * The [PlatformType] of [AtomicNonNullRef].
 * @since 3.0
 */
val AtomicNonNullRef_TYPE: PlatformType<AtomicNonNullRef<*>> = forKClass(AtomicNonNullRef::class).withPackageName(PACKAGE_NAME)

/**
 * The [PlatformType] of [AtomicRef].
 * @since 3.0
 */
val AtomicRef_TYPE: PlatformType<AtomicRef<*>> = forKClass(AtomicRef::class).withPackageName(PACKAGE_NAME)

/**
 * The [PlatformType] of [WeakRef].
 * @since 3.0
 */
val WeakRef_TYPE: PlatformType<WeakRef<*>> = forKClass(WeakRef::class).withPackageName(PACKAGE_NAME)

/**
 * The [PlatformType] of [BinaryView].
 * @since 3.0
 */
val BinaryView_TYPE: PlatformType<BinaryView> = forKClass(BinaryView::class).withPackageName(PACKAGE_NAME)

/**
 * The [PlatformType] of [Like].
 * @since 3.0
 */
val Like_TYPE: PlatformType<Like> = forKClass(Like::class).withPackageName(PACKAGE_NAME)

/**
 * The [PlatformType] of [PlatformDataView].
 * @since 3.0
 */
val PlatformDataView_Type: PlatformType<PlatformDataView> = forKClass(PlatformDataView::class).withPackageName(PACKAGE_NAME)

/**
 * The [PlatformType] of [PlatformObject].
 * @since 3.0
 */
val PlatformObject_TYPE: PlatformType<PlatformObject> = forKClass(PlatformObject::class).withPackageName(PACKAGE_NAME)

/**
 * The [PlatformType] of [PlatformList].
 * @since 3.0
 */
val PlatformList_TYPE: PlatformType<PlatformList> = forKClass(PlatformList::class).withPackageName(PACKAGE_NAME)

/**
 * The [PlatformType] of [PlatformMap].
 * @since 3.0
 */
val PlatformMap_TYPE: PlatformType<PlatformMap> = forKClass(PlatformMap::class).withPackageName(PACKAGE_NAME)

/**
 * The [PlatformType] of [PlatformImporter].
 * @since 3.0
 */
val PlatformImporter_TYPE: PlatformType<PlatformImporter> = forKClass(PlatformImporter::class).withPackageName(PACKAGE_NAME)

/**
 * The [PlatformType] of [PlatformExporter].
 * @since 3.0
 */
val PlatformExporter_TYPE: PlatformType<PlatformExporter> = forKClass(PlatformExporter::class).withPackageName(PACKAGE_NAME)

/**
 * The [PlatformType] of [PlatformLock].
 * @since 3.0
 */
val PlatformLock_TYPE: PlatformType<PlatformLock> = forKClass(PlatformLock::class).withPackageName(PACKAGE_NAME)

/**
 * The [PlatformType] of [PlatformLogger].
 * @since 3.0
 */
val PlatformLogger_TYPE: PlatformType<PlatformLogger> = forKClass(PlatformLogger::class).withPackageName(PACKAGE_NAME)

/**
 * The [PlatformType] of [PlatformThreadLocal].
 * @since 3.0
 */
val PlatformThreadLocal_TYPE: PlatformType<PlatformThreadLocal<*>> = forKClass(PlatformThreadLocal::class).withPackageName(PACKAGE_NAME)

/**
 * The [PlatformType] of [Symbol].
 * @since 3.0
 */
val Symbol_TYPE: PlatformType<Symbol> = forKClass(Symbol::class).withPackageName(PACKAGE_NAME)

/**
 * The [PlatformType] of [SymbolResolver].
 * @since 3.0
 */
val SymbolResolver_TYPE: PlatformType<SymbolResolver> = forKClass(SymbolResolver::class).withPackageName(PACKAGE_NAME)

/**
 * The [PlatformType] of [PlatformType].
 * @since 3.0
 */
val PlatformType_TYPE: PlatformType<PlatformType<*>> = forKClass(PlatformType::class).withPackageName(PACKAGE_NAME)

/**
 * If the given exception is a [NakshaException], rethrow it, otherwise call the given function to wrap the exception into a [NakshaException]. Usage like:
 *
 * ```kotlin
 * try {
 *   ...
 * } catch (t: Throwable) {
 *   throwNakshaException(t) { e ->
 *     generalException("The database query failed", e)
 *   }
 * }
 * ```
 * As within the Naksha framework it is common practise to wrap all platform exceptions into a [NakshaException] with a dedicated error, but before doing this, avoid to "repack" a [NakshaException], this helper method was created.
 * @param t the throwable to check if it is an [NakshaException].
 * @param wrapToNakshaException a function that wraps an [Exception] into a [NakshaException], if not given _(`null`)_, an auto-wrapping is done.
 * @since 3.0
 * @throws NakshaException in all cases.
 */
fun throwNakshaException(t: Throwable, wrapToNakshaException: Fn1<NakshaException, Exception>? = null): Nothing {
    if (t is NakshaException) throw t
    if (t is Exception && wrapToNakshaException != null) throw wrapToNakshaException.call(t)
    throw NakshaException(EXCEPTION, t.message ?: t.toString(), t)
}

/**
 * Create [ILLEGAL_ID] exception.
 * @param msg the message.
 * @return the [NakshaException].
 * @since 3.0
 */
fun illegalId(msg: String): NakshaException = NakshaException(ILLEGAL_ID, msg)

/**
 * Create [ILLEGAL_ARGUMENT] exception.
 * @param msg the message.
 * @return the [NakshaException].
 * @since 3.0
 */
fun illegalArg(msg: String): NakshaException = NakshaException(ILLEGAL_ARGUMENT, msg)

/**
 * Create [ILLEGAL_STATE] exception.
 * @param msg the message.
 * @return the [NakshaException].
 * @since 3.0
 */
fun illegalState(msg: String): NakshaException = NakshaException(ILLEGAL_STATE, msg)

/**
 * Create [ILLEGAL_STATE] exception.
 * @param msg the message.
 * @param reason the exception that caused this exception.
 * @return the [NakshaException].
 * @since 3.0
 */
fun illegalState(msg: String, reason: Exception): NakshaException = NakshaException(ILLEGAL_STATE, msg, reason)

/**
 * Create [FORBIDDEN] exception.
 * @param msg the message.
 * @return the [NakshaException].
 * @since 3.0
 */
fun forbidden(msg: String): NakshaException = NakshaException(FORBIDDEN, msg)

/**
 * Create [Exception_TYPE] exception.
 * @param msg the message.
 * @param cause the cause, if any.
 * @return the [NakshaException].
 * @since 3.0
 */
fun generalException(msg: String, cause: Throwable? = null): NakshaException = NakshaException(EXCEPTION, msg, cause)

/**
 * Create [NOT_FOUND] exception.
 * @param msg the message.
 * @return the [NakshaException].
 * @since 3.0
 */
fun notFound(msg: String): NakshaException = NakshaException(NOT_FOUND, msg)

/**
 * Create [MAP_NOT_FOUND] exception.
 * @param msg the message.
 * @return the [NakshaException].
 * @since 3.0
 */
fun mapNotFound(msg: String): NakshaException = NakshaException(MAP_NOT_FOUND, msg)

/**
 * Create [MAP_EXISTS] exception.
 * @param msg the message.
 * @return the [NakshaException].
 * @since 3.0
 */
fun mapExists(msg: String): NakshaException = NakshaException(MAP_EXISTS, msg)

/**
 * Create [COLLECTION_NOT_FOUND] exception.
 * @param msg the message.
 * @return the [NakshaException].
 * @since 3.0
 */
fun collectionNotFound(msg: String): NakshaException = NakshaException(COLLECTION_NOT_FOUND, msg)

/**
 * Create [COLLECTION_EXISTS] exception
 * @param msg the message.
 * @return the [NakshaException].
 * @since 3.0
 */
fun collectionExists(msg: String): NakshaException = NakshaException(COLLECTION_EXISTS, msg)

/**
 * Create [FEATURE_NOT_FOUND] exception.
 * @param msg the message.
 * @return the [NakshaException].
 * @since 3.0
 */
fun featureNotFound(msg: String): NakshaException = NakshaException(FEATURE_NOT_FOUND, msg)

/**
 * Create [FEATURE_EXISTS] exception.
 * @param msg the message.
 * @return the [NakshaException].
 * @since 3.0
 */
fun featureExists(msg: String): NakshaException = NakshaException(FEATURE_EXISTS, msg)

/**
 * Create [CONFLICT] exception
 * @param msg the message.
 * @return the [NakshaException].
 * @since 3.0
 */
fun conflict(msg: String): NakshaException = NakshaException(CONFLICT, msg)

/**
 * Create [UNSUPPORTED_OPERATION] exception
 * @param msg the message.
 * @return the [NakshaException].
 * @since 3.0
 */
fun unsupportedOp(msg: String): NakshaException = NakshaException(UNSUPPORTED_OPERATION, msg)

private fun autoDetectMap() {

}

/**
 * Box the given value into the given type.
 *
 * @param raw The raw value to convert.
 * @param alternative The alternative to return, when the raw value can't be converted.
 * @param init The initializer, when the raw value can't be converted, preferred above [alternative] if given.
 * @return The raw value as given type, the result of [init], or the given [alternative] (in that order).
 * @since 3.0
 */
@Suppress("UNCHECKED_CAST")
internal fun <T> boxInto(raw: Any?, type: PlatformType<T>, alternative: T? = null, init: Fn0<T?>? = null): T? {
    val data = Platform.unbox(raw) ?: return if (init != null) init.call() else alternative

    if (isPlatformObject(data)) {
        // If ANY type is okay:
        if (type == Any_TYPE) {
            val existing = Symbols.get(asPlatformObject(data))
            if (existing != null) return existing as T
            if (data is PlatformMap) return detectMap(data).proxy(data) as T
            if (data is PlatformList) return AnyList.TYPE.proxy(data) as T
            if (data is PlatformDataView) return DataViewProxy.TYPE.proxy(data) as T
            return raw as T?
        }

        // If a proxy is requested, try to create one, or return existing one.
        if (type.isProxy()) {
            if (type.isInstantiatable) return type.proxy(data)

            val existing = type.getProxy(asPlatformObject(data))
            if (existing != null) return existing
        }

        // If there is anything assigned already to the platform-map, and of correct type, return existing.
        // This captures interfaces in maps without `type` property.
        val existing = Symbols.get(asPlatformObject(data))
        if (type.isInstance(existing)) return type.cast(existing)

        // If data is a platform-map, we can read detect the property to detect the type.
        if (data is PlatformMap) return detectMap(data).proxy(data) as T

        // Otherwise, if raw or data maps correctly, return, otherwise init or alternative.
        if (type.isInstance(raw)) return type.cast(raw)
        if (type.isInstance(data)) return type.cast(data)
        return if (init != null) init.call() else alternative
    }

    // If enum was requested, turn the value into an enumeration value.
    if (JsEnum.TYPE.isAssignableFrom(type)) {
        return JsEnum.get(data, type as PlatformType<JsEnum>) as T
    }

    // If Int64 is requested.
    if (type == Int64_TYPE) {
        val value = when (data) {
            is Short -> Int64(data.toInt())
            is Int -> Int64(data)
            is Long -> Int64(data)
            is Float -> if (data % 1.0f == 0.0f) Int64(data.toDouble()) else data
            is Double -> if (data % 1.0 == 0.0) Int64(data) else data
            else -> data
        }
        if (value is Int64) return value as T
    }

    // If raw or data are of an acceptable correct type, return them
    if (type.isInstance(raw)) return raw as T
    if (type.isInstance(data)) return data as T

    return if (init != null) init.call() else alternative
}

// TODO: Move this into an AtomicMapSet, working basically like the AtomicSet, just two level
//       We need it, and maybe others need it too, so let's offer it.

/**
 * To be used with an `AtomicMap<K, Array<V>>` to atomically remove a value.
 *
 * @param map The atomic map _(`AtomicMap<K, Array<V>>`)_
 * @param key The key in the atomic map to modify.
 * @param value The value in the inner array to remove.
 * @return _true_ if the `value` was in the atomic map and removed; false the `value` was not in the map.
 */
inline fun <K, reified V> atomicMapArrayRemove(map: AtomicMap<K, Array<V>>, key: K, value: V): Boolean {
    while (true) { // Optimistic locking algorithm.
        val existing = map[key] ?: return false
        val remove_index = existing.indexOfFirst { it === value }
        if (remove_index < 0) return false
        val new_size = existing.size - 1
        if (new_size == 0) {
            if (map.remove(key, existing)) return true
        } else {
            var j = 0
            val new_array: Array<V> = Array(new_size) {
                if (j == remove_index) j++
                existing[j++]
            }
            if (map.replace(key, existing, new_array)) return true
        }
        // Concurrent update, retry.
    }
}

/**
 * To be used with an `AtomicMap<K, Array<V>>` to atomically add a value.
 *
 * @param map The atomic map _(`AtomicMap<K, Array<V>>`)_
 * @param key The key in the atomic map to modify.
 * @param value The value in the inner array to add.
 * @return The index where the value is stored.
 */
inline fun <K, reified V> atomicMapArrayAdd(map: AtomicMap<K, Array<V>>, key: K, value: V): Int {
    while (true) { // Optimistic locking algorithm.
        val existing = map[key]
        if (existing == null) {
            val new_array = Array(1) { value }
            if (map.putIfAbsent(key, new_array) == null) return 0
            // Concurrent map update, retry.
            continue
        }
        val existing_index = existing.indexOfFirst { it === value }
        if (existing_index >= 0) return existing_index
        val new_array = Array(existing.size + 1) { if (it == existing.size) value else existing[it] }
        if (map.replace(key, existing, new_array)) return new_array.lastIndex
        // Concurrent map update, retry.
    }
}
