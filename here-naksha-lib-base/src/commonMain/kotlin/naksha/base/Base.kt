@file:Suppress("NOTHING_TO_INLINE", "unused")

package naksha.base

import naksha.base.fn.Fn0
import kotlin.reflect.KClass

/**
 * The root management class of `lib-base`.
 *
 * This central class is implemented differently for each platform to support the multi-platform code. All methods in this singleton are by definition thread safe.
 *
 * Standard types supported by `lib-base`:
 * - [Boolean]
 * - [Number]
 *     - [Byte]
 *     - [Short]
 *     - [Int]
 *     - [Long]
 *     - [Float]
 *     - [Double]
 * - [ByteArray]
 * - [String]
 *
 * The list of custom types implementing the `IBase` interface:
 * - [BaseNumber] - Mutable numbers.
 *     - [BaseInt]
 *     - [BaseLong]
 *     - [BaseDouble]
 * - [AbstractBase] - Abstract root for mutable base types.
 *     - [BaseBool] - Mutable boolean.
 *     - [BaseRef] - Mutable reference, in serialization transparently defererenced.
 *     - [BaseRefNotNull] - Mutable non-null reference, in serialization transparently defererenced.
 *     - [BaseWeakRef] - Mutable weak-reference, in serialization transparently defererenced.
 *     - [BaseObject] - Adds support for [IProxyable].
 *         - [BaseArray] - Implements [IMutableArray].
 *         - [BaseMap] - Implements [IMutableMap].
 *         - [BaseBook]
 *         - [TuplePart] - Abstract class holdiong shared code for tuple mapping.
 *             - [TupleMap] - A proxy for a `JBON` map within a [Tuple], implementing the [IMap] interface.
 *             - [TupleArray] - A proxy for a `JBON` array within a [Tuple], implementing the [IArray] interface.
 *
 * Immutable Special types added by `lib-base` as supportive types _(not implementing [IBase] interface)_:
 * - [Literal] - A string singleton used as key and to reduce memory consumption in `JSON` parsing.
 * - [BaseEnum] - A special enumeration implementation that supports runtime enumeration values.
 * - [Tuple] - A wrapper around a [ByteArray] storing the `JBON` binary, adds support for [IProxyable]. Actually, an unique immutable state of an document stored in a storage. The [Tuple] does have a [TupleNumber] which is the globally unique identifier of the state.
 * - [Twkb] - A wrapper around a [ByteArray] that encodes geometries in [WTKB](https://github.com/TWKB/Specification/blob/master/twkb.md) format. Allows to read the geometry and to decode or encode it.
 * - [TupleNumber] - Unique immutable 256-bit binary identifier of a [Tuple], serialized as [Guid].
 * - [Guid] - Global unique identifier, immutable textual identifier of a [Tuple], actually the stringifies [TupleNumber].
 *
 * Standard proxies:
 * - [AbstractProxy] - Root of all proxies, adds support for [IProxyable].
 *     - [Proxy] -
 *
 * Helper classes:
 * - [ByteArrays] - Cross-platform code to access the content of an [ByteArray].
 * - [AtomicMap] - Cross-platform concurrent atomic hash-map implementation.
 * - [Lock] - Cross-platform lock implementation.
 * - [ThreadLocalNullable] - Cross-platform thread-local that can be `null`.
 * - [ThreadLocalNotNull] - Cross-platform thread-local that must not be `null`.
 */
