@file:Suppress("NOTHING_TO_INLINE", "unused")

// This will be exposed
// - in JavaScript at the namespace: naksha.base.{name}
// - jn Java at the class naksha.base.NakshaBaseKt.{name}
package naksha.base

import naksha.base.NakshaError.NakshaError_C.COLLECTION_EXISTS
import naksha.base.NakshaError.NakshaError_C.COLLECTION_NOT_FOUND
import naksha.base.NakshaError.NakshaError_C.CONFLICT
import naksha.base.NakshaError.NakshaError_C.EXCEPTION
import naksha.base.NakshaError.NakshaError_C.FEATURE_EXISTS
import naksha.base.NakshaError.NakshaError_C.FEATURE_NOT_FOUND
import naksha.base.NakshaError.NakshaError_C.FORBIDDEN
import naksha.base.NakshaError.NakshaError_C.ILLEGAL_ARGUMENT
import naksha.base.NakshaError.NakshaError_C.ILLEGAL_ID
import naksha.base.NakshaError.NakshaError_C.ILLEGAL_STATE
import naksha.base.NakshaError.NakshaError_C.MAP_EXISTS
import naksha.base.NakshaError.NakshaError_C.MAP_NOT_FOUND
import naksha.base.NakshaError.NakshaError_C.NOT_FOUND
import naksha.base.NakshaError.NakshaError_C.UNSUPPORTED_OPERATION
import naksha.base.Platform.Platform_C.asPlatformObject
import naksha.base.Platform.Platform_C.detectMap
import naksha.base.Platform.Platform_C.forKClass
import naksha.base.Platform.Platform_C.isPlatformObject
import naksha.base.PlatformDataViewApi.PlatformDataViewApi_C.dataview_get_byte_array
import naksha.base.PlatformListApi.PlatformListApi_C.list_get
import naksha.base.PlatformListApi.PlatformListApi_C.list_get_length
import naksha.base.PlatformMapApi.PlatformMapApi_C.map_contains_key
import naksha.base.PlatformMapApi.PlatformMapApi_C.map_get
import naksha.base.PlatformMapApi.PlatformMapApi_C.map_key_iterator
import naksha.base.PlatformMapApi.PlatformMapApi_C.map_size
import naksha.base.fn.Fn0
import naksha.base.fn.Fn1
import naksha.base.fn.Fx2
import kotlin.jvm.JvmOverloads

/**
 * The package name `naksha.base`.
 * @since 3.0
 */
const val PACKAGE_NAME = "naksha.base"

/**
 * The string `Feature`.
 */
internal const val FEATURE = "Feature"

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
 * Returns the amount of elements in an array or list, or the amount of key-value pairs in a map.
 * @param listOrMap The list or map to check.
 * @return the length or `0`.
 * @since 3.0
 */
internal fun get_length(listOrMap: Any?): Int {
    if (listOrMap is Array<*>) return listOrMap.size
    if (listOrMap is List<*>) return listOrMap.size // works as well for MutableList<*>
    if (listOrMap is PlatformList) return list_get_length(listOrMap)
    if (listOrMap is Map<*, *>) return listOrMap.size // works as well for MutableMap<*, *>
    if (listOrMap is PlatformMap) return map_size(listOrMap)
    return 0
}

/**
 * Returns the list element at the given index, or `null`.
 * @param list The list.
 * @param index The index.
 * @param alternative The alternative to return, when the index is out of bounds.
 * @return The element at the given index, or `alternative`.
 * @since 3.0
 */
@JvmOverloads
internal fun get_element(list: Any?, index: Int, alternative: Any? = null): Any? {
    if (list is Array<*>) return if (index in 0 ..< list.size) list[index] else alternative
    if (list is List<*>) return if (index in 0 ..< list.size) list[index] else alternative
    if (list is PlatformList) return if (index in 0 ..< list_get_length(list)) list_get(list, index) else alternative
    return alternative
}

/**
 * Tests if the given map contains the given key.
 * @param map The map to query.
 * @param key The key to lookup.
 * @return _true_ if the map contains the `key`, _false_ otherwise.
 * @since 3.0
 */
internal fun contains_key(map: Any?, key: Any?): Boolean {
    if (map is Map<*, *>) return map.containsKey(key)
    if (map is PlatformMap) return map_contains_key(map, key)
    return false
}

/**
 * Reads the value associated to the given key in the given map.
 * @param map The map to read.
 * @param key The key to read.
 * @param alternative The alternative to return, when the map does not contain the `key`.
 * @return the associated value, or `alternative`.
 * @since 3.0
 */
