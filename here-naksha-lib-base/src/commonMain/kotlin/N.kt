@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING", "OPT_IN_USAGE")

package com.here.naksha.lib.base

import kotlin.js.JsStatic
import kotlin.jvm.JvmStatic
import kotlin.reflect.KClass
import kotlin.reflect.KFunction

/**
 * The native abstraction, implemented for each platform to support the multi-platform code. All methods in this singleton are by
 * definition thread safe.
 */
expect class N {
    companion object {
        /**
         * The default symbol used for all proxies for which no explicit symbol is returned by the symbol resolvers.
         */
        @JvmStatic
        @JsStatic
        var DEFAULT_SYMBOL: Symbol

        /**
         * The iterator member.
         */
        @JvmStatic
        @JsStatic
        var ITERATOR: Symbol

        /**
         * The maximum value of a 64-bit integer.
         * @return The maximum value of a 64-bit integer.
         */
        @JvmStatic
        @JsStatic
        val INT64_MAX_VALUE: Int64

        /**
         * The minimum value of a 64-bit integer.
         * @return The minimum value of a 64-bit integer.
         */
        @JvmStatic
        @JsStatic
        val INT64_MIN_VALUE: Int64

        /**
         * The minimum integer that can safely stored in a double.
         * @return The minimum integer that can safely stored in a double.
         */
        @JvmStatic
        @JsStatic
        val MAX_SAFE_INT: Double

        /**
         * The maximum integer that can safely stored in a double.
         * @return The maximum integer that can safely stored in a double.
         */
        @JvmStatic
        @JsStatic
        val MIN_SAFE_INT: Double

        /**
         * The undefined value of the type [Any].
         */
        @JvmStatic
        @JsStatic
        val undefined: Any

        /**
         * The KClass for [Any].
         */
        @JvmStatic
        @JsStatic
        val anyKlass: KClass<Any>

        /**
         * The KClass for [Boolean].
         */
        @JvmStatic
        @JsStatic
        val booleanKlass: KClass<Boolean>

        /**
         * The KClass for [Short].
         */
        @JvmStatic
        @JsStatic
        val shortKlass: KClass<Short>

        /**
         * The KClass for [Int].
         */
        @JvmStatic
        @JsStatic
        val intKlass: KClass<Int>

        /**
         * The KClass for [Int64].
         */
        @JvmStatic
        @JsStatic
        val int64Klass: KClass<Int64>

        /**
         * The KClass for [Double].
         */
        @JvmStatic
        @JsStatic
        val doubleKlass: KClass<Double>

        /**
         * The KClass for [N_Object].
         */
        @JvmStatic
        @JsStatic
        val objectKlass: KClass<N_Object>

        /**
         * The KClass for [N_Array].
         */
        @JvmStatic
        @JsStatic
        val arrayKlass: KClass<N_Array>

        /**
         * The KClass for [N_Map].
         */
        @JvmStatic
        @JsStatic
        val mapKlass: KClass<N_Map>

        /**
         * The KClass for [N_DataView].
         */
        @JvmStatic
        @JsStatic
        val dataViewKlass: KClass<N_DataView>

        /**
         * Tests if the given value is [undefined].
         * @param any The value to test.
         * @return _true_ if the value is [undefined]; false otherwise.
         */
        @JvmStatic
        @JsStatic
        fun isUndefined(any: Any?): Boolean

        /**
         * Creates an undefined value for the given type or returns the cached one.
         * @param klass The type for which to create an undefined value.
         * @return The undefined value.
         */
        @JvmStatic
        @JsStatic
        fun <T : Any> undefinedOf(klass: KClass<T>): T

        /**
         * Must be called ones in the lifetime of an application to initialize the multi-platform code. The method is thread safe and
         * only does something when first called.
         * @param parameters Some arbitrary platform specific parameters to be forwarded.
         * @return _true_ if this was the first call and the platform was initialized; _false_ if the platform is already initialized.
         */
        @JvmStatic
        @JsStatic
        fun initialize(vararg parameters: Any?): Boolean

        /**
         * Returns the [KClass] created **by** the given constructor. This is mainly for JavaScript, it will simply query a cached and if not
         * found, it will create an instance, query the [KClass] using [klassOf] and add it into the cache. Therefore, the cost of
         * creating an instance to get the [KClass] is only paid ones in the lifetime of an application.
         * @param constructor The constructor.
         * @return The [KClass] that is created **by** this constructor.
         * @throws IllegalArgumentException If the given constructor does not create any valid Kotlin object.
         */
        @JvmStatic
        @JsStatic
        fun <T : Any> klassBy(constructor: KFunction<T>): KClass<out T>

        /**
         * Returns the [KClass] **of** the given object.
         * @param o The object to query.
         * @return The [KClass] **of** the given object.
         * @throws IllegalArgumentException If the given object has no valid [KClass].
         */
        @JvmStatic
        @JsStatic
        fun <T : Any> klassOf(o: T): KClass<out T>
        // TODO: In Java, add klassOf(Class<T>)

        /**
         * Returns the default symbol to bind the given [KClass] against. If no symbol is returned by the registered symbol resolvers,
         * it returns [DEFAULT_SYMBOL].
         * @param klass The [KClass] for which to return the default symbol.
         * @return The default symbol to bind the given [KClass] against.
         */
        @JvmStatic
        @JsStatic
        fun <T : Any> symbolOf(klass: KClass<out T>): Symbol

        /**
         * Returns a read-only list of all currently registered symbol resolvers.
         * @return The list of all currently registered symbol resolvers.
         */
        @JvmStatic
        @JsStatic
        fun getSymbolResolvers(): List<Fn1<Symbol, Klass<*>>>

        /**
         * Compares and sets the symbol resolvers in an atomic way.
         * @param expect The list that was read.
         * @param value The new list that should be set, a read-only copy will be done.
         * @return _true_ if the set was successful; _false_ if it failed (another thread modified the list concurrently).
         */
        @JvmStatic
        @JsStatic
        fun compareAndSetSymbolResolvers(expect: List<Fn1<Symbol, Klass<*>>>, value: List<Fn1<Symbol, Klass<*>>>): Boolean

        /**
         * Intern the given string and perform a [NFC](https://unicode.org/reports/tr15/) (Canonical Decomposition,
         * followed by Canonical Composition). Optionally, if [cd] is set to _true_, perform a Compatibility Decomposition,
         * followed by Canonical Composition. Beware that this is only good for search or special cases, the recommended
         * form is NFC (the default).
         * @param s The string to intern and convert to NFC form.
         * @param cd If _true_, then perform a Compatibility Decomposition instead of the Canonical Decomposition.
         * @return The new interned string or the given one, if it is already in the right form.
         */
        @JvmStatic
        @JsStatic
        fun intern(s: String, cd: Boolean = false): String

        /**
         * Create a proxy of the given type or return the existing proxy. If a proxy of a not compatible type exists already, either
         * override it or throw an _IllegalStateException_. The method will automatically [unbox] all [P] instances, when given.
         * @param <T> The proxy type.
         * @param o The native object to query, should be an instance of [N_Object].
         * @param klass The proxy class.
         * @param override If an existing incompatible proxy should be overridden; if _false_, an exception is thrown.
         * @return The proxy type.
         * @throws IllegalArgumentException If the given class is no instance of [N_Object].
         * @throws IllegalStateException If the given object binds an incompatible proxy and [override] is _false_.
         */
        @JvmStatic
        @JsStatic
        fun <T : P> proxy(o: Any?, klass: KClass<T>, override: Boolean = false): T

        @JvmStatic
        @JsStatic
        fun <K : Any, V : Any, T : P_Map<K, V>> proxyMap(
            o: Any?,
            key: KClass<K>,
            value: KClass<V>,
            klass: Klass<T>? = null,
            override: Boolean = false
        ): T

        @JvmStatic
        @JsStatic
        fun <E : Any, T : P_List<E>> proxyList(o: Any?, value: KClass<E>, klass: Klass<T>? = null, override: Boolean = false): T

        /**
         * Returns the symbol for the given string from the global registry. It is recommended to use a package name, for example
         * _com.here.naksha_ is used for [DEFAULT_SYMBOL], the default Naksha multi-platform library.
         * @param key The symbol key; if _null_, a random symbol not part of the registry is created.
         * @return The existing symbol, if no such symbol exist yet, creates a new one.
         */
        @JvmStatic
        @JsStatic
        fun symbol(key: String?): Symbol

        /**
         * Creates a new object.
         * @param entries The entries to add into the object. Can be a list of [P_Entry] of just alternating (_key_, _value_)'s,
         * where _key_ need to a string and _value_ can be anything.
         * @return The created object.
         */
        @JvmStatic
        @JsStatic
        fun newObject(vararg entries: Any?): N_Object

        /**
         * Creates a new array.
         * @param entries The entries to initialize the array with.
         * @return The created array.
         */
        @JvmStatic
        @JsStatic
        fun newArray(vararg entries: Any?): N_Array

        /**
         * Creates a new map.
         * @param entries The entries to add into the map. Can be a list of [P_Entry] of just alternating (_key_, _value_)'s,
         * where _key_ need to a string and _value_ can be anything.
         * @return The created map.
         */
        @JvmStatic
        @JsStatic
        fun newMap(vararg entries: Any?): N_Map

        /**
         * Creates a new concurrent (thread-safe) map.
         * @param entries The entries to add into the map. Can be a list of [P_Entry] of just alternating (_key_, _value_)'s,
         * where _key_ need to a string and _value_ can be anything.
         * @return The created map.
         */
        @JvmStatic
        @JsStatic
        fun newConcurrentMap(vararg entries: Any?): N_ConcurrentMap

        /**
         * Creates a new byte-array of the given size.
         * @param size The size in byte.
         * @return The byte-array of the given size.
         */
        @JvmStatic
        @JsStatic
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
        @JsStatic
        fun newDataView(byteArray: ByteArray, offset: Int = 0, size: Int = byteArray.size - offset): N_DataView

        /**
         * Unboxes the given object so that the underlying native value is returned.
         * @param o The object to unbox.
         * @return The unboxed value.
         */
        @JvmStatic
        @JsStatic
        fun unbox(o: Any?): N_Object

        /**
         * Create a 32-bit integer from the given value.
         * @param value A value being either [Number], [Int64] or [String] that contains a decimal number.
         * @return The value as 32-bit integer.
         * @throws IllegalArgumentException If the given value fails to be converted into a 32-bit integer.
         */
        @JvmStatic
        @JsStatic
        fun toInt(value: Any): Int

        /**
         * Create a 64-bit integer from the given value.
         * @param value A value being either a [Number] or a [String] that contains a decimal number.
         * @return The value as 64-bit integer.
         * @throws IllegalArgumentException If the given value fails to be converted into a 64-bit integer.
         */
        @JvmStatic
        @JsStatic
        fun toInt64(value: Any): Int64

        /**
         * Create a 64-bit floating point number from the given value.
         * @param value A value being either [Number], [Int64] or [String].
         * @return The value as 64-bit floating point number.
         * @throws IllegalArgumentException If the given value fails to be converted into a 64-bit floating point number.
         */
        @JvmStatic
        @JsStatic
        fun toDouble(value: Any): Double

        /**
         * Cast the given 64-bit integer into a 64-bit floating point number using only raw bits. That means, the 64-bits of the
         * integer are treated as if they store an [IEEE-754](https://en.wikipedia.org/wiki/IEEE_754) 64-bit floating point number,
         * so for example 0xffff_ffff_ffff_ffff becomes [Double.NaN].
         * @param i The 64-bit integer.
         * @return The integer converted into a double.
         */
        @JvmStatic
        @JsStatic
        fun toDoubleRawBits(i: Int64): Double

        /**
         * Cast the given 64-bit floating point number into a 64-bit integer using only raw bits. That means, the 64-bits of the
         * floating point are treated as if they are simply a 64-bit integer, so for example [Double.NaN] becomes 0xffff_ffff_ffff_ffff.
         * @param d The 64-bit double in [IEEE-754](https://en.wikipedia.org/wiki/IEEE_754) format.
         * @return The integer converted into a double.
         */
        @JvmStatic
        @JsStatic
        fun toInt64RawBits(d: Double): Int64

        /**
         * Converts an internal 64-bit integer into a platform specific.
         * @param value The internal 64-bit.
         * @return The platform specific 64-bit.
         */
        @JvmStatic
        @JsStatic
        fun longToInt64(value: Long): Int64

        /**
         * Converts a platform specific 64-bit integer into an internal one to be used for example with the [N_DataView].
         * @param value The platform specific 64-bit integer.
         * @return The internal 64-bit integer.
         */
        @JvmStatic
        @JsStatic
        fun int64ToLong(value: Int64): Long

        /**
         * Tests if the given object is a [Number] or [Int64].
         * @param o The object to test.
         * @return _true_ if the object is a [Number] or [Int64]; _false_ otherwise.
         */
        @JvmStatic
        @JsStatic
        fun isNumber(o: Any?): Boolean

        /**
         * Tests if the given object is a [Byte], [Short], [Int] or [Int64].
         * @param o The object to test.
         * @return _true_ if the object is a [Byte], [Short], [Int] or [Int64]; _false_ otherwise.
         */
        @JvmStatic
        @JsStatic
        fun isInteger(o: Any?): Boolean

        /**
         * Tests if the given object is a [Double].
         * @param o The object to test.
         * @return _true_ if the object is a [Double]; _false_ otherwise.
         */
        @JvmStatic
        @JsStatic
        fun isDouble(o: Any?): Boolean

        /**
         * Returns the value of a member field, stored with the underlying native object.
         * @param obj The object to access.
         * @param key The key of the member.
         * @return The member value or [N.undefined] if no such member exist.
         */
        @JvmStatic
        @JsStatic
        fun getMember(obj: N_Object, key: Symbol): Any?

        /**
         * Sets the value of a protected member field, stored with the underlying native object.
         * @param obj The object to access.
         * @param key The key of the member.
         * @param value The value to assign, if being [N.undefined], then the member is removed.
         * @return The previously assigned member value; [N.undefined] if no such member existed.
         */
        @JvmStatic
        @JsStatic
        fun setMember(obj: N_Object, key: Symbol, value: Any?): Any?

        /**
         * Returns the value of a property, stored with the underlying native object.
         * @param obj The object to access.
         * @param key The key of the property.
         * @return The property value or [N.undefined] if no such property exist.
         */
        fun get(obj: N_Object, key: String): Any?

        /**
         * Sets the value of a property, stored with the underlying native object.
         * @param obj The object to access.
         * @param key The key of the property.
         * @param value The value to assign, if being [N.undefined], then the property is removed.
         * @return The previously assigned property value; [N.undefined] if no such property existed.
         */
        fun set(obj: N_Object, key: String, value: Any?): Any?

        /**
         * Tests if the property exists, stored with the underlying native object.
         * @param obj The object to access.
         * @param key The key of the property.
         * @return _true_ if the property exists; _false_ otherwise.
         */
        fun has(obj: N_Object, key: String): Boolean

        /**
         * Removes the property, stored with the underlying native object.
         * @param obj The object to access.
         * @param key The key of the property.
         * @return The value being removed; [N.undefined] if no such property existed.
         */
        fun remove(obj: N_Object, key: String): Any?

        /**
         * Returns an iterator above all properties of an object.
         * @param obj The object to iterate.
         * @return The iterator above all properties, where the value is an array with element at index 0 being the key (a string)
         * and element at index 1 being the value.
         */
        @JvmStatic
        @JsStatic
        fun iterate(obj: N_Object): N_Iterator<N_Array>

        /**
         * Collect all the keys of the object properties (being [String]).
         * @param obj The object from which to get all property keys.
         * @return The keys of the object properties.
         */
        @JvmStatic
        @JsStatic
        fun keys(obj: N_Object): Array<String>

        /**
         * Collect all the values of the object properties.
         * @param obj The object from which to get all property values.
         * @return All values of the object properties.
         */
        @JvmStatic
        @JsStatic
        fun values(obj: N_Object): Array<Any?>

        /**
         * Returns the amount of properties assigned to the given object.
         * @param obj The object for which to count the properties.
         * @return The amount of properties.
         */
        @JvmStatic
        @JsStatic
        fun count(obj: N_Object): Int

        @JvmStatic
        @JsStatic
        fun eq(t: Int64, o: Int64): Boolean

        @JvmStatic
        @JsStatic
        fun eqi(t: Int64, o: Int): Boolean

        @JvmStatic
        @JsStatic
        fun lt(t: Int64, o: Int64): Boolean

        @JvmStatic
        @JsStatic
        fun lti(t: Int64, o: Int): Boolean

        @JvmStatic
        @JsStatic
        fun lte(t: Int64, o: Int64): Boolean

        @JvmStatic
        @JsStatic
        fun ltei(t: Int64, o: Int): Boolean

        @JvmStatic
        @JsStatic
        fun gt(t: Int64, o: Int64): Boolean

        @JvmStatic
        @JsStatic
        fun gti(t: Int64, o: Int): Boolean

        @JvmStatic
        @JsStatic
        fun gte(t: Int64, o: Int64): Boolean

        @JvmStatic
        @JsStatic
        fun gtei(t: Int64, o: Int): Boolean

        @JvmStatic
        @JsStatic
        fun shr(t: Int64, bits: Int): Int64

        @JvmStatic
        @JsStatic
        fun ushr(t: Int64, bits: Int): Int64

        @JvmStatic
        @JsStatic
        fun shl(t: Int64, bits: Int): Int64

        @JvmStatic
        @JsStatic
        fun add(t: Int64, o: Int64): Int64

        @JvmStatic
        @JsStatic
        fun addi(t: Int64, o: Int): Int64

        @JvmStatic
        @JsStatic
        fun sub(t: Int64, o: Int64): Int64

        @JvmStatic
        @JsStatic
        fun subi(t: Int64, o: Int): Int64

        @JvmStatic
        @JsStatic
        fun mul(t: Int64, o: Int64): Int64

        @JvmStatic
        @JsStatic
        fun muli(t: Int64, o: Int): Int64

        @JvmStatic
        @JsStatic
        fun mod(t: Int64, o: Int64): Int64

        @JvmStatic
        @JsStatic
        fun modi(t: Int64, o: Int): Int64

        @JvmStatic
        @JsStatic
        fun div(t: Int64, o: Int64): Int64

        @JvmStatic
        @JsStatic
        fun divi(t: Int64, o: Int): Int64

        @JvmStatic
        @JsStatic
        fun and(t: Int64, o: Int64): Int64

        @JvmStatic
        @JsStatic
        fun or(t: Int64, o: Int64): Int64

        @JvmStatic
        @JsStatic
        fun xor(t: Int64, o: Int64): Int64

        @JvmStatic
        @JsStatic
        fun inv(t: Int64): Int64

        /**
         * Compare the two given objects. If object a support the "compareTo" method it is invoked, if not or comparing fails with an
         * exception, "compareTo" of b is tried and eventually the hashCode of both object is calculated and compared.
         * @param a The first object to compare.
         * @param b The second object to compare.
         * @return -1 if the [a] is less than [b]; 0 if they are equal; 1 if [a] is greater than [b].
         */
        @JvmStatic
        @JsStatic
        fun compare(a: Any?, b: Any?): Int

        /**
         * Calculate a hash-code of the given value and return it. At the JVM this will always just call [hashCode] of the given object,
         * in JavaScript it will try if there is a "hashCode" method on the object and then call, otherwise some default alternative,
         * which will be a stringify with FNV1a hash and then storing the hash in a symbol to have same behavior as in Java.
         * @param o The value to calculate the hash-code.
         * @return The 32-bit hash code.
         */
        @JvmStatic
        @JsStatic
        fun hashCodeOf(o: Any?): Int

        @JvmStatic
        @JsStatic
        fun <T : Any> newInstanceOf(klass: KClass<T>): T
    }
}