expect class Base {
    companion object BaseCompanion {
        /**
         * The platform specific value for the _undefined_ singleton.
         */
        val UNDEFINED: Any

        /**
         * The platform specific value for the _invalidated_ singleton; used for atomic implementations of map and array.
         */
        val INVALIDATED: Any

        /**
         * The minimum integer that can safely stored in a double.
         * @return The minimum integer that can safely stored in a double.
         */
        val MAX_SAFE_INT_AS_DOUBLE: Double

        /**
         * The minimum integer that can safely stored in a double as long.
         * @return The minimum integer that can safely stored in a double as long.
         */
        val MAX_SAFE_INT_AS_LONG: Long

        /**
         * The maximum integer that can safely stored in a double.
         * @return The maximum integer that can safely stored in a double.
         */
        val MIN_SAFE_INT_AS_DOUBLE: Double

        /**
         * The maximum integer that can safely stored in a double as long.
         * @return The maximum integer that can safely stored in a double as long.
         */
        val MIN_SAFE_INT_AS_LONG: Long

        /**
         * The difference between 1 and the smallest floating point number greater than 1.
         */
        val EPSILON: Double

        /**
         * A map that is queried when a new proxy should be created.
         *
         * The key should be an interface class, the value a class extending [AbstractProxy].
         *
         * On the JVM the map accepts as well `Class` and in JavaScript constructor references are valid as well _(function that constructs object, the same as `JsClass` in Kotlin)_.
         *
         * The effect is that the key type is replaced with the value type, when new proxies are needed for the key type. This should be used to map interfaces to concrete implementations, so that the `proxy` method is able to instantiate an interface. Otherwise, interfaces will raise a [NakshaException] with error [NakshaError.ILLEGAL_ARGUMENT], when an interface is requested as proxy. Some entries will be added by default:
         * - [IMap] and [IMutableMap] to [PAnyMap]
         * - [IArray] and [IMutableArray] to [PAnyArray]
         * @since 3.0
         */
        val interfaceToImplementation: AtomicMap<Any, Any>

        /**
         * An atomic reference to a function that creates a new thread-local logger instance.
         *
         * If not explicitly set by the application, some platform specific standard is used.
         * @since 3.0
         * @see logger
         */
        val loggerFactory: BaseRefNotNull<Fn0<IBaseLogger>>

        /**
         * The logger to which the current thread should report.
         *
         * Automatically updates whenever the application replaces the [loggerFactory] function.
         * @since 3.0
         * @see loggerFactory
         */
        val logger: IBaseLogger

        /**
         * Tests if the given value is `null`, [Base.UNDEFINED] or [Base.INVALIDATED].
         * @param any The value to test.
         * @return _true_ if the value is `null`, [Base.UNDEFINED] or [Base.INVALIDATED]; _false_ otherwise.
         */
        fun isNil(any: Any?): Boolean

        /**
         * Must be called ones in the lifetime of an application to initialize the multi-platform code. The method is thread safe and
         * only does something when first called.
         * @return `true` if this was the first call and the platform was initialized; `false` if the platform is already initialized.
         */
        fun initialize(): Boolean

        /**
         * Tests if the [target] class or interface is either the same as, or is a superclass or superinterface of, the class or interface represented by the specified [source] parameter.
         *
         * For example `isAssignable(CharSequence, String)` will be _false_ (not every [CharSequence] is a [String]), while `isAssignable(String, CharSequence)` will be _true_ (every [String] is a [CharSequence]).
         *
         * In other words, this method tests if the [source] type can be cast down to the [target] type, so if
         * **`source as target`** is possible.
         *
         * **Warning**: An assignment is not the same as an instanceof test. For example for interfaces the example can be tricky,
         * because formally the cast from a [CharSequence] to a [String] is not an assignable form, but technically can still
         * succeed, if the object being tried to cast down is actually a string, just the compiler type is formally [CharSequence].
         * Formally this kind of cast is an assignment from [String] to [String] not being known at compile time.
         *
         * @param source The type that should be cast.
         * @param target The target type to which to cast.
         * @return _true_ if `source as target` will always succeed; so, the [source] type can be cast to the [target] type in all cases; _false_ otherwise.
         */
        fun isAssignable(source: KClass<*>, target: KClass<*>): Boolean

        /**
         * Returns the [KClass] **of** the given object.
         *
         * If the given object is a Java class or a JavaScript class _(aka a constructor)_, then the type is translated into a Kotlin class.
         * @param o The object or platform specific class to query.
         * @return The [KClass] **of** the given object or the [KClass] of the platform specific class.
         * @throws NakshaException with error [ILLEGAL_ARGUMENT][NakshaError.ILLEGAL_ARGUMENT] if the given object has no valid [KClass].
         */
        fun <T : Any> klassOf(o: T): KClass<T>

        /**
         * A reflective method to turn a full qualified classname into a klass instance.
         *
         * - In the JVM this method will use `Class.forName` to find the class, initialize it, and then return the Kotlin class of it.
         * - In JavaScript this method will use `globalThis` to find the constructor, so `com.here.example.Foo` will resolve into `globalThis.com.here.example.Foo`, this is expected to be a constructor function, then casting the constructor into `JsClass` and use [klassOf] to resolve the class it into the Kotlin class.
         * @param name the full qualified name of the Klass.
         * @return the Klass.
         * @since 3.0.0
         * @throws NakshaException with error [ILLEGAL_ARGUMENT][NakshaError.ILLEGAL_ARGUMENT] if no such Klass is found.
         */
        fun <T : Any> klassForName(name: String): KClass<T>

        /**
         * Convert the given 64-bit integer into a 64-bit floating point number using only raw bits. That means, the 64-bits of the integer are treated as if they store an [IEEE-754](https://en.wikipedia.org/wiki/IEEE_754) 64-bit floating point number, so for example 0xffff_ffff_ffff_ffff becomes [Double.NaN].
         * @param i The 64-bit integer.
         * @return The integer converted into a double.
         * @since 3.0
         */
        fun doubleRawBits(i: Long): Double

        /**
         * Convert the given 64-bit floating point number into a 64-bit integer using only raw bits. That means, the 64-bits of the floating point are treated as if they are simply a 64-bit integer, so for example [Double.NaN] becomes 0xffff_ffff_ffff_ffff.
         * @param d The 64-bit double in [IEEE-754](https://en.wikipedia.org/wiki/IEEE_754) format.
         * @return The integer converted into a double.
         * @since 3.0
         */
        fun longRawBits(d: Double): Long

        /**
         * Tests if the given object is a scalar.
         *
         * Types being treated as scalar are:
         * - `null`
         * - [Boolean]
         * - [Number]
         * - [String]
         * - [Literal]
         * - [BaseEnum]
         * - [Base.UNDEFINED]
         * - [Base.INVALIDATED]
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
         * Tests if the given object is a [Byte], [Short], [Int], [Long], [BaseInt] or [BaseLong].
         * @param o The object to test.
         * @return _true_ if the object represents an integer; _false_ otherwise.
         */
        fun isInteger(o: Any?): Boolean

        /**
         * Tests if the given object is a [Float], [Double] or [BaseDouble].
         * @param o The object to test.
         * @return _true_ if the object is a floating point number; _false_ otherwise.
         */
        fun isFloat(o: Any?): Boolean

        /**
         * Calculate a hash-code of the given value and return it.
         * @param o The value to calculate the hash-code.
         * @return The 32-bit hash code.
         */
        fun hashCodeOf(o: Any?): Int

        /**
         * Creates a new initialized instance of the given type, using the primary constructor.
         *
         * In Kotlin the primary constructor is clearly defined, in the JVM any constructor is applicable. To stay JavaScript compatible the exact calling convention for the primary constructor must be used, otherwise the call will fail. JavaScript does not support multiple constructors _(they are mangled internally)_. For the JVM, any constructor accepting the given arguments can be used, with the risk of no longer being compatible with the JavaScript platform.
         * @param klass The type of which to create a new instance.
         * @param args The arguments to be passed to the constructor; must not be `null`!
         * @return The new instance.
         * @throws NakshaException with [ILLEGAL_ARGUMENT][NakshaError.ILLEGAL_ARGUMENT] if there is no matching constructor found.
         */
        fun <T : Any> newInstance(klass: KClass<out T>, vararg args: Any): T

        /**
         * Creates a new instance of the given type, bypassing the constructor, so it returns the uninitialized instance.
         * @param klass The type of which to create a new instance.
         * @return The new instance.
         */
        fun <T : Any> allocateInstance(klass: KClass<out T>): T

        /**
         * Forces the class loader to initialize the given Kotlin class.
         * @param klass The type to initialize.
         */
        fun initializeKClass(klass: KClass<*>)

        /**
         * Ask the platform to make a copy of the given object.
         *
         * This method supports copy of:
         * - `null`
         * - [Boolean]
         * - [Byte]
         * - [Short]
         * - [Int] and [BaseInt]
         * - [Long] and [BaseLong]
         * - [Float]
         * - [Double] and [BaseDouble]
         * - [String] and [Literal]
         * - [ByteArray]
         * - [BaseWeakRef], [BaseRef] and [BaseRefNotNull]
         * - [ThreadLocalNotNull] and [ThreadLocalNullable]
         * - [BaseEnum]
         * - [BaseMap]
         * - [BaseArray]
         * - [BaseBook]
         *
         * Other types have no supported. Calling the method on a graph that contains an unsupported type with throw an exception.
         *
         * @param obj the object to make a copy of, must not be `null` _(`null` values are recursively supported)_.
         * @param recursive _true_ if the copy should be made recursive; _false_ if a shallow copy should be made.
         * @return the copy.
         * @throws NakshaException with error being [ILLEGAL_ARGUMENT][NakshaError.ILLEGAL_ARGUMENT], if the object or graph contains not supported types.
         */
        fun <T> copy(obj: T, recursive: Boolean) : T

        /**
         * Serialize the given value to `JSON`.
         * @param obj the object to serialize.
         * @param longToDataUrl if the Java [Long] should be converted into a data-url _(`data:bigint,{value}`)_; defaults to _false_.
         * @return the `JSON` string.
         * @see ToJsonOptions.DEFAULT
         */
        fun toJsonString(obj: Any?, longToDataUrl: Boolean = false): String

        /**
         * Serialize the given value to `JSON`.
         * @param obj the object to serialize.
         * @param longToDataUrl if the Java [Long] should be converted into a data-url _(`data:bigint,{value}`)_; defaults to _false_.
         * @return the `JSON` string as UTF-8 encoded bytes.
         * @see ToJsonOptions.DEFAULT
         */
        fun toJsonBytes(obj: Any?, longToDataUrl: Boolean = false): ByteArray

        /**
         * Deserialize the given `JSON`.
         * @param json The `JSON` string to parse.
         * @param parseDataUrls if data-urls should be parsed, so `data:bigint,...` and `data:uint8array,base64;...` are automatically turned into Java [Long] and [ByteArray]; defaults to _true_.
         * @return The parsed `JSON`.
         * @see FromJsonOptions.DEFAULT
         */
        fun fromJsonString(json: String, parseDataUrls: Boolean = true): Any?

        /**
         * Deserialize the given `JSON`.
         * @param json The `JSON` as UTF-8 encoded byte array to parse.
         * @param parseDataUrls if data-urls should be parsed, so `data:bigint,...` and `data:uint8array,base64;...` are automatically turned into Java [Long] and [ByteArray]; defaults to _true_.
         * @return The parsed `JSON`.
         * @see [FromJsonOptions.DEFAULT]
         */
        fun fromJsonBytes(json: ByteArray, parseDataUrls: Boolean = true): Any?

        /**
         * Convert the given platform native objects recursively into multi-platform objects. So all maps are converted to [BaseMap], lists and arrays are converted to [BaseArray], and so on.
         *
         * This is used when exchanging objects with platform specific libraries that do not like the multi-platform objects.
         * @param obj The platform native objects to convert recursively.
         * @param importers The importers that will perform the convertion.
         * @return the multi-platform objects.
         */
        fun fromPlatform(obj: Any?, importers: Array<BaseImporter>): Any?

        /**
         * Convert the given multi-platform objects recursively into platform native objects, for example [BaseMap] may become a pure `Foo` object.
         *
         * This is used when exchanging objects with platform specific libraries that do not like the multi-platform objects.
         * @param obj The multi-platform object to be converted recursively into platform native objects.
         * @param exporters The exporters that will perform the convertion.
         * @return the platform native objects.
         */
        fun toPlatform(obj: Any?, exporters: List<BaseExporter>): Any?

        /**
         * Returns the current epoch milliseconds.
         * @return The current epoch milliseconds.
         */
        fun currentMillis(): Long

        /**
         * Returns the current epoch microseconds.
         * @return current epoch microseconds.
         */
        fun currentMicros(): Long

        /**
         * Returns the current epoch nanoseconds.
         * @return current epoch nanoseconds.
         */
        fun currentNanos(): Long

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
         * The `encodeURIComponent()` function encodes a URI by replacing each instance of certain characters by one, two, three, or four escape sequences representing the UTF-8 encoding of the character; will only be four escape sequences for characters composed of two surrogate characters.
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
         * @see Literal.normalize
         */
        fun normalize(value: String, form: NormalizerForm): String

        /**
         * `File-And-Line` - Returns the filename and code line of the caller as string: `[filename:line] `. If not supported on the target platform, returns an empty string.
         *
         * Can be added to the start of an exception like:
         * ```
         * throw illegalArg("${FAL}Some message")
         * ```
         * Resulting in an effective string:
         * ```
         * [Foo.kt:123] Some message
         * ```
         * This is very helpful for debugging, if not supported on a platform, it becomes just:
         * ```
         * Some message
         * ```
         * @return the filename and code line of the caller as string or an empty string.
         * @since 3.0
         */
        val FAL: String

        /**
         * `File-And-Line` - Returns the filename and code line of the caller as string: `[filename:line] `. If not supported on the target platform, returns an empty string.
         *
         * Can be added to the start of an exception like:
         * ```
         * throw illegalArg("${fal(3)}Some message")
         * ```
         * Resulting in an effective string:
         * ```
         * [Foo.kt:123] Some message
         * ```
         * This is very helpful for debugging, if not supported on a platform, it becomes just:
         * ```
         * Some message
         * ```
         * @param n the amount of frames to go back, minimally `1` which is the caller.
         * @return the filename and code line of the caller as string or an empty string.
         * @since 3.0
         */
        fun fal(n: Int): String
    }
}