@JvmOverloads
internal fun get_value(map: Any?, key: Any?, alternative: Any? = null): Any? {
    try {
        if (alternative == null) {
            // Fast path.
            if (map is Map<*, *>) return map[key]
            if (map is PlatformMap) return map_get(map, key)
            return null
        }
        if (map is Map<*, *>) return if (map.containsKey(key)) map[key] else alternative
        if (map is PlatformMap) return if (map_contains_key(map, key)) map_get(map, key) else alternative
        return alternative
    } catch (_: Exception) {
        // Some maps do allow null keys, some don't!
        return alternative
    }
}

/**
 * Reads the value associated to the given key in the given map.
 * @param map The map to read.
 * @param fn The function to call for each key-value pair, receiving the key and value as arguments, in that order.
 * @return if [AbortVisit] is thrown, returns the value returned; otherwise `null`.
 * @since 3.0
 */
internal fun <T> for_each_entry(map: Any?, fn: Fx2<Any?, Any?>): T? {
    try {
        if (map is Map<*, *>) {
            map.forEach { e -> fn.call(e.key, e.value) }
        } else if (map is PlatformMap) {
            val it = map_key_iterator(map)
            var keyEntry = it.next()
            while (!keyEntry.done) {
                val key = keyEntry.value
                val value = map_get(map, key)
                fn.call(key, value)
                keyEntry = it.next()
            }
        }
    } catch (e: NakshaException) {
        val err = e.error
        @Suppress("UNCHECKED_CAST")
        if (err is AbortVisit<*>) return err.value as T?
    }
    return null
}

/**
 * Tests if the given `value` is a [PlatformMap], [Map], or [MutableMap].
 * @param value The value to test.
 * @return _true_ if the value is a map; _false_ otherwise.
 * @since 3.0
 */
internal fun is_map(value: Any?): Boolean = value is PlatformMap || value is Map<*, *> // matches as well MutableMap<*, *>

/**
 * Tests if the given `value` is a [PlatformList], [List], [MutableList], or [Array].
 * @param value The value to test.
 * @return _true_ if the value is a list; _false_ otherwise.
 * @since 3.0
 */
internal fun is_list(value: Any?): Boolean = value is PlatformList || value is Array<*> || value is List<*> // matches as well MutableList<*>

/**
 * Tests if the given `value` is data, so it is [PlatformDataView] or [ByteArray].
 * @param value The value to test.
 * @return _true_ if the value is data; _false_ otherwise.
 * @since 3.0
 */
internal fun is_data(value: Any?): Boolean = value is PlatformDataView || value is ByteArray

/**
 * Tests if the given `value` has data bytes, so it is [PlatformDataView], [ByteArray], or [String].
 * @param value The value to test.
 * @return _true_ if the value has data bytes; _false_ otherwise.
 * @since 3.0
 */
internal fun has_data(value: Any?): Boolean = value is PlatformDataView || value is ByteArray || value is String

private val EMPTY_BYTES = ByteArray(0)

/**
 * Returns the data bytes backing the given value or an empty byte array (size `0`).
 * @param value The value for which to return the bytes ([ByteArray], [PlatformMap], or [String]).
 * @return the
 * @since 3.0
 */
internal fun get_data(value: Any?): ByteArray {
    if (value is PlatformDataView) return dataview_get_byte_array(value)
    if (value is ByteArray) return value
    if (value is String) return value.encodeToByteArray()
    return EMPTY_BYTES
}

/**
 * Tests if the given `haystack` contains the given `needle` recursively.
 *
 * @param haystack The haystack in which to search.
 * @param needle The needle to search for.
 * @return _true_ if `needle` is contained in `haystack`; _false_ otherwise.
 * @since 3.0
 */
