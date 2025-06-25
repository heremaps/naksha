@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package naksha.base

import naksha.base.fn.Fn0
import kotlin.reflect.KClass

/**
 * The platform abstraction, implemented for each platform to support the multi-platform code. All methods in this singleton are
 * by definition thread safe.
 * @since 3.0
 */
expect class Platform private constructor() {
    companion object Platform_C {
        /**
         * The platform specific value of undefined.
         */
        val UNDEFINED: Any

        /**
         * The default symbol used for all proxies for which no explicit symbol is returned by the symbol resolvers.
         */
        val DEFAULT_SYMBOL: Symbol

        /**
         * The iterator member.
         */
        val ITERATOR: Symbol

        /**
         * The maximum value of a 64-bit integer.
         * @return The maximum value of a 64-bit integer.
         */
        val INT64_MAX_VALUE: Int64

        /**
         * The minimum value of a 64-bit integer.
         * @return The minimum value of a 64-bit integer.
         */
        val INT64_MIN_VALUE: Int64

        /**
         * The maximum integer that can safely stored in a double _(`9,007,199,254,740,991`, 2^53-1)_.
         * @return The maximum integer that can safely stored in a double.
         */
        val MAX_SAFE_INT: Double

        /**
         * The maximum integer that can safely stored in a double _(`9,007,199,254,740,991`, 2^53-1)_.
         * @return The maximum integer that can safely stored in a double.
         */
        val MAX_SAFE_INT64: Int64

        /**
         * The minimum integer that can safely stored in a double _(`-9,007,199,254,740,991`, -(2^53-1))_.
         * @return The minimum integer that can safely stored in a double.
         */
        val MIN_SAFE_INT: Double

        /**
         * The minimum integer that can safely stored in a double _(`-9,007,199,254,740,991`, -(2^53-1))_.
         * @return The minimum integer that can safely stored in a double.
         */
        val MIN_SAFE_INT64: Int64

        /**
         * The difference between 1 and the smallest floating point number greater than 1.
         */
        val EPSILON: Double

        /**
         * A map to manage name aliases for types.
         * @since 3.0
         */
        val forNameAlias: AtomicMap<String, PlatformType<*>>

        /**
         * The KClass for [Any].
         */
        val anyKlass: KClass<Any>

        /**
         * The KClass for [Boolean].
         */
        val booleanKlass: KClass<Boolean>

        /**
         * The KClass for [Short].
         */
        val shortKlass: KClass<Short>

        /**
         * The KClass for [Int].
         */
        val intKlass: KClass<Int>

        /**
         * The KClass for [Int64].
         */
        val int64Klass: KClass<Int64>

        /**
         * The KClass for [Double].
         */
        val doubleKlass: KClass<Double>

        /**
         * The KClass for [String].
         */
        val stringKlass: KClass<String>

        /**
         * The KClass for [PlatformObject].
         */
        val objectKlass: KClass<PlatformObject>

        /**
         * The KClass for [PlatformList].
         */
        val listKlass: KClass<PlatformList>

        /**
         * The KClass for [PlatformMap].
         */
        val mapKlass: KClass<PlatformMap>

        /**
         * The KClass for [PlatformDataViewApi].
         */
        val dataViewKlass: KClass<PlatformDataView>

        /**
         * The [PlatformLogger].
         */
        val logger: PlatformLogger

        /**
         * Tests if the given value is _null_ or _undefined_.
         * @param any The value to test.
         * @return _true_ if the value is _null_ or _undefined_; false otherwise.
         */
        fun isNil(any: Any?): Boolean

        /**
         * Must be called ones in the lifetime of an application to initialize the multi-platform code. The method is thread safe and
         * only does something when first called.
         * @return _true_ if this was the first call and the platform was initialized; _false_ if the platform is already initialized.
         */
        fun initialize(): Boolean

        /**
         * A reflective method to turn a full qualified classname into a [PlatformType] instance.
         *
         * - In the JVM this method will use `Class.forName` to find the class, initialize it, and then return the Kotlin class of it.
         * - In JavaScript this method will use `globalThis` to find the constructor of the given instance, so `com.here.example.Foo` will resolve into `globalThis["com"]["here"]["example"]["Foo"]`, this is expected to be a constructor function, then using `ofJs` to resolve it into the [PlatformType] class. In JavaScript calling this function updates the [name] of the [PlatformType], if not yet being detected.
         * Query the [PlatformType] instance for the given full qualified name.
         *
         * @param name the full qualified name of a type.
         * @return the [PlatformType] or `null`, if no such type exists.
         * @since 3.0
         */
        fun <T : Any> forName(name: String): PlatformType<T>?

        /**
         * A reflective method to find all types that has in a JSON representation the property `type` set to the given value.
         *
         * @param jsonType the value read from the `type` property of a JSON object.
         * @return a list of potential [platform types][PlatformType] that serialize to this type; can be an empty list, if no known type matches this.
         * @since 3.0
         */
        fun forJsonType(jsonType: String?): PlatformTypeList

        /**
         * A reflective method to find the first type that has in a JSON representation the property `type` set to the given value, and that is _(or implements)_ the given type.
         *
         * @param jsonType The value read from the `type` property of a JSON object.
         * @param type The [PlatformType] that is searched for.
         * @return either the first matching [PlatformType] or `null`, if no type matches.
         * @since 3.0
         */
        fun <T> forFirstJsonType(jsonType: String?, type: PlatformType<T>): PlatformType<T>?

        /**
         * A reflection method to query the [PlatformType] instance for the given Kotlin class.
         * @param kClass the Kotlin class for which to return the [PlatformType].
         * @return the [PlatformType].
         * @since 3.0
         */
        fun <T: Any> forKClass(kClass: KClass<T>): PlatformType<T>

        /**
         * A reflection method to query the [PlatformType] of the given instance.
         * @param instance the instance for which to return the [PlatformType].
         * @return the [PlatformType].
         * @since 3.0
         */
        fun <T: Any> forInstance(instance: T): PlatformType<T>

        /**
         * Returns the same hash code for the given object as would be returned by the default method hashCode(), whether or not the given object's class overrides hashCode(). The hash code for the null reference is zero.
         * @param obj The object for which to return the identity hash-code.
         * @return the identity hash-code.
         * @since 3.0
         */
        fun identityHashCode(obj: Any?): Int

        /**
         * Intern the given string and perform a [NFC](https://unicode.org/reports/tr15/) (Canonical Decomposition,
         * followed by Canonical Composition). Optionally, if [cd] is set to _true_, perform a Compatibility Decomposition,
         * followed by Canonical Composition. Beware that this is only good for search or special cases, the recommended
         * form is NFC (the default).
         * @param s The string to intern and convert to NFC form.
         * @param cd If _true_, then perform a Compatibility Decomposition instead of the Canonical Decomposition.
         * @return The new interned string or the given one, if it is already in the right form.
         */
        fun intern(s: String, cd: Boolean = false): String

        /**
         * Creates a new list from the given arguments.
         * @param entries The entries to initialize the list with.
         * @return The created list.
         */
        fun listOf(vararg entries: Any?): PlatformList

        /**
         * Creates a new list from the given elements.
         * @param elements The elements to initialize the list with.
         * @return The created list.
         */
        fun listOfArray(elements: Array<*>): PlatformList

        /**
         * Creates a new list with a specific initial capacity.
         * @param capacity The capacity to initialize the list with.
         * @return The created list.
         */
        fun newList(capacity: Int): PlatformList

        /**
         * Creates a new map.
         * @param entries The entries to add into the map. Can be a list of [Pair] or alternating (`key`, `value`)'s.
         * @return The created map.
         */
        fun newMap(vararg entries: Any?): PlatformMap

        /**
         * Create a new concurrent map.
         * @return The concurrent map.
         */
        fun <K, V> newAtomicMap(): AtomicMap<K, V>

        /**
         * Create a new atomic reference.
         * @param startValue the initial value.
         * @return the atomic reference.
         */
        fun <R: Any> newAtomicRef(startValue: R?): AtomicRef<R>

        /**
         * Create a new atomic reference that is never `null`.
         * @param startValue the initial value must not `null`.
         * @return the atomic reference.
         */
        fun <R: Any> newAtomicNonNullRef(startValue: R): AtomicNonNullRef<R>

        /**
         * Create a new atomic boolean.
         * @param startValue the initial value.
         * @return the atomic boolean.
         */
        fun newAtomicBool(startValue: Boolean): AtomicBool

        /**
         * Create a new atomic integer.
         * @param startValue the initial value.
         * @return the atomic integer.
         */
        fun newAtomicInt(startValue: Int): AtomicInt

        /**
         * Create a new atomic 64-bytes based integer (long).
         * @param startValue the initial value.
         * @return the atomic integer.
         */
        fun newAtomicInt64(startValue: Int64): AtomicInt64

        /**
         * Creates a new byte-array of the given size.
         * @param size The size in byte.
         * @return The byte-array of the given size.
         */
        fun newByteArray(size: Int): ByteArray

        /**
         * Creates a view above a byte-array to access the content.
         * @param byteArray The array to map.
         * @param offset The offset of the first byte to map, defaults to 0.
         * @param size The amount of byte to map.
         * @return The data view.
         * @throws IllegalArgumentException If any of the given arguments is invalid.
         */
        fun newDataView(byteArray: ByteArray, offset: Int = 0, size: Int = byteArray.size - offset): PlatformDataView

        /**
         * Creates a new thread-local. Should be stored only in a static immutable variable (`val`).
         * @param initializer An optional lambda to be invoked, when the thread-local is read for the first time.
         * @return The thread local.
         */
        fun <T> newThreadLocal(initializer: (() -> T)? = null): PlatformThreadLocal<T>

        /**
         * Creates a new weak reference to the given referent.
         * @param referent the referent.
         * @return the weak reference to the referent.
         */
        fun <T : Any> newWeakRef(referent: T): WeakRef<T>

        /**
         * Creates a new reentrant lock.
         * @return the created reentrant lock.
         */
        fun newLock(): PlatformLock

        /**
         * Unpack the given object to the closed native representation.
         *
         * - If the given object is a [Proxy], returns the [PlatformObject] of the proxy.
         * - If the given object is [Long], then [Int64] is returned.
         * - If the given object is an [JsEnum], the underlying value is returned ([JsEnum.value]).
         * - Otherwise, the given object is returned as is.
         *
         * @param value The object to access.
         * @return The [PlatformObject] if a [Proxy] given; otherwise the value itself.
         */
        fun unbox(value: Any?): Any?

        /**
         * Box the given value, mainly useful for [proxy types][Proxy].
         *
         * @param raw The raw value to box.
         * @param type The type to box the raw value into.
         * @param alternative The alternative to return, when the raw value can't be boxed.
         * @param init The initializer, when the raw value can't be boxed, preferred above [alternative] if given.
         * @return The raw value boxed to given type, the result of [init], or the given [alternative] (in that order).
         * @since 3.0
         */
        fun <T> box(raw: Any?, type: PlatformType<T>, alternative: T? = null, init: Fn0<T?>? = null): T?

        /**
         * Create a 32-bit integer from the given value.
         * @param value A value being either [Number], [Int64] or [String] that contains a decimal number.
         * @return The value as 32-bit integer.
         * @throws IllegalArgumentException If the given value fails to be converted into a 32-bit integer.
         */
        fun toInt(value: Any): Int

        /**
         * Create a 64-bit integer from the given value.
         * @param value A value being either a [Number] or a [String] that contains a decimal number.
         * @return The value as 64-bit integer.
         * @throws IllegalArgumentException If the given value fails to be converted into a 64-bit integer.
         */
        fun toInt64(value: Any): Int64

        /**
         * Create a 64-bit floating point number from the given value.
         * @param value A value being either [Number], [Int64] or [String].
         * @return The value as 64-bit floating point number.
         * @throws IllegalArgumentException If the given value fails to be converted into a 64-bit floating point number.
         */
        fun toDouble(value: Any): Double

        /**
         * Cast the given 64-bit integer into a 64-bit floating point number using only raw bits. That means, the 64-bits of the
         * integer are treated as if they store an [IEEE-754](https://en.wikipedia.org/wiki/IEEE_754) 64-bit floating point number,
         * so for example 0xffff_ffff_ffff_ffff becomes [Double.NaN].
         * @param i The 64-bit integer.
         * @return The integer converted into a double.
         */
        fun toDoubleRawBits(i: Int64): Double

        /**
         * Cast the given 64-bit floating point number into a 64-bit integer using only raw bits. That means, the 64-bits of the
         * floating point are treated as if they are simply a 64-bit integer, so for example [Double.NaN] becomes 0xffff_ffff_ffff_ffff.
         * @param d The 64-bit double in [IEEE-754](https://en.wikipedia.org/wiki/IEEE_754) format.
         * @return The integer converted into a double.
         */
        fun toInt64RawBits(d: Double): Int64

        /**
         * Widen a 32-bit integer into a platform specific 64-bit integer.
         * @param value the 32-bit integer.
         * @return the platform specific 64-bit representation.
         */
        fun intToInt64(value: Int): Int64

        /**
         * Converts an internal 64-bit integer into a platform specific.
         * @param value The internal 64-bit.
         * @return The platform specific 64-bit.
         */
        fun longToInt64(value: Long): Int64

        /**
         * Converts a platform specific 64-bit integer into an internal one to be used for example with the [PlatformDataViewApi].
         * @param value The platform specific 64-bit integer.
         * @return The internal 64-bit integer.
         */
        fun int64ToLong(value: Int64): Long

        /**
         * A cross-platform test if the given object is a [PlatformObject].
         *
         * ### Note
         * In Java this is the same as doing `if (o instanceof JvmObject)`, in JavaScript there are some edge cases to cover, for example when boxing primitives:
         * ```javascript
         * var x = 5
         * (x instanceof Object) -> false
         * typeof x -> "number"
         *
         * var b = Object(5)
         * (b instanceof Object) -> true
         * typeof b -> "object"
         * ```
         * The issue is, that `Object(5)` technically is a boxed number like `Integer` in Java, but:
         * ```javascript
         * Object.prototype.toString.apply(Object(5))
         * -> "[object Number]"
         * Object.prototype.toString.apply(Object())
         * -> "[object Object]"
         * ```
         * Therefore, all objects in _JavaScript_ do have the capability to box arbitrary values, which can be extracted using `valueOf()` method, even allowing to override that method. So, `isPlatformObject` simply helps to treat `Object(5)` as a boxed number, `Object(true)` as boxed boolean aso, so logically as `Integer`, `Boolean`, ..., instead of identifying them as a real `PlatformObject`.
         *
         * @param o The object to test.
         * @return _true_ if the object is a valid [PlatformObject]; _false_ otherwise.
         */
        fun isPlatformObject(o: Any?): Boolean

        /**
         * Cast the given object into an [PlatformObject].
         *
         * In _Java_ this is the same as `o as JvmObject`, in _JavaScript_ a pure cast via `o as PlatformObject` can fail for some standard Kotlin types, like collections or hash-map. Even while they are technically [PlatformObject] in _JavaScript_, Kotlin has some odd way to check for the interface implementation, calling this method can avoid issues.
         *
         * - Throws [NakshaError.ILLEGAL_ARGUMENT] if the given object is no valid platform object.
         */
        fun asPlatformObject(o: Any?): PlatformObject

        /**
         * Tests if the given object is a scalar, so `null`, `undefined`, [Number], [String], [Boolean], or [Symbol].
         * @param o The object to test.
         * @return _true_ if the object is a scalar; _false_ otherwise.
         */
        fun isScalar(o: Any?): Boolean

        /**
         * Tests if the given object is a [Number].
         * @param o The object to test.
         * @return _true_ if the object is a [Number]; _false_ otherwise.
         */
        fun isNumber(o: Any?): Boolean

        /**
         * Tests if the given object is a [Byte], [Short], [Int], or [Int64] _(aka `Long` in Java)_.
         * @param o The object to test.
         * @return _true_ if the object is a [Byte], [Short], [Int] or [Int64]; _false_ otherwise.
         */
        fun isInteger(o: Any?): Boolean

        /**
         * Tests if the given object is a [Double].
         * @param o The object to test.
         * @return _true_ if the object is a [Double]; _false_ otherwise.
         */
        fun isDouble(o: Any?): Boolean

        /**
         * Compare the two given objects. If object a support the "compareTo" method it is invoked, if not or comparing fails with an
         * exception, "compareTo" of b is tried and eventually the hashCode of both object is calculated and compared.
         * @param a The first object to compare.
         * @param b The second object to compare.
         * @return -1 if the [a] is less than [b]; 0 if they are equal; 1 if [a] is greater than [b].
         */
        fun compare(a: Any?, b: Any?): Int

        /**
         * Calculate a hash-code of the given value and return it. At the JVM this will always just call [hashCode] of the given object,
         * in JavaScript it will try if there is a "hashCode" method on the object and then call, otherwise some default alternative,
         * which will be a stringify with FNV1a hash and then storing the hash in a symbol to have same behavior as in Java.
         * @param o The value to calculate the hash-code.
         * @return The 32-bit hash code.
         */
        fun hashCodeOf(o: Any?): Int

        /**
         * Ask the platform to make a copy of the given platform object.
         *
         * This method supports copy of:
         * - [PlatformMap]
         * - [PlatformList]
         * - [PlatformDataView]
         * - [Boolean]
         * - [Short]
         * - [Int]
         * - [Int64]
         * - [Float]
         * - [Double]
         * - [String]
         *
         * No other types are supported.
         *
         * @param obj the object to make a copy of.
         * @param recursive _true_ if the copy should be made recursive; _false_ if a shallow copy should be made.
         * @return the copy.
         */
        fun <T> copy(obj: T?, recursive: Boolean) : T?

        /**
         * The global [TypeDetector]'s, checked in reverse order, so last added detector is called first.
         * @since 3.0
         * @see box
         */
        val globalDetectors: AtomicSet<TypeDetector>

        /**
         * Detects the type of the given [PlatformMap].
         *
         * This method is invoked by [box] _(without custom [detectors])_, when an [PlatformMap] should be boxed.
         *
         * @param map The map for which the best type should be detected.
         * @param detectors A set of detectors to try, before using the [global detectors][globalDetectors].
         * @return the best matching [MapProxy], worst case is [AnyObject].
         */
        fun detectMap(map: PlatformMap, detectors: AtomicSet<TypeDetector>? = null): PlatformType<MapProxy<String,*>>

        /**
         * Serialize the given value to JSON.
         * @param obj the object to serialize.
         * @return the JSON string.
         * @see [ToJsonOptions.DEFAULT]
         */
        fun toJson(obj: Any?): String

        /**
         * Serialize the given value to JSON.
         * @param obj the object to serialize.
         * @param options the options to use; defaults to [ToJsonOptions.DEFAULT].
         * @return the JSON string.
         */
        fun toJson(obj: Any?, options: ToJsonOptions = ToJsonOptions.DEFAULT): String

        /**
         * Deserialize the given JSON.
         * @param json The JSON string to parse.
         * @return The parsed JSON.
         * @see [FromJsonOptions.DEFAULT]
         */
        fun fromJson(json: String): Any?

        /**
         * Deserialize the given JSON.
         * @param json The JSON string to parse.
         * @return The parsed JSON.
         */
        fun fromJson(json: String, options: FromJsonOptions): Any?

        /**
         * Deserialize the given JSON.
         * @param json The JSON string to parse.
         * @param type The desired type.
         * @return The parsed JSON.
         * @see [FromJsonOptions.DEFAULT]
         */
        fun <T> fromJson(json: String, type: PlatformType<T>): T?

        /**
         * Deserialize the given JSON.
         * @param json the JSON string to parse.
         * @param type The desired type.
         * @param options the options to use; defaults to [FromJsonOptions.DEFAULT].
         * @return The parsed JSON.
         */
        fun <T> fromJson(json: String, type: PlatformType<T>, options: FromJsonOptions): T?

        /**
         * Convert the given platform native objects recursively into multi-platform objects. So all maps are corrected to [PlatformMap], all strings starting with `data:bigint,` or Java `Long`'s are converted into [Int64]'s, lists are corrected to [PlatformList], and so on. This can be used after a JSON was parsed from an arbitrary platform tool into some platform specific standard objects or when exchanging data with a platform specific library that does not like the multi-platform objects.
         * @param obj The platform native objects to convert recursively.
         * @param importers The importers to use.
         * @return The given platform native objects converted into multi-platform objects.
         */
        fun fromPlatform(obj: Any?, importers: List<PlatformImporter>): Any?

        /**
         * Convert the given multi-platform objects recursively into the default platform native objects, for example [PlatformMap] may become a pure `Object` in JavaScript. This is often useful when exchanging code with libraries that do not support `Map`. In Java this will convert to [PlatformMap] to [LinkedHashMap].
         * @param obj The multi-platform objects to be converted into platform native objects.
         * @param exporters The exporters to use.
         * @return The platform native objects.
         */
        fun toPlatform(obj: Any?, exporters: List<PlatformExporter>): Any?

        /**
         * Returns the current epoch milliseconds.
         * @return The current epoch milliseconds.
         */
        fun currentMillis(): Int64

        /**
         * Returns the current epoch microseconds.
         * @return current epoch microseconds.
         */
        fun currentMicros(): Int64

        /**
         * Returns the current epoch nanoseconds.
         * @return current epoch nanoseconds.
         */
        fun currentNanos(): Int64

        /**
         * Generates a new random number between 0 and 1 (therefore with 53-bit random bits).
         * @return The new random number between 0 and 1.
         */
        fun random(): Double

        /**
         * Tests if the given 64-bit floating point number can be converted into a 32-bit floating point number without losing information.
         * @param value The 64-bit floating point number.
         * @return _true_ if the given 64-bit float can be converted into a 32-bit one without losing information; _false_ otherwise.
         */
        fun canBeFloat32(value: Double): Boolean

        /**
         * Tests if the given 64-bit floating point number can be converted into a 32-bit integer without losing information.
         * @param value The 64-bit floating point number.
         * @return _true_ if the given 64-bit float can be converted into a 32-bit integer without losing information; _false_ otherwise.
         */
        fun canBeInt32(value: Double): Boolean

        /**
         * Tests if this code is executed within a PostgresQL database using [PLV8 extension](https://plv8.github.io/).
         * @return _true_ if this code is executed within PostgresQL database using [PLV8 extension](https://plv8.github.io/).
         */
        fun isPlv8(): Boolean

        /**
         * The `encodeURIComponent()` function encodes a URI by replacing each instance of certain characters by one, two, three, or four escape sequences representing the UTF-8 encoding of the character (will only be four escape sequences for characters composed of two surrogate characters).
         * @param uriComponent a string to be encoded as a URI component (a path, query string, fragment, etc.).
         * @return a new string representing the provided uriComponent encoded as a URI component.
         * @since 3.0
         */
        fun encodeURIComponent(uriComponent: String): String

        /**
         * The `decodeURIComponent()` function decodes a Uniform Resource Identifier (URI) component previously created by [encodeURIComponent] or by a similar routine.
         * @param encodedURI an encoded component of a Uniform Resource Identifier.
         * @return a new string representing the decoded version of the given encoded Uniform Resource Identifier (URI) component.
         * @since 3.0
         */
        fun decodeURIComponent(encodedURI: String): String

        /**
         * Calculates the MD5 hash about the given text.
         *
         * @param text the text to hash.
         * @return the MD5 hash, being a byte-array with size 16 (128-bit).
         */
        fun md5(text: String): ByteArray

        /**
         * Calculates the MD5 hash about the given byte-array.
         *
         * @param bytes the byte-array to hash.
         * @return the MD5 hash, being a byte-array with size 16 (128-bit).
         */
        fun md5(bytes: ByteArray): ByteArray

        /**
         * Compress bytes.
         * @param raw the bytes to compress.
         * @return the deflated (compressed) bytes.
         */
        fun lz4Deflate(raw: ByteArray): ByteArray

        /**
         * Decompress bytes.
         * @param compressed the compressed bytes.
         * @return the inflated (decompress) bytes.
         */
        fun lz4Inflate(compressed: ByteArray): ByteArray

        /**
         * Compress bytes.
         * @param raw the bytes to compress.
         * @return the deflated (compressed) bytes.
         */
        fun gzipDeflate(raw: ByteArray): ByteArray

        /**
         * Decompress bytes.
         * @param compressed the bytes to decompress.
         * @return the inflated (decompress) bytes.
         */
        fun gzipInflate(compressed: ByteArray): ByteArray

        /**
         * Create a stack-trace as string for debugging purpose.
         *
         * In Kotlin, you can simply invoke [Throwable.stackTraceToString], which is how this method is implemented.
         * @param t the throwable for which to return the stack-trace.
         * @return the stack-trace as string.
         */
        fun stackTrace(t: Throwable): String

        /**
         * Normalize the string using selected [form](https://www.unicode.org/reports/tr15/#Norm_Forms)
         *
         * @param value - string value to normalize
         * @param form - [NormalizerForm] to use
         * @return new normalized string
         */
        fun normalize(value: String, form: NormalizerForm): String
    }
}