// ---------------------------------------------------------------------------------------------------------------------------------------
// ---------------------------------------------------------------------------------------------------------------------------------------
//
// These functions will be exposed
// - in JavaScript at the namespace: naksha.base.{name}
// - jn Java at the class naksha.base.BaseKt.{name}
// In Java static imports can be used to use them mostly the same as via Kotlin.
// For example:
// import static naksha.base.BaseKt.getInt32Be;
// ...
// getInt32Be(bytes, 0)
//

/**
 * Can be used to unbox a string.
 * @return the unboxed string or `null`, if the variable is no boxed string _(still toString may work!)_.
 * @since 3.0
 */
inline fun Any?.unboxString(): String? = BaseUtil.unboxString(this)

/**
 * Cast the 64-bit floating point number into a 64-bit integer using only raw bits. That means, the 64-bits of the
 * floating point are treated as if they are simply a 64-bit integer, so for example [Double.NaN] becomes 0xffff_ffff_ffff_ffff.
 * @return The double converted into a long.
 */
inline fun Double.longRawBits(): Long = Base.longRawBits(this)

/**
 * Cast the 64-bit integer into a 64-bit floating point number using only raw bits. That means, the 64-bits of the
 * integer are treated as if they store an [IEEE-754](https://en.wikipedia.org/wiki/IEEE_754) 64-bit floating point number,
 * so for example 0xffff_ffff_ffff_ffff becomes [Double.NaN].
 * @return The long converted into a double.
 */