internal fun deep_contains(haystack: Any?, needle: Any?): Boolean {
    // Same references or both null
    if (haystack === needle) return true
    // If only one of them is null
    if (haystack == null || needle == null) return false
    if (is_map(haystack)) {
        if (!is_map(needle)) return false
        // for_each_entry returns null, if needle is in haystack, or false otherwise!
        return for_each_entry<Boolean?>(needle) { key, needle_value ->
            // All needle values should be in haystack!
            if (!contains_key(haystack, key)) AbortVisit.with(false)
            // We do recursive equal.
            val hay_value = get_value(haystack, key)
            if (!PlatformUtil.deepEquals(hay_value, needle_value)) AbortVisit.with(false)
        } != false
    }
    if (is_list(haystack)) {
        if (!is_list(needle)) return false
        val hay_len = get_length(haystack)
        val needle_len = get_length(needle)
        var needle_i = -1
        while (++needle_i < needle_len) {
            val needle_value = get_element(needle, needle_i)
            // We need to find this needle value within haystack.
            var found = false
            var hay_i = -1
            while (++hay_i < hay_len) {
                val hay_value = get_element(haystack, hay_i)
                if (PlatformUtil.deepEquals(hay_value, needle_value)) {
                    found = true
                    break
                }
            }
            // If we can't, we're done, needle not contained in haystack.
            if (!found) return false
        }
        return true
    }
    if (is_data(haystack)) {
        if (!is_data(needle)) return false
        val haystack_bytes = get_data(haystack)
        val needle_bytes = get_data(needle)
        return haystack_bytes.contentEquals(needle_bytes)
    }
    return haystack == needle
}

/**
 * Tests if the given values are recursively equal.
 *
 * @param a The first value.
 * @param b The second value to compare.
 * @return _true_ if `a` is recursively equal to `b`; _false_ otherwise.
 * @since 3.0
 */
internal fun deep_equals(a: Any?, b: Any?): Boolean {
    if (a === b) return true
    if (a == null || b == null) return false
    if (is_map(a)) {
        if (!is_map(b)) return false
        val a_len = get_length(a)
        val b_len = get_length(b)
        if (a_len != b_len) return false
        // Compare key-value pairs.
        return for_each_entry<Boolean?>(a) { key, a_value ->
            if (!contains_key(b, key)) AbortVisit.with(false)
            val b_value = get_value(b, key)
            if (!deep_equals(a_value, b_value)) AbortVisit.with(false)
        } != false
    }
    if (is_list(a)) {
        if (!is_list(b)) return false
        val a_len = get_length(a)
        val b_len = get_length(b)
        if (a_len != b_len) return false
        var i = -1
        while (++i < a_len) {
            val a_value = get_element(a, i)
            val b_value = get_element(b, i)
            if (!deep_equals(a_value, b_value)) return false
        }
        return true
    }
    if (is_data(a)) {
        if (!is_data(b)) return false
        val a_bytes = get_data(a)
        val b_bytes = get_data(b)
        return a_bytes.contentEquals(b_bytes)
    }
    return a == b
}

/**
 * The [PlatformType] for `any`.
 * @since 3.0
 */
val Any_TYPE = forKClass(Any::class).initialize()

/**
 * The [PlatformType] for `boolean`.
 * @since 3.0
 */
val Boolean_TYPE = forKClass(Boolean::class).initialize()

/**
 * The [PlatformType] for `int`.
 * @since 3.0
 */
val Int_TYPE = forKClass(Int::class).initialize()

/**
 * The [PlatformType] for `int64`.
 * @since 3.0
 */
val Int64_TYPE = forKClass(Int64::class).initialize()

/**
 * The [PlatformType] for `double`.
 * @since 3.0
 */
val Double_TYPE = forKClass(Double::class).initialize()

/**
 * The [PlatformType] for `string`.
 * @since 3.0
 */
val String_TYPE = forKClass(String::class).initialize()

/**
 * The [PlatformType] for `ByteArray`.
 * @since 3.0
 */
val ByteArray_TYPE = forKClass(ByteArray::class).initialize()

/**
 * The [PlatformType] for `throwable`.
 * @since 3.0
 */
val Throwable_TYPE = forKClass(Throwable::class).initialize()

/**
 * The [PlatformType] for `exception`.
 * @since 3.0
 */
val Exception_TYPE = forKClass(Exception::class).initialize()

/**
 * The [PlatformType] of [AtomicBool].
 * @since 3.0
 */
val AtomicBool_TYPE = forKClass(AtomicBool::class).withPackageName(PACKAGE_NAME).initialize()

/**
 * The [PlatformType] of [AtomicInt].
 * @since 3.0
 */
val AtomicInt_TYPE = forKClass(AtomicInt::class).withPackageName(PACKAGE_NAME).initialize()

/**
 * The [PlatformType] of [AtomicInt64].
 * @since 3.0
 */
val AtomicInt64_TYPE = forKClass(AtomicInt64::class).withPackageName(PACKAGE_NAME).initialize()

/**
 * The [PlatformType] of [AtomicMap].
 * @since 3.0
 */
