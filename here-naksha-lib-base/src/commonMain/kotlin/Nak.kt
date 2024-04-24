@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package com.here.naksha.lib.nak

import kotlin.jvm.JvmStatic

/**
 * The platform engine to support the multi-platform code. All methods in this engine are by definition thread safe.
 */
expect class Nak {
    companion object {
        /**
         * The symbol to store the default Naksha multi-platform types in.
         */
        @JvmStatic
        val NAK_SYM: PSymbol

        /**
         * The maximum value of a 64-bit integer.
         * @return The maximum value of a 64-bit integer.
         */
        @JvmStatic
        val INT64_MAX_VALUE: Int64

        /**
         * The minimum value of a 64-bit integer.
         * @return The minimum value of a 64-bit integer.
         */
        @JvmStatic
        val INT64_MIN_VALUE: Int64

        /**
         * The minimum integer that can safely stored in a double.
         * @return The minimum integer that can safely stored in a double.
         */
        @JvmStatic
        val MAX_SAFE_INT: Double

        /**
         * The maximum integer that can safely stored in a double.
         * @return The maximum integer that can safely stored in a double.
         */
        @JvmStatic
        val MIN_SAFE_INT: Double

        /**
         * A constant for the special singleton value "undefined".
         */
        @JvmStatic
        val undefined: Any

        /**
         * Must be called ones in the lifetime of an application. The method is thread safe and only does something when first called.
         * @param parameters Some arbitrary platform specific parameters to be forwarded.
         * @return _true_ if this was the first call and the platform was initialized; _false_ if the platform is already initialized.
         */
        @JvmStatic
        fun initNak(vararg parameters: Any?): Boolean

        /**
         * Reads the current multi-platform type of the given object from the given symbol.
         * @param <P> The platform type.
         * @param <T> The multi-platform type.
         * @param o The object to cast.
         * @param symbol The symbol to read.
         * @return The multi-platform type or _null_, if this symbol is not yet linked.
         */
        @JvmStatic
        fun <P, T : NakType<P>> type(o: P, symbol: PSymbol = NAK_SYM): T?

        /**
         * Cast the given object to the multi-platform type represented by the given multi-platform class. This method will fail when
         * [NakClass.canCast] returns _false_.
         * @param <P> The platform type.
         * @param <T> The multi-platform type.
         * @param o The object to cast.
         * @param klass The multi-platform class.
         * @return The multi-platform type.
         * @throws ClassCastException If casting failed.
         */
        @JvmStatic
        fun <P, T : NakType<P>> cast(o: P, klass: NakClass<P, T>): T

        /**
         * Forces to cast the given object to the multi-platform type represented by the given multi-platform class. This method does
         * not invoke [NakClass.canCast].
         * @param <P> The platform type.
         * @param <T> The multi-platform type.
         * @param o The object to cast.
         * @param klass The multi-platform class.
         * @return The multi-platform type.
         * @throws ClassCastException If casting failed, only when the given object does not allow multi-platform types, which means, it
         * does not allow symbols.
         */
        @JvmStatic
        fun <P, T : NakType<P>> force(o: P, klass: NakClass<P, T>): T

        /**
         * Tests if the given object can be cast to the given multi-platform type. If the method returns _false_, casting is still
         * possible doing a forceful cast, but not recommended. This method is a shortcut for calling [NakClass.canCast].
         * @param o The object to test.
         * @param klass The multi-platform class.
         * @return _true_ if the object can be cast safely to the given type; _false_ otherwise.
         */
        @JvmStatic
        fun canCast(o: Any?, klass: NakClass<*, *>): Boolean

        /**
         * Returns the symbol for the given string from the global registry. It is recommended to use the package name, for example
         * _com.here.naksha.lib.nak_ is used for [NAK_SYM], the default Naksha multi-platform library.
         * @param key The symbol key.
         * @return The existing symbol, if no such symbol exist yet, creates a new one.
         */
        @JvmStatic
        fun symbol(key: String): PSymbol

        /**
         * Creates a new platform object.
         * @param entries The entries to add into the object, must be pairs of key ([String]) and value ([Any?]).
         * @return The created object.
         */
        @JvmStatic
        fun newObject(vararg entries: Any?): PObject

        /**
         * Creates a new platform array.
         * @param entries The entries to add into the array.
         * @return The created array.
         */
        @JvmStatic
        fun newArray(vararg entries: Any?): PArray

        /**
         * Creates a new byte-array of the given size.
         * @param size The size in byte.
         * @return The byte-array of the given size.
         */
        @JvmStatic
        fun newByteArray(size: Int): ByteArray

        /**
         * Creates a view above a byte-array to access the content.
         * @param byteArray The array to map.
         * @param offset The offset of the first byte to map, defaults to 0.
         * @param size The amount of byte to map.
         * @return The data view.
         * @throws IllegalArgumentException If any of the given arguments is invalid.
         */
        @JvmStatic
        fun newDataView(byteArray: ByteArray, offset: Int = 0, size: Int = byteArray.size - offset): PDataView

        /**
         * Unboxes the given object so that the underlying native value is returned.
         * @param o The object to unbox.
         * @return The unboxed value.
         */
        @JvmStatic
        fun unbox(o: Any?): Any?

        /**
         * Create a 32-bit integer from the given value.
         * @param value A value being either [Number], [Int64] or [String] that contains a decimal number.
         * @return The value as 32-bit integer.
         * @throws IllegalArgumentException If the given value fails to be converted into a 32-bit integer.
         */
        @JvmStatic
        fun toInt(value: Any): Int

        /**
         * Create a 64-bit integer from the given value.
         * @param value A value being either a [Number] or a [String] that contains a decimal number.
         * @return The value as 64-bit integer.
         * @throws IllegalArgumentException If the given value fails to be converted into a 64-bit integer.
         */
        @JvmStatic
        fun toInt64(value: Any): Int64

        /**
         * Create a 64-bit floating point number from the given value.
         * @param value A value being either [Number], [Int64] or [String].
         * @return The value as 64-bit floating point number.
         * @throws IllegalArgumentException If the given value fails to be converted into a 64-bit floating point number.
         */
        @JvmStatic
        fun toDouble(value: Any): Double

        /**
         * Cast the given 64-bit integer into a 64-bit floating point number using only raw bits. That means, the 64-bits of the
         * integer are treated as if they store an [IEEE-754](https://en.wikipedia.org/wiki/IEEE_754) 64-bit floating point number,
         * so for example 0xffff_ffff_ffff_ffff becomes [Double.NaN].
         * @param i The 64-bit integer.
         * @return The integer converted into a double.
         */
        @JvmStatic
        fun toDoubleRawBits(i: Int64): Double

        /**
         * Cast the given 64-bit floating point number into a 64-bit integer using only raw bits. That means, the 64-bits of the
         * floating point are treated as if they are simply a 64-bit integer, so for example [Double.NaN] becomes 0xffff_ffff_ffff_ffff.
         * @param d The 64-bit double in [IEEE-754](https://en.wikipedia.org/wiki/IEEE_754) format.
         * @return The integer converted into a double.
         */
        @JvmStatic
        fun toInt64RawBits(d: Double): Int64

        /**
         * Converts an internal 64-bit integer into a platform specific.
         * @param value The internal 64-bit.
         * @return The platform specific 64-bit.
         */
        @JvmStatic
        fun longToInt64(value: Long): Int64

        /**
         * Converts a platform specific 64-bit integer into an internal one to be used for example with the [PDataView].
         * @param value The platform specific 64-bit integer.
         * @return The internal 64-bit integer.
         */
        @JvmStatic
        fun int64ToLong(value: Int64): Long

        /**
         * Tests if the given object is a platform native object or scalar.
         * @param o The object to test.
         * @return _true_ if the object is a platform native object or scalar; _false_ otherwise.
         */
        @JvmStatic
        fun isNative(o: Any?): Boolean

        /**
         * Tests if the given object is a [String].
         * @param o The object to test.
         * @return _true_ if the object is a [String]; _false_ otherwise.
         */
        @JvmStatic
        fun isString(o: Any?): Boolean

        /**
         * Tests if the given object is a [Number] or [Int64].
         * @param o The object to test.
         * @return _true_ if the object is a [Number] or [Int64]; _false_ otherwise.
         */
        @JvmStatic
        fun isNumber(o: Any?): Boolean

        /**
         * Tests if the given object is a [Byte], [Short], [Int] or [Int64].
         * @param o The object to test.
         * @return _true_ if the object is a [Byte], [Short], [Int] or [Int64]; _false_ otherwise.
         */
        @JvmStatic
        fun isInteger(o: Any?): Boolean

        /**
         * Tests if the given object is a [Double].
         * @param o The object to test.
         * @return _true_ if the object is a [Double]; _false_ otherwise.
         */
        @JvmStatic
        fun isDouble(o: Any?): Boolean

        /**
         * Tests if the given object is an [Int].
         * @param o The object to test.
         * @return _true_ if the object is a [Int]; _false_ otherwise.
         */
        @JvmStatic
        fun isInt(o: Any?): Boolean

        /**
         * Tests if the given object is an [Int64].
         * @param o The object to test.
         * @return _true_ if the object is a [Int64]; _false_ otherwise.
         */
        @JvmStatic
        fun isInt64(o: Any?): Boolean

        /**
         * Tests if the given object is a [PObject].
         * @param o The object to test.
         * @return _true_ if the object is a [PObject]; _false_ otherwise.
         */
        @JvmStatic
        fun isObject(o: Any?): Boolean

        /**
         * Tests if the given object is a [PArray].
         * @param o The object to test.
         * @return _true_ if the object is a [PArray]; _false_ otherwise.
         */
        @JvmStatic
        fun isArray(o: Any?): Boolean

        /**
         * Tests if the given object is a [PSymbol].
         * @param o The object to test.
         * @return _true_ if the object is a [PSymbol]; _false_ otherwise.
         */
        @JvmStatic
        fun isSymbol(o: Any?): Boolean

        /**
         * Tests if the given object is a [ByteArray].
         * @param o The object to test.
         * @return _true_ if the object is a [ByteArray]; _false_ otherwise.
         */
        @JvmStatic
        fun isByteArray(o: Any?): Boolean

        /**
         * Tests if the given object is a [PDataView].
         * @param o The object to test.
         * @return _true_ if the object is a [PDataView]; _false_ otherwise.
         */
        @JvmStatic
        fun isDataView(o: Any?): Boolean

        /**
         * Tests if the given object contains the given key. Possible objects are [PObject], [PArray] and [PDataView].
         * All of them support [Int], [String] and [PSymbol] as keys. Note that all, except [PArray], will convert an [Int] into a string
         * and query for the serialized integer.
         * @param o The object to test.
         * @param key The key to test.
         * @return _true_ if the object contains the key; _false_ otherwise.
         */
        @JvmStatic
        fun has(o: Any?, key: Any?): Boolean

        /**
         * Read the value assigned to the given key. Possible objects are [PObject], [PArray] and [PDataView].
         * All of them support [Int], [String] and [PSymbol] as keys. Note that all, except [PArray], will convert an [Int] into a string
         * and query for the serialized integer.
         * @param o The object to access.
         * @param key The key to access.
         * @return The value, possibly [undefined] to signal that no such key exists.
         */
        @JvmStatic
        fun get(o: Any, key: Any): Any?

        /**
         * Assigns the given value to the given key. Possible objects are [PObject], [PArray] and [PDataView].
         * All of them support [Int], [String] and [PSymbol] as keys. Note that all, except [PArray], will convert an [Int] into a string
         * and query for the serialized integer.
         * @param o The object to access.
         * @param key The key to access.
         * @param value The value to set, if [undefined], the effect is the same as calling [delete].
         * @return The previous value, possibly [undefined] to signal that no such key existed before.
         * @throws IllegalArgumentException If any of the given arguments is invalid.
         */
        @JvmStatic
        fun set(o: Any, key: Any, value: Any?): Any?

        /**
         * Delete the given key form the given object. Possible objects are [PObject], [PArray] and [PDataView].
         * All of them support [Int], [String] and [PSymbol] as keys. Note that all, except [PArray], will convert an [Int] into a string
         * and query for the serialized integer.
         * @param o The object to access.
         * @param key The key to access.
         * @return The value being removed, possibly [undefined] to signal that no such key existed.
         */
        @JvmStatic
        fun delete(o: Any, key: Any): Any?

        /**
         * Iterate above all values of an array.
         * @param o The array to iterate.
         * @return The iterator above all values of the given array.
         */
        @JvmStatic
        fun arrayIterator(o: PArray): PIterator<Int, Any?>

        /**
         * Iterate above all properties of an object.
         * @param o The object to iterate.
         * @return The iterator above all properties of the object.
         */
        @JvmStatic
        fun objectIterator(o: PObject): PIterator<String, Any?>

        /**
         * Collect all the keys of the object properties. For an array this only returns the property keys, so not the value indices.
         * @param o The object from which to get all keys.
         * @return All keys of the object properties.
         */
        @JvmStatic
        fun keys(o: Any): Array<String>

        /**
         * Collect all the symbols of the object.
         * @param o The object from which to get all symbols.
         * @return All symbols of the object.
         */
        @JvmStatic
        fun symbols(o: Any): Array<PSymbol>

        /**
         * Collect all the values of the object properties. For [PArray] this does not contain the values of the array, only the values
         * of the properties assigned to the array.
         * @param o The object from which to get all property values.
         * @return All property values of the object.
         */
        @JvmStatic
        fun values(o: Any): Array<Any?>

        @JvmStatic
        fun eq(t: Int64, o: Int64): Boolean

        @JvmStatic
        fun eqi(t: Int64, o: Int): Boolean

        @JvmStatic
        fun lt(t: Int64, o: Int64): Boolean

        @JvmStatic
        fun lti(t: Int64, o: Int): Boolean

        @JvmStatic
        fun lte(t: Int64, o: Int64): Boolean

        @JvmStatic
        fun ltei(t: Int64, o: Int): Boolean

        @JvmStatic
        fun gt(t: Int64, o: Int64): Boolean

        @JvmStatic
        fun gti(t: Int64, o: Int): Boolean

        @JvmStatic
        fun gte(t: Int64, o: Int64): Boolean

        @JvmStatic
        fun gtei(t: Int64, o: Int): Boolean

        @JvmStatic
        fun shr(t: Int64, bits: Int): Int64

        @JvmStatic
        fun ushr(t: Int64, bits: Int): Int64

        @JvmStatic
        fun shl(t: Int64, bits: Int): Int64

        @JvmStatic
        fun add(t: Int64, o: Int64): Int64

        @JvmStatic
        fun addi(t: Int64, o: Int): Int64

        @JvmStatic
        fun sub(t: Int64, o: Int64): Int64

        @JvmStatic
        fun subi(t: Int64, o: Int): Int64

        @JvmStatic
        fun mul(t: Int64, o: Int64): Int64

        @JvmStatic
        fun muli(t: Int64, o: Int): Int64

        @JvmStatic
        fun mod(t: Int64, o: Int64): Int64

        @JvmStatic
        fun modi(t: Int64, o: Int): Int64

        @JvmStatic
        fun div(t: Int64, o: Int64): Int64

        @JvmStatic
        fun divi(t: Int64, o: Int): Int64

        @JvmStatic
        fun and(t: Int64, o: Int64): Int64

        @JvmStatic
        fun or(t: Int64, o: Int64): Int64

        @JvmStatic
        fun xor(t: Int64, o: Int64): Int64

        @JvmStatic
        fun inv(t: Int64): Int64

        /**
         * Compare the two given objects. If object a support the "compareTo" method it is invoked, if not or comparing fails with an
         * exception, "compareTo" of b is tried and eventually the hashCode of both object is calculated and compared.
         * @param a The first object to compare.
         * @param b The second object to compare.
         * @return -1 if the [a] is less than [b]; 0 if they are equal; 1 if [a] is greater than [b].
         */
        @JvmStatic
        fun compare(a: Any?, b: Any?): Int

        /**
         * Calculate a hash-code of the given value and return it. At the JVM this will always just call [hashCode] of the given object,
         * in JavaScript it will try if there is a "hashCode" method on the object and then call, otherwise some default alternative,
         * which will be a stringify with FNV1a hash and then storing the hash in a symbol to have same behavior as in Java.
         * @param o The value to calculate the hash-code.
         * @return The 32-bit hash code.
         */
        @JvmStatic
        fun hashCodeOf(o: Any?): Int
    }
}