inline fun Long.doubleRawBits(): Double = Base.doubleRawBits(this)

/**
 * Convert the integer into an unsigned 64-bit integer, so `-1` becomes `4294967295` _(aka `0xffffffff`)_.
 */
inline fun Int.toUnsignedLong(): Long = this.toLong() and 0xffff_ffffL

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

// -------------------------------------------------------------------------------------------------
// ByteArray typed accessors — thin inline wrappers around ByteArrayApi
// -------------------------------------------------------------------------------------------------

// float32

inline fun ByteArray.getFloat32(pos: Int): Float    = ByteArrays.getFloat32(this, pos)
inline fun ByteArray.getFloat32Be(pos: Int): Float  = ByteArrays.getFloat32Be(this, pos)
inline fun ByteArray.getFloat32Le(pos: Int): Float  = ByteArrays.getFloat32Le(this, pos)
inline fun ByteArray.setFloat32(pos: Int, value: Float)   { ByteArrays.setFloat32(this, pos, value) }
inline fun ByteArray.setFloat32Be(pos: Int, value: Float) { ByteArrays.setFloat32Be(this, pos, value) }
inline fun ByteArray.setFloat32Le(pos: Int, value: Float) { ByteArrays.setFloat32Le(this, pos, value) }

// float64

inline fun ByteArray.getFloat64(pos: Int): Double    = ByteArrays.getFloat64(this, pos)
inline fun ByteArray.getFloat64Be(pos: Int): Double  = ByteArrays.getFloat64Be(this, pos)
inline fun ByteArray.getFloat64Le(pos: Int): Double  = ByteArrays.getFloat64Le(this, pos)
inline fun ByteArray.setFloat64(pos: Int, value: Double)   { ByteArrays.setFloat64(this, pos, value) }
inline fun ByteArray.setFloat64Be(pos: Int, value: Double) { ByteArrays.setFloat64Be(this, pos, value) }
inline fun ByteArray.setFloat64Le(pos: Int, value: Double) { ByteArrays.setFloat64Le(this, pos, value) }