val AtomicMap_TYPE = forKClass(AtomicMap::class).withPackageName(PACKAGE_NAME).initialize()

/**
 * The [PlatformType] of [AtomicNonNullRef].
 * @since 3.0
 */
val AtomicNonNullRef_TYPE = forKClass(AtomicNonNullRef::class).withPackageName(PACKAGE_NAME).initialize()

/**
 * The [PlatformType] of [AtomicRef].
 * @since 3.0
 */
val AtomicRef_TYPE = forKClass(AtomicRef::class).withPackageName(PACKAGE_NAME).initialize()

/**
 * The [PlatformType] of [WeakRef].
 * @since 3.0
 */
val WeakRef_TYPE = forKClass(WeakRef::class).withPackageName(PACKAGE_NAME).initialize()

/**
 * The [PlatformType] of [BinaryView].
 * @since 3.0
 */
val BinaryView_TYPE = forKClass(BinaryView::class).withPackageName(PACKAGE_NAME).initialize()

/**
 * The [PlatformType] of [Like].
 * @since 3.0
 */
val Like_TYPE = forKClass(Like::class).withPackageName(PACKAGE_NAME).initialize()

/**
 * The [PlatformType] of [PlatformDataView].
 * @since 3.0
 */
val PlatformDataView_Type = forKClass(PlatformDataView::class).withPackageName(PACKAGE_NAME).initialize()

/**
 * The [PlatformType] of [PlatformObject].
 * @since 3.0
 */
val PlatformObject_TYPE = forKClass(PlatformObject::class).withPackageName(PACKAGE_NAME).initialize()

/**
 * The [PlatformType] of [PlatformList].
 * @since 3.0
 */
val PlatformList_TYPE = forKClass(PlatformList::class).withPackageName(PACKAGE_NAME).initialize()

/**
 * The [PlatformType] of [PlatformMap].
 * @since 3.0
 */
val PlatformMap_TYPE = forKClass(PlatformMap::class).withPackageName(PACKAGE_NAME).initialize()

/**
 * The [PlatformType] of [PlatformImporter].
 * @since 3.0
 */
val PlatformImporter_TYPE = forKClass(PlatformImporter::class).withPackageName(PACKAGE_NAME).initialize()

/**
 * The [PlatformType] of [PlatformExporter].
 * @since 3.0
 */
val PlatformExporter_TYPE = forKClass(PlatformExporter::class).withPackageName(PACKAGE_NAME).initialize()

/**
 * The [PlatformType] of [PlatformLock].
 * @since 3.0
 */
val PlatformLock_TYPE = forKClass(PlatformLock::class).withPackageName(PACKAGE_NAME).initialize()

/**
 * The [PlatformType] of [PlatformLogger].
 * @since 3.0
 */
val PlatformLogger_TYPE = forKClass(PlatformLogger::class).withPackageName(PACKAGE_NAME).initialize()

/**
 * The [PlatformType] of [PlatformThreadLocal].
 * @since 3.0
 */
val PlatformThreadLocal_TYPE = forKClass(PlatformThreadLocal::class).withPackageName(PACKAGE_NAME).initialize()

/**
 * The [PlatformType] of [Symbol].
 * @since 3.0
 */
val Symbol_TYPE = forKClass(Symbol::class).withPackageName(PACKAGE_NAME).initialize()

/**
 * The [PlatformType] of [SymbolResolver].
 * @since 3.0
 */
val SymbolResolver_TYPE = forKClass(SymbolResolver::class).withPackageName(PACKAGE_NAME).initialize()

/**
 * The [PlatformType] of [PlatformType].
 * @since 3.0
 */