// int8 (no endian variants)

inline fun ByteArray.getInt8(pos: Int): Byte       = ByteArrays.getInt8(this, pos)
inline fun ByteArray.setInt8(pos: Int, value: Byte) { ByteArrays.setInt8(this, pos, value) }

// int16

inline fun ByteArray.getInt16(pos: Int): Short    = ByteArrays.getInt16(this, pos)
inline fun ByteArray.getInt16Be(pos: Int): Short  = ByteArrays.getInt16Be(this, pos)
inline fun ByteArray.getInt16Le(pos: Int): Short  = ByteArrays.getInt16Le(this, pos)
inline fun ByteArray.setInt16(pos: Int, value: Short)   { ByteArrays.setInt16(this, pos, value) }
inline fun ByteArray.setInt16Be(pos: Int, value: Short) { ByteArrays.setInt16Be(this, pos, value) }
inline fun ByteArray.setInt16Le(pos: Int, value: Short) { ByteArrays.setInt16Le(this, pos, value) }

// int32

inline fun ByteArray.getInt32(pos: Int): Int    = ByteArrays.getInt32(this, pos)
inline fun ByteArray.getInt32Be(pos: Int): Int  = ByteArrays.getInt32Be(this, pos)
inline fun ByteArray.getInt32Le(pos: Int): Int  = ByteArrays.getInt32Le(this, pos)
inline fun ByteArray.setInt32(pos: Int, value: Int)   { ByteArrays.setInt32(this, pos, value) }
inline fun ByteArray.setInt32Be(pos: Int, value: Int) { ByteArrays.setInt32Be(this, pos, value) }
inline fun ByteArray.setInt32Le(pos: Int, value: Int) { ByteArrays.setInt32Le(this, pos, value) }

// int64

inline fun ByteArray.getInt64(pos: Int): Long    = ByteArrays.getInt64(this, pos)
inline fun ByteArray.getInt64Be(pos: Int): Long  = ByteArrays.getInt64Be(this, pos)
inline fun ByteArray.getInt64Le(pos: Int): Long  = ByteArrays.getInt64Le(this, pos)
inline fun ByteArray.setInt64(pos: Int, value: Long)   { ByteArrays.setInt64(this, pos, value) }
inline fun ByteArray.setInt64Be(pos: Int, value: Long) { ByteArrays.setInt64Be(this, pos, value) }
inline fun ByteArray.setInt64Le(pos: Int, value: Long) { ByteArrays.setInt64Le(this, pos, value) }