val PlatformType_TYPE = forKClass(PlatformType::class).withPackageName(PACKAGE_NAME).initialize()

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
    // If raw is for example Foo, and we ask boxInto(raw, Foo.TYPE), we want to just return raw.
    if (type != Any_TYPE && type.isInstance(raw)) return raw as T

    // Otherwise, unbox and start checking.
    val unboxed = Platform.unbox(raw) ?: return if (init != null) init.call() else alternative

    if (isPlatformObject(unboxed)) {
        // If ANY type is okay:
        if (type == Any_TYPE) {
            val existing = Symbols.get(asPlatformObject(unboxed))
            if (existing != null) return existing as T
            if (unboxed is PlatformMap) return detectMap(unboxed).proxy(unboxed) as T
            if (unboxed is PlatformList) return AnyList.TYPE.proxy(unboxed) as T
            if (unboxed is PlatformDataView) return DataViewProxy.TYPE.proxy(unboxed) as T
            return raw as T?
        }

        // If a proxy is requested, try to create one, or return existing one.
        if (type.isProxy()) {
            if (type.isInstantiatable) return type.proxy(asPlatformObject(unboxed))

            val existing = type.getProxy(asPlatformObject(unboxed))
            if (existing != null) return existing
        }

        // If there is anything assigned already to the platform-map, and of correct type, return existing.
        // This captures interfaces in maps without `type` property.
        val existing = Symbols.get(asPlatformObject(unboxed))
        if (type.isInstance(existing)) return type.cast(existing)

        // If data is a platform-map, we detect the type, and if the detected is compatible, use it.
        // This allows to detect interface implementations.
        if (unboxed is PlatformMap) {
            val detectedType = detectMap(unboxed)
            if (detectedType.isAssignableTo(type)) return detectedType.proxy(unboxed) as T
        }

        // Otherwise, if raw or data maps correctly, return, otherwise init or alternative.
        if (type.isInstance(raw)) return type.cast(raw)
        if (type.isInstance(unboxed)) return type.cast(unboxed)
        return if (init != null) init.call() else alternative
    }

    // If enum was requested, turn the value into an enumeration value.
    if (JsEnum.TYPE.isAssignableFrom(type)) {
        return JsEnum.get(unboxed, type as PlatformType<JsEnum>) as T
    }

    // If Int64 is requested.
    if (type == Int64_TYPE) {
        val value = when (unboxed) {
            is Short -> Int64(unboxed.toInt())
            is Int -> Int64(unboxed)
            is Long -> Int64(unboxed)
            is Float -> if (unboxed % 1.0f == 0.0f) Int64(unboxed.toDouble()) else unboxed
            is Double -> if (unboxed % 1.0 == 0.0) Int64(unboxed) else unboxed
            else -> unboxed
        }
        if (value is Int64) return value as T
    }

    // If raw or data are of an acceptable correct type, return them
    if (type.isInstance(raw)) return raw as T
    if (type.isInstance(unboxed)) return unboxed as T

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

/**
 * If [MapProxy], by default, may reuse `Map.Entry`, when iterating map entries.
 *
 * Enable this to improve performance and avoid a new object created for every entry in a map, but beware that some  implementations may fail, because hey keeps references to the returned `Map.Entry` instances _(currently only IntelliJ debugger is known to fail)_.
 * @since 3.0
 */
var reuseMapEntry = false

// ----------------------------------------------------------------------------------------------------------------------------------------
private val isInitialied = AtomicBool(false)
internal fun initialize() {
    if (isInitialied.compareAndSet(expect = false, update = true)) {
        // Order is significant, we try to initialize the static TYPE properties!
        forKClass(Proxy::class).initialize()
        forKClass(ListProxy::class).initialize()
        forKClass(MapProxy::class).initialize()
        forKClass(DataViewProxy::class).initialize()

        forKClass(AnyList::class).initialize()
        forKClass(AnyMap::class).initialize()
        forKClass(AnyObject::class).initialize()
        forKClass(Binary::class).initialize()

        forKClass(AnyTypedObject::class).initialize()
        forKClass(AnyTypedIdObject::class).initialize()

        forKClass(IntList::class).initialize()
        forKClass(Int64List::class).initialize()
        forKClass(StringList::class).initialize()

        forKClass(NakshaError::class).initialize()
        forKClass(NakshaException::class).initialize()

        forKClass(AbortVisit::class).initialize()
        forKClass(DoubleList::class).initialize()
        forKClass(Epoch::class).initialize()
        forKClass(FromJsonOptions::class).initialize()
        forKClass(Int64Encoding::class).initialize()
        forKClass(JsEnum::class).initialize()
        forKClass(MutableInt::class).initialize()
        forKClass(MutableDouble::class).initialize()
        forKClass(PlatformIterator::class).initialize()
        forKClass(PlatformIteratorResult::class).initialize()
        forKClass(PlatformTypeList::class).initialize()
        forKClass(SymbolMember::class).initialize()
        forKClass(Timestamp::class).initialize()
        forKClass(AnyTypedObjectDetector::class).initialize()
        forKClass(ToJsonOptions::class).initialize()

        // Eventually, add typed object detector
        Platform.globalDetectors.add(AnyTypedObjectDetector.instance)
    }
}